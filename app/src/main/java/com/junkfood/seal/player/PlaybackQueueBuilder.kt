package com.junkfood.seal.player

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

object PlaybackQueueBuilder {
    fun build(context: Context, queue: List<PlaybackQueueItem>): List<MediaItem> =
        queue.mapNotNull { item ->
            val file = PlaybackFileResolver.resolve(context, item.path) ?: return@mapNotNull null
            val metadata =
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.author.takeUnless { it == "null" })
                    .setArtworkUri(item.thumbnailUrl.takeIf { it.isNotBlank() }?.toUri())
                    .build()

            MediaItem.Builder()
                .setMediaId(item.id.toString())
                .setUri(file.uri)
                .setMimeType(file.mimeType)
                .setMediaMetadata(metadata)
                .build()
        }
}
