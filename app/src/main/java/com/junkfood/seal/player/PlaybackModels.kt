package com.junkfood.seal.player

data class PlaybackQueueItem(
    val id: Int,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val path: String,
)

data class PlaybackStartRequest(val queue: List<PlaybackQueueItem>, val startIndex: Int)

sealed interface StopRule {
    data object None : StopRule

    data class Timer(
        val durationMs: Long,
        val startedAtMs: Long = System.currentTimeMillis(),
        val waitForCurrentItem: Boolean = false,
    ) : StopRule

    data class ItemCount(val targetCount: Int, val completedCount: Int = 0) : StopRule
}

fun StopRule.label(nowMs: Long = System.currentTimeMillis()): String =
    when (this) {
        StopRule.None -> ""
        is StopRule.ItemCount -> "还剩 ${targetCount - completedCount} 个视频后停止"
        is StopRule.Timer -> {
            val remaining = (durationMs - (nowMs - startedAtMs)).coerceAtLeast(0)
            if (waitForCurrentItem && remaining == 0L) "当前视频结束后停止"
            else "将在 ${remaining.toStopRuleDurationText()} 后停止"
        }
    }

private fun Long.toStopRuleDurationText(): String {
    val totalSeconds = ((this + 999) / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, minutes, seconds)
        minutes > 0 -> "%d:%02d".format(minutes, seconds)
        else -> "${seconds}秒"
    }
}
