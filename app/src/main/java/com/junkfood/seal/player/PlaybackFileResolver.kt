package com.junkfood.seal.player

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.junkfood.seal.util.FileUtil.getFileProvider
import java.io.File

data class PlaybackFile(val uri: Uri, val mimeType: String?)

object PlaybackFileResolver {
    fun resolve(context: Context, path: String): PlaybackFile? {
        val uri =
            if (path.startsWith("content://")) {
                DocumentFile.fromSingleUri(context, Uri.parse(path))?.takeIf { it.exists() }?.uri
            } else {
                File(path).takeIf { it.exists() }?.let { Uri.fromFile(it) }
            }
                ?: return null
        return PlaybackFile(uri = uri, mimeType = context.contentResolver.getType(uri))
    }

    fun resolveForSharing(context: Context, path: String): PlaybackFile? {
        val uri =
            DocumentFile.fromSingleUri(context, Uri.parse(path))?.takeIf { it.exists() }?.uri
                ?: File(path).takeIf { it.exists() }?.let {
                    FileProvider.getUriForFile(context, context.getFileProvider(), it)
                }
                ?: return null
        return PlaybackFile(uri = uri, mimeType = context.contentResolver.getType(uri))
    }
}
