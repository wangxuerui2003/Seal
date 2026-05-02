package com.junkfood.seal.player

import com.junkfood.seal.util.PreferenceUtil
import com.junkfood.seal.util.PreferenceUtil.getBoolean
import com.junkfood.seal.util.PreferenceUtil.getInt
import com.junkfood.seal.util.PreferenceUtil.getLong
import com.junkfood.seal.util.PreferenceUtil.getString
import com.junkfood.seal.util.PreferenceUtil.updateBoolean
import com.junkfood.seal.util.PreferenceUtil.updateInt
import com.junkfood.seal.util.PreferenceUtil.updateLong
import com.junkfood.seal.util.PreferenceUtil.updateString

private const val PLAYBACK_SPEED = "player_speed_percent"
private const val AUTO_PIP = "player_auto_pip"
private const val LAST_STOP_RULE_TYPE = "player_stop_rule_type"
private const val LAST_TIMER_DURATION = "player_last_timer_duration"
private const val LAST_TIMER_WAIT_CURRENT = "player_last_timer_wait_current"
private const val LAST_ITEM_COUNT = "player_last_item_count"
private const val PROGRESS_PREFIX = "player_progress_"
private const val DURATION_PREFIX = "player_duration_"

object PlaybackPreferences {
    data class Progress(val positionMs: Long, val durationMs: Long)

    fun getSpeed(): Float = PLAYBACK_SPEED.getInt(default = 100).coerceIn(50, 200) / 100f

    fun setSpeed(speed: Float) {
        PLAYBACK_SPEED.updateInt((speed * 100).toInt().coerceIn(50, 200))
    }

    fun isAutoPipEnabled(): Boolean = AUTO_PIP.getBoolean(default = true)

    fun setAutoPipEnabled(enabled: Boolean) {
        AUTO_PIP.updateBoolean(enabled)
    }

    fun saveTimerPreset(durationMs: Long, waitForCurrentItem: Boolean) {
        LAST_STOP_RULE_TYPE.updateString("timer")
        LAST_TIMER_DURATION.updateLong(durationMs)
        LAST_TIMER_WAIT_CURRENT.updateBoolean(waitForCurrentItem)
    }

    fun saveItemCountPreset(count: Int) {
        LAST_STOP_RULE_TYPE.updateString("count")
        LAST_ITEM_COUNT.updateInt(count.coerceIn(1, 10))
    }

    fun lastStopRuleType(): String = LAST_STOP_RULE_TYPE.getString()

    fun lastTimerDuration(): Long = LAST_TIMER_DURATION.getLong(default = 30 * 60_000L)

    fun lastTimerWaitForCurrent(): Boolean = LAST_TIMER_WAIT_CURRENT.getBoolean(default = false)

    fun lastItemCount(): Int = LAST_ITEM_COUNT.getInt(default = 1).coerceIn(1, 10)

    fun saveProgress(mediaId: String, positionMs: Long, durationMs: Long) {
        if (mediaId.isBlank()) return
        "$PROGRESS_PREFIX$mediaId".updateLong(positionMs.coerceAtLeast(0L))
        "$DURATION_PREFIX$mediaId".updateLong(durationMs.coerceAtLeast(0L))
    }

    fun getResumePosition(mediaId: String): Long {
        val position = "$PROGRESS_PREFIX$mediaId".getLong(default = 0L)
        val duration = "$DURATION_PREFIX$mediaId".getLong(default = 0L)
        return if (position > 30_000L && (duration <= 0L || duration - position > 30_000L)) {
            position
        } else {
            0L
        }
    }

    fun getProgress(mediaId: String): Progress? {
        if (mediaId.isBlank()) return null
        val position = "$PROGRESS_PREFIX$mediaId".getLong(default = 0L)
        val duration = "$DURATION_PREFIX$mediaId".getLong(default = 0L)
        if (position < 5_000L) return null
        return Progress(positionMs = position, durationMs = duration)
    }
}
