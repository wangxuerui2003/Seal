package com.junkfood.seal.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.junkfood.seal.R
import com.junkfood.seal.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackServiceState(
    val stopRule: StopRule = StopRule.None,
    val errorMessage: String? = null,
)

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null
    private var timerJob: Job? = null
    private var lastCompletedMediaId: String? = null
    private var currentMediaId: String? = null

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(PLAYBACK_NOTIFICATION_ID)
                .setChannelId(PLAYBACK_CHANNEL_ID)
                .setChannelName(R.string.playback_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_stat_seal) }
        )
        player =
            ExoPlayer.Builder(this).build().apply {
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
                playWhenReady = true
                playbackParameters = playbackParameters.withSpeed(PlaybackPreferences.getSpeed())
                addListener(listener)
            }
        currentMediaId = player.currentMediaItem?.mediaId
        val sessionActivity =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        mediaSession =
            MediaSession.Builder(this, player)
                .setSessionActivity(sessionActivity)
                .build()
        current = this
        startProgressUpdates()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    fun setStopRule(rule: StopRule) {
        state.update { it.copy(stopRule = rule) }
        timerJob?.cancel()
        if (rule is StopRule.Timer) startTimer(rule)
    }

    fun clearStopRule() {
        setStopRule(StopRule.None)
    }

    fun retry() {
        state.update { it.copy(errorMessage = null) }
        player.prepare()
        player.play()
    }

    fun stopPlayback() {
        saveCurrentProgress()
        timerJob?.cancel()
        state.update { it.copy(stopRule = StopRule.None, errorMessage = null) }
        player.stop()
        player.clearMediaItems()
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.isPlaying) stopSelf()
    }

    override fun onDestroy() {
        saveCurrentProgress()
        progressJob?.cancel()
        timerJob?.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        current = null
        super.onDestroy()
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob =
            scope.launch {
                while (isActive) {
                    saveCurrentProgress()
                    delay(5_000L)
                }
            }
    }

    private fun startTimer(rule: StopRule.Timer) {
        timerJob =
            scope.launch {
                val elapsed = System.currentTimeMillis() - rule.startedAtMs
                delay((rule.durationMs - elapsed).coerceAtLeast(0L))
                if (rule.waitForCurrentItem) {
                    state.update {
                        it.copy(stopRule = rule.copy(durationMs = 0L, startedAtMs = 0L))
                    }
                } else {
                    player.pause()
                    clearStopRule()
                }
            }
    }

    private fun saveCurrentProgress() {
        val currentItem = player.currentMediaItem ?: return
        val position = player.currentPosition.takeUnless { it == C.TIME_UNSET } ?: return
        val duration = player.duration.takeUnless { it == C.TIME_UNSET } ?: 0L
        PlaybackPreferences.saveProgress(currentItem.mediaId, position, duration)
    }

    private fun handleEndedItem(mediaId: String? = currentMediaId ?: player.currentMediaItem?.mediaId) {
        if (mediaId == null) return
        if (lastCompletedMediaId == mediaId) return
        lastCompletedMediaId = mediaId

        when (val rule = state.value.stopRule) {
            StopRule.None -> Unit
            is StopRule.Timer -> {
                if (rule.durationMs == 0L) {
                    player.pause()
                    clearStopRule()
                }
            }
            is StopRule.ItemCount -> {
                val completed = rule.completedCount + 1
                if (completed >= rule.targetCount) {
                    player.pause()
                    clearStopRule()
                } else {
                    state.update { it.copy(stopRule = rule.copy(completedCount = completed)) }
                }
            }
        }
    }

    private val listener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) handleEndedItem()
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) handleEndedItem(currentMediaId)
                lastCompletedMediaId = null
                currentMediaId = mediaItem?.mediaId
                mediaItem?.mediaId?.let { id ->
                    val resumePosition = PlaybackPreferences.getResumePosition(id)
                    if (resumePosition > 0L && reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        player.seekTo(resumePosition)
                    }
                }
            }

            override fun onPlaybackParametersChanged(
                playbackParameters: androidx.media3.common.PlaybackParameters
            ) {
                PlaybackPreferences.setSpeed(playbackParameters.speed)
            }

            override fun onPlayerError(error: PlaybackException) {
                state.update { it.copy(errorMessage = error.localizedMessage ?: error.message) }
            }
        }

    companion object {
        private val state = MutableStateFlow(PlaybackServiceState())
        val stateFlow = state.asStateFlow()
        var current: PlaybackService? = null
            private set
        private const val PLAYBACK_NOTIFICATION_ID = 9124
        private const val PLAYBACK_CHANNEL_ID = "seal_bg_playback"
    }
}
