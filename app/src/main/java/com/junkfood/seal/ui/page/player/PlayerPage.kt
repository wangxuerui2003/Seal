@file:OptIn(
    ExperimentalAnimationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalStdlibApi::class,
)

package com.junkfood.seal.ui.page.player

import android.app.Activity
import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateOf
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.common.util.concurrent.MoreExecutors
import com.junkfood.seal.player.PlaybackPreferences
import com.junkfood.seal.player.PlaybackLauncher
import com.junkfood.seal.player.PlaybackQueueBuilder
import com.junkfood.seal.player.PlaybackService
import com.junkfood.seal.player.PlaybackStartStore
import com.junkfood.seal.player.StopRule
import com.junkfood.seal.player.label
import com.junkfood.seal.util.ToastUtil
import com.junkfood.seal.R
import com.junkfood.seal.ui.common.LocalDarkTheme
import com.junkfood.seal.ui.theme.SealTheme
import java.lang.ref.WeakReference
import kotlin.OptIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PlayerPage(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    var controller by remember { mutableStateOf<MediaController?>(null) }
    val serviceState by PlaybackService.stateFlow.collectAsState()
    val isInPip by PlaybackLauncher.isInPictureInPictureMode.collectAsState()
    val notificationPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS) { isGranted ->
                if (!isGranted) ToastUtil.makeToast(R.string.permission_denied)
            }
        } else null

    LaunchedEffect(notificationPermission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            notificationPermission?.status?.isGranted == false
        ) {
            notificationPermission.launchPermissionRequest()
        }
    }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                controller = future.get()
                scope.launch { startPendingPlayback(context, future.get()) }
            },
            MoreExecutors.directExecutor(),
        )
        onDispose {
            controller?.let { MediaController.releaseFuture(future) }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            PlaybackLauncher.isInPictureInPictureMode.value = false
        }
    }

    DisposableEffect(activity, controller) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null) {
            runCatching {
                activity.setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .setAutoEnterEnabled(PlaybackPreferences.isAutoPipEnabled())
                        .build()
                )
            }
        }
        onDispose {}
    }

    BackHandler {
        PlaybackService.current?.stopPlayback()
        onNavigateBack()
    }

    val player = controller
    if (player == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("正在加载播放器") }
        return
    }

    PlayerScreen(
        player = player,
        stopRule = serviceState.stopRule,
        errorMessage = serviceState.errorMessage,
        isFullscreen = false,
        isInPip = isInPip,
        onBack = {
            PlaybackService.current?.stopPlayback()
            onNavigateBack()
        },
        onRetry = { PlaybackService.current?.retry() },
        onToggleFullscreen = {
            context.startActivity(Intent(context, FullscreenPlayerActivity::class.java))
        },
        onEnterPip = { activity?.enterPlayerPip() },
    )
}

