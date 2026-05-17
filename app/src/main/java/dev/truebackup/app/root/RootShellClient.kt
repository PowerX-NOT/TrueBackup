package dev.truebackup.app.root

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import com.topjohnwu.superuser.NoShellException
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/**
 * Forwards shell work to [TrueBackupRootService] in libsu **daemon mode**.
 *
 * The daemon outlives the UI process. Bind only when a command runs (never on app launch),
 * so Magisk is not notified on every open—only when the daemon is first created at setup.
 */
data class RootExecutionResult(
    val exitCode: Int,
    val output: String
)

object RootShellClient {

    private lateinit var appContext: Context
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }
    private var service: IRootCommandService? = null
    private var connection: ServiceConnection? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val debuggable =
            (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Shell.enableVerboseLogging = debuggable
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
        )
    }

    fun execute(command: String): RootExecutionResult {
        return try {
            val parcelable = getService().execute(command)
            RootExecutionResult(
                exitCode = parcelable.code,
                output = parcelable.output
            )
        } catch (e: RemoteException) {
            invalidate()
            RootExecutionResult(
                exitCode = 127,
                output = rootFailureOutput(e)
            )
        } catch (e: Exception) {
            invalidate()
            RootExecutionResult(
                exitCode = 127,
                output = rootFailureOutput(e)
            )
        }
    }

    /** Stops the persistent daemon and drops any cached main shell before a fresh access check. */
    fun prepareForAccessCheck() {
        stopDaemon()
        invalidateCachedShell()
    }

    /** Stops the persistent daemon. */
    fun stopDaemon() {
        runOnMainThread {
            connection?.let { RootService.unbind(it) }
            invalidate()
            RootService.stopOrTask(daemonIntent())
        }
    }

    /** Closes libsu's cached main shell so Magisk revoke is detected on the next command. */
    fun invalidateCachedShell() {
        runCatching {
            Shell.getCachedShell()?.takeIf { it.isAlive }?.close()
        }
    }

    private fun getService(): IRootCommandService {
        val existing = service
        if (existing != null && existing.asBinder().isBinderAlive) {
            return existing
        }
        return bindBlocking()
    }

    private fun bindBlocking(): IRootCommandService {
        val bound = AtomicReference<IRootCommandService>()
        val error = AtomicReference<Exception>()
        val latch = CountDownLatch(1)
        runOnMainThread {
            try {
                if (service != null && service!!.asBinder().isBinderAlive) {
                    bound.set(service)
                    latch.countDown()
                    return@runOnMainThread
                }
                invalidate()

                if (Shell.isAppGrantedRoot() == false) {
                    error.set(rootUnavailableException())
                    latch.countDown()
                    return@runOnMainThread
                }

                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        service = IRootCommandService.Stub.asInterface(binder)
                        bound.set(service)
                        latch.countDown()
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        invalidate()
                        if (bound.get() == null) {
                            error.set(RemoteException("Root daemon disconnected."))
                            latch.countDown()
                        }
                    }

                    override fun onBindingDied(name: ComponentName) {
                        invalidate()
                        if (bound.get() == null) {
                            error.set(RemoteException("Root daemon binding died."))
                            latch.countDown()
                        }
                    }

                    override fun onNullBinding(name: ComponentName) {
                        invalidate()
                        error.set(rootUnavailableException())
                        latch.countDown()
                    }
                }
                connection = conn

                val task = RootService.bindOrTask(daemonIntent(), mainExecutor, conn)
                if (task == null) {
                    return@runOnMainThread
                }
                Shell.EXECUTOR.execute {
                    try {
                        val shell = Shell.getShell()
                        if (!shell.isRoot) {
                            if (bound.get() == null) {
                                error.set(rootUnavailableException())
                                latch.countDown()
                            }
                            return@execute
                        }
                        shell.execTask(task)
                    } catch (e: NoShellException) {
                        if (bound.get() == null) {
                            error.set(e)
                            latch.countDown()
                        }
                    } catch (e: IOException) {
                        if (bound.get() == null) {
                            error.set(e)
                            latch.countDown()
                        }
                    }
                }
            } catch (e: Exception) {
                error.set(e)
                latch.countDown()
            }
        }
        latch.await()
        error.get()?.let { throw it }
        return bound.get() ?: throw rootUnavailableException()
    }

    private fun daemonIntent(): Intent =
        Intent().apply {
            component = ComponentName(
                appContext.packageName,
                TrueBackupRootService::class.java.name
            )
            addCategory(RootService.CATEGORY_DAEMON_MODE)
        }

    private fun invalidate() {
        service = null
        connection = null
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            val latch = CountDownLatch(1)
            val error = AtomicReference<Exception>()
            mainHandler.post {
                try {
                    block()
                } catch (e: Exception) {
                    error.set(e)
                } finally {
                    latch.countDown()
                }
            }
            latch.await()
            error.get()?.let { throw it }
        }
    }

    private fun rootUnavailableException(): IllegalStateException =
        IllegalStateException("Root is not available (or Magisk denied this app).")

    private fun rootFailureOutput(error: Throwable): String =
        when (error) {
            is NoShellException -> "Root is not available (or Magisk denied this app). ${error.message?.trim().orEmpty()}".trim()
            is IllegalStateException -> error.message?.trim().orEmpty()
            else -> (error.message ?: error.toString()).trim()
        }
}
