package dev.truebackup.app.ui.util

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

/**
 * Best-effort mapping from a document-tree URI to a usable filesystem path.
 *
 * Root backup operations require absolute paths. We currently only support primary shared storage.
 */
fun resolvePrimaryStoragePathFromTreeUri(uri: Uri): String? {
    val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
    val split = id.split(':', limit = 2)
    val volume = split.firstOrNull() ?: return null
    val relative = if (split.size > 1) split[1] else ""
    if (!volume.equals("primary", ignoreCase = true)) return null
    val base = Environment.getExternalStorageDirectory().absolutePath
    return if (relative.isBlank()) base else "$base/$relative"
}

