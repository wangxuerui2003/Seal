package com.junkfood.seal.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import com.junkfood.seal.MainActivity

object PlaybackLauncher {
    const val ACTION_PLAY_PATH = "com.junkfood.seal.action.PLAY_PATH"
    const val EXTRA_PATH = "path"
    const val EXTRA_TITLE = "title"
    const val EXTRA_AUTHOR = "author"
    const val EXTRA_THUMBNAIL = "thumbnail"
    val isInPictureInPictureMode = MutableStateFlow(false)

    fun start(context: Context, item: PlaybackQueueItem) {
        PlaybackStartStore.set(PlaybackStartRequest(queue = listOf(item), startIndex = 0))
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_PLAY_PATH)
                .putExtra(EXTRA_PATH, item.path)
                .putExtra(EXTRA_TITLE, item.title)
                .putExtra(EXTRA_AUTHOR, item.author)
                .putExtra(EXTRA_THUMBNAIL, item.thumbnailUrl)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun pendingIntent(context: Context, path: String?, title: String, author: String = ""): PendingIntent? {
        if (path.isNullOrBlank()) return null
        val intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_PLAY_PATH)
                .putExtra(EXTRA_PATH, path)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_AUTHOR, author)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            path.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