private fun startPendingPlayback(context: Context, player: MediaController) {
    val request = PlaybackStartStore.consume() ?: return
    val items = PlaybackQueueBuilder.build(context, request.queue)
    if (items.isEmpty()) return
    val requestedId = request.queue.getOrNull(request.startIndex)?.id?.toString()
    val startIndex = items.indexOfFirst { it.mediaId == requestedId }.coerceAtLeast(0)
    player.setMediaItems(items, startIndex, 0L)
    player.prepare()
    player.play()
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerScreen(
    player: Player,
    stopRule: StopRule,
    errorMessage: String?,
    isFullscreen: Boolean,
    isInPip: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onEnterPip: () -> Unit,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var title by remember { mutableStateOf(player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()) }
    var author by remember { mutableStateOf(player.currentMediaItem?.mediaMetadata?.artist?.toString().orEmpty()) }
    var artwork by remember { mutableStateOf(player.currentMediaItem?.mediaMetadata?.artworkUri) }
    var position by remember { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var duration by remember { mutableLongStateOf(player.duration.takeUnless { it == C.TIME_UNSET } ?: 0L) }
    var showStopSheet by remember { mutableStateOf(false) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(player.playbackParameters.speed) }
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    var seekFeedbackTotal by remember { mutableIntStateOf(0) }
    var hasRenderedFirstFrame by remember { mutableStateOf(false) }
    var stopRuleLabelTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(value: Boolean) {
                    isPlaying = value
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    title = mediaItem?.mediaMetadata?.title?.toString().orEmpty()
                    author = mediaItem?.mediaMetadata?.artist?.toString().orEmpty()
                    artwork = mediaItem?.mediaMetadata?.artworkUri
                    hasRenderedFirstFrame = false
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    duration = player.duration.takeUnless { it == C.TIME_UNSET } ?: 0L
                }

                override fun onRenderedFirstFrame() {
                    hasRenderedFirstFrame = true
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeUnless { it == C.TIME_UNSET } ?: 0L
            speed = player.playbackParameters.speed
            delay(500L)
        }
    }

    LaunchedEffect(stopRule) {
        while (stopRule is StopRule.Timer) {
            stopRuleLabelTick = System.currentTimeMillis()
            delay(1000L)
        }
    }

    LaunchedEffect(seekFeedbackText) {
        if (seekFeedbackText != null) {
            delay(700L)
            seekFeedbackText = null
            seekFeedbackTotal = 0
        }
    }

    if (showStopSheet) {
        StopRuleSheet(
            activeRule = stopRule,
            onDismiss = { showStopSheet = false },
            onSetRule = {
                PlaybackService.current?.setStopRule(it)
                ToastUtil.makeToast("已设置：${it.label()}")
                showStopSheet = false
            },
            onClearRule = {
                PlaybackService.current?.clearStopRule()
                ToastUtil.makeToast("已清除定时关闭")
                showStopSheet = false
            },
        )
    }

    val playerSurface: @Composable (Modifier) -> Unit = { modifier ->
        BoxWithConstraints(
            modifier =
                modifier
                    .background(Color.Black)
                    .pointerInput(player) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = { offset ->
                                val delta = if (offset.x < size.width / 2f) -5_000L else 5_000L
                                val target =
                                    (player.currentPosition + delta)
                                        .coerceIn(0L, duration.coerceAtLeast(0L))
                                player.seekTo(target)
                                seekFeedbackTotal += if (delta < 0) -5 else 5
                                seekFeedbackText =
                                    if (seekFeedbackTotal < 0) "${seekFeedbackTotal}s"
                                    else "+${seekFeedbackTotal}s"
                                controlsVisible = true
                            },
                        )
                    },
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = {
                    (LayoutInflater.from(it)
                        .inflate(R.layout.player_view_texture, null, false) as PlayerView)
                    .apply {
                        useController = false
                        layoutParams =
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                    }
                },
                update = {
                    FullscreenPlayerActivity.attachPlayerView(player, it, isFullscreen)
                },
                onRelease = {
                    if (isFullscreen) FullscreenPlayerActivity.fullscreenPlayerView = null
                    else FullscreenPlayerActivity.inlinePlayerView = null
                    if (it.player === player) it.player = null
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (artwork != null && !hasRenderedFirstFrame) {
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    modifier = Modifier.size(220.dp).aspectRatio(1f),
                )
            }
            if (!isInPip) {
                val stopRuleLabel = if (stopRule !is StopRule.None) {
                    stopRule.label(stopRuleLabelTick)
                } else ""
                if (stopRuleLabel.isNotBlank()) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                        color = Color.Black.copy(alpha = 0.58f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = stopRuleLabel,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    PlayerControlsOverlay(
                        player = player,
                        isPlaying = isPlaying,
                        position = position,
                        duration = duration,
                        isFullscreen = isFullscreen,
                        speed = speed,
                        speedMenuExpanded = speedMenuExpanded,
                        onSpeedMenuExpandedChange = { speedMenuExpanded = it },
                        onToggleFullscreen = onToggleFullscreen,
                        onEnterPip = onEnterPip,
                        onShowStopSheet = { showStopSheet = true },
                    )
                }
                seekFeedbackText?.let {
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.Black.copy(alpha = 0.58f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = it,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            if (errorMessage != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.onErrorContainer)
                        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                            Text("重试")
                        }
                    }
                }
            }
        }
    }

    if (isInPip) {
        playerSurface(Modifier.fillMaxSize())
    } else if (isFullscreen) {
        playerSurface(Modifier.fillMaxSize())
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Seal BG", maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                        }
                    },
                )
            }
        ) { padding ->
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(padding)
                        .background(MaterialTheme.colorScheme.background)
            ) {
                playerSurface(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(
                        title.ifBlank { "播放器" },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (author.isNotBlank()) {
                        Text(
                            author,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (stopRule !is StopRule.None) {
                        Text(
                            stopRule.label(stopRuleLabelTick),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    Text(
                        "轻点视频显示或隐藏播放控制。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerControlsOverlay(
    player: Player,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    isFullscreen: Boolean,
    speed: Float,
    speedMenuExpanded: Boolean,
    onSpeedMenuExpandedChange: (Boolean) -> Unit,
    onToggleFullscreen: () -> Unit,
    onEnterPip: () -> Unit,
    onShowStopSheet: () -> Unit,
) {
    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            IconButton(onClick = { player.seekToPreviousMediaItem() }) {
                Icon(Icons.Outlined.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = { if (isPlaying) player.pause() else player.play() }) {
                Icon(
                    if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp),
                )
            }
            IconButton(onClick = { player.seekToNextMediaItem() }) {
                Icon(Icons.Outlined.SkipNext, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Slider(
                value = if (duration > 0) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                onValueChange = { player.seekTo(it.toLong()) },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                modifier = Modifier.height(28.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatTime(position), color = Color.White, style = MaterialTheme.typography.labelSmall)
                Text(formatTime(duration), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(modifier = Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { onSpeedMenuExpandedChange(true) }) {
                    Text("${speed}x", color = Color.White)
                }
                DropdownMenu(
                    expanded = speedMenuExpanded,
                    onDismissRequest = { onSpeedMenuExpandedChange(false) },
                ) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { value ->
                        DropdownMenuItem(
                            text = { Text("${value}x") },
                            onClick = {
                                player.setPlaybackSpeed(value)
                                PlaybackPreferences.setSpeed(value)
                                onSpeedMenuExpandedChange(false)
                            },
                        )
                    }
                }
            }
            IconButton(onClick = onShowStopSheet) { Icon(Icons.Outlined.Timer, null, tint = Color.White) }
            IconButton(onClick = onEnterPip) {
                Icon(Icons.Outlined.PictureInPictureAlt, null, tint = Color.White)
            }
            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    if (isFullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                    null,
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun StopRuleSheet(
    activeRule: StopRule,
    onDismiss: () -> Unit,
    onSetRule: (StopRule) -> Unit,
    onClearRule: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(sheetState = sheetState, onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("定时关闭", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, null) }
        }
        if (activeRule !is StopRule.None) {
            Text(
                activeRule.label(),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        TabRow(selectedTabIndex = selectedTab, modifier = Modifier.padding(top = 8.dp)) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("按时间") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("按视频数") })
        }
        if (selectedTab == 0) {
            TimerRuleContent(onSetRule = onSetRule)
        } else {
            ItemCountRuleContent(onSetRule = onSetRule)
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClearRule) { Text("清除") }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TimerRuleContent(onSetRule: (StopRule) -> Unit) {
    var durationMs by remember { mutableLongStateOf(PlaybackPreferences.lastTimerDuration()) }
    var waitCurrent by remember { mutableStateOf(PlaybackPreferences.lastTimerWaitForCurrent()) }
    val quickDurations =
        listOf(
            "5 分钟" to 5 * 60_000L,
            "10 分钟" to 10 * 60_000L,
            "15 分钟" to 15 * 60_000L,
            "30 分钟" to 30 * 60_000L,
            "1 小时" to 60 * 60_000L,
            "90 分钟" to 90 * 60_000L,
        )
    Column(Modifier.padding(20.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quickDurations.forEach { (label, value) ->
                Button(onClick = { durationMs = value }) { Text(label) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("选择时长", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NumberSelector(
                label = "小时",
                range = 0..12,
                value = (durationMs / 3_600_000L).toInt(),
                onChange = { hours ->
                    val minutes = ((durationMs % 3_600_000L) / 60_000L).toInt()
                    durationMs = ((hours * 60L) + minutes).coerceAtLeast(1L) * 60_000L
                },
                modifier = Modifier.weight(1f),
            )
            NumberSelector(
                label = "分钟",
                range = 0..59,
                value = ((durationMs % 3_600_000L) / 60_000L).toInt(),
                onChange = { minutes ->
                    val hours = (durationMs / 3_600_000L).toInt()
                    durationMs = ((hours * 60L) + minutes).coerceAtLeast(1L) * 60_000L
                },
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("倒计时结束后等当前视频播放完再停止", modifier = Modifier.weight(1f))
            Switch(checked = waitCurrent, onCheckedChange = { waitCurrent = it })
        }
        Button(
            onClick = {
                PlaybackPreferences.saveTimerPreset(durationMs, waitCurrent)
                onSetRule(StopRule.Timer(durationMs = durationMs, waitForCurrentItem = waitCurrent))
            },
            modifier = Modifier.align(Alignment.End).padding(top = 12.dp),
        ) {
            Text("设置 ${formatDuration(durationMs)}")
        }
    }
}

@Composable
private fun ItemCountRuleContent(onSetRule: (StopRule) -> Unit) {
    var count by remember { mutableIntStateOf(PlaybackPreferences.lastItemCount()) }
    Column(Modifier.padding(20.dp)) {
        Text("播放几个视频后停止", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (1..10).forEach { value ->
                Button(onClick = { count = value }, enabled = count != value) { Text(value.toString()) }
            }
        }
        Button(
            onClick = {
                PlaybackPreferences.saveItemCountPreset(count)
                onSetRule(StopRule.ItemCount(targetCount = count))
            },
            modifier = Modifier.align(Alignment.End).padding(top = 20.dp),
        ) {
            Text("设置")
        }
    }
}

@Composable
private fun NumberSelector(
    label: String,
    range: IntRange,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = { onChange((value - 1).coerceAtLeast(range.first)) }) { Text("-") }
            Text(
                value.toString().padStart(2, '0'),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            Button(onClick = { onChange((value + 1).coerceAtMost(range.last)) }) { Text("+") }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private fun formatDuration(ms: Long): String {
    val minutes = (ms / 60_000L).coerceAtLeast(1)
    val hours = minutes / 60
    val rest = minutes % 60
    return if (hours > 0 && rest > 0) "${hours} 小时 ${rest} 分钟"
    else if (hours > 0) "${hours} 小时"
    else "${minutes} 分钟"
}

private fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun Activity.enterPlayerPip() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    runCatching {
        enterPictureInPictureMode(
            PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
        )
    }
}

class FullscreenPlayerActivity : AppCompatActivity() {
    private var controller: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        enableEdgeToEdge()
        enterImmersiveFullscreen()
        setContent {
            SealTheme(
                darkTheme = LocalDarkTheme.current.isDarkTheme(),
                isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
            ) {
                val context = LocalContext.current
                var player by remember { mutableStateOf<MediaController?>(null) }
                val serviceState by PlaybackService.stateFlow.collectAsState()

                DisposableEffect(context) {
                    val token =
                        SessionToken(
                            context,
                            ComponentName(context, PlaybackService::class.java),
                        )
                    val future = MediaController.Builder(context, token).buildAsync()
                    future.addListener(
                        {
                            val mediaController = future.get()
                            controller = mediaController
                            player = mediaController
                        },
                        MoreExecutors.directExecutor(),
                    )
                    onDispose {
                        val mediaController = controller
                        fullscreenPlayerView?.get()?.let { view ->
                            if (view.player === mediaController) view.player = null
                        }
                        fullscreenPlayerView = null
                        restoreInlinePlayerView()
                        MediaController.releaseFuture(future)
                        controller = null
                    }
                }

                player?.let {
                    PlayerScreen(
                        player = it,
                        stopRule = serviceState.stopRule,
                        errorMessage = serviceState.errorMessage,
                        isFullscreen = true,
                        isInPip = false,
                        onBack = { finish() },
                        onRetry = { PlaybackService.current?.retry() },
                        onToggleFullscreen = { finish() },
                        onEnterPip = { enterPlayerPip() },
                    )
                } ?: Box(Modifier.fillMaxSize().background(Color.Black))
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveFullscreen()
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveFullscreen()
    }

    private fun enterImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PlaybackLauncher.isInPictureInPictureMode.value = isInPictureInPictureMode
    }

    companion object {
        var inlinePlayerView: WeakReference<PlayerView>? = null
        private var inlinePlayer: WeakReference<Player>? = null
        var fullscreenPlayerView: WeakReference<PlayerView>? = null

        fun attachPlayerView(player: Player, view: PlayerView, isFullscreen: Boolean) {
            if (isFullscreen) {
                val oldView = inlinePlayerView?.get()
                if (view.player !== player) {
                    PlayerView.switchTargetView(player, oldView, view)
                }
                fullscreenPlayerView = WeakReference(view)
            } else {
                if (view.player !== player && fullscreenPlayerView?.get() == null) {
                    view.player = player
                }
                inlinePlayerView = WeakReference(view)
                inlinePlayer = WeakReference(player)
            }
        }

        fun restoreInlinePlayerView() {
            val player = inlinePlayer?.get() ?: return
            val view = inlinePlayerView?.get() ?: return
            view.player = null
            view.player = player
            player.seekTo(player.currentPosition)
        }
    }
}
