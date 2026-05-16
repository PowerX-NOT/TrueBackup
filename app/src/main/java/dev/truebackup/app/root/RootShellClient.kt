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
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
                .setTimeout(120)
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
                output = (e.message ?: e.toString()).trim()
            )
        } catch (e: Exception) {
            invalidate()
            RootExecutionResult(
                exitCode = 127,
                output = (e.message ?: e.toString()).trim()
            )
        }
    }

    /** Stops the persistent daemon; only for Settings → “Check again”. */
    fun stopDaemon() {
        runOnMainThread {
            connection?.let { RootService.unbind(it) }
            invalidate()
            RootService.stopOrTask(daemonIntent())
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
                        error.set(RemoteException("Root daemon returned null binding."))
                        latch.countDown()
                    }
                }
                connection = conn
                RootService.bind(daemonIntent(), conn)
            } catch (e: Exception) {
                error.set(e)
                latch.countDown()
            }
        }
        if (!latch.await(120, TimeUnit.SECONDS)) {
            throw IllegalStateException("Timed out waiting for root daemon.")
        }
        error.get()?.let { throw it }
        return bound.get() ?: throw IllegalStateException("Root daemon is not available.")
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
            latch.await(30, TimeUnit.SECONDS)
            error.get()?.let { throw it }
        }
    }
}
