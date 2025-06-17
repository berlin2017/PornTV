package com.berlin.porntv.ui.player

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackGroup
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.berlin.porntv.UnsafeOkHttpClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

// Seek behavior constants
private const val INITIAL_LONG_PRESS_DELAY_MS = 500L // Time to hold before continuous seek starts
private const val CONTINUOUS_SEEK_INTERVAL_MS = 300L // Interval between seeks during long press
private const val SEEK_INCREMENT_MS = 10_000L // Amount to seek by in ms (10 seconds)


@OptIn(
    ExperimentalMaterial3Api::class // This OptIn is repeated, consolidate if PlayerScreen already has it at the top
)
@Composable
fun PlayerScreen(
    videoId: String, navController: NavController, viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val unsafeOkHttpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient()
    val httpDataSourceFactory: DataSource.Factory = OkHttpDataSource.Factory(unsafeOkHttpClient)
    val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

    val exoPlayer = remember {
        ExoPlayer.Builder(context).setMediaSourceFactory(mediaSourceFactory).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    data class ResolutionOption(
        val label: String, val trackGroup: TrackGroup, val trackIndex: Int
    )

    var isLoading by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }

    var currentPlaybackSpeed by remember { mutableFloatStateOf(1.0f) }
    val supportedSpeeds = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }
    var showSpeedSelection by remember { mutableStateOf(false) }

    var availableResolutions by remember { mutableStateOf<List<ResolutionOption>>(emptyList()) }
    var selectedResolutionLabel by remember { mutableStateOf("Auto") }
    var showResolutionSelection by remember { mutableStateOf(false) }

    var controlsVisible by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var hideControlsJob: Job? by remember { mutableStateOf(null) }

    // Seek states
    var longPressRewindJob by remember { mutableStateOf<Job?>(null) }
    var longPressFastForwardJob by remember { mutableStateOf<Job?>(null) }
    var seekDirection: Int? by remember { mutableStateOf(null) } // -1 for rewind, 1 for fast-forward
    var seekProgressCount by remember { mutableStateOf(0) } // Number of seek increments performed


    val playerFocusRequester = remember { FocusRequester() }
    val playPauseButtonFocusRequester = remember { FocusRequester() }
    val rewindButtonFocusRequester = remember { FocusRequester() }
    val fastForwardButtonFocusRequester = remember { FocusRequester() }
    val speedButtonFocusRequester = remember { FocusRequester() }
    val resolutionButtonFocusRequester = remember { FocusRequester() }
    val playbackSliderFocusRequester = remember { FocusRequester() }


    val firstSpeedOptionFocusRequester = remember { FocusRequester() }
    val firstResolutionOptionFocusRequester = remember { FocusRequester() }

    val showControlsAndHideLater: (Long) -> Unit = { delayMillis ->
        controlsVisible = true
        hideControlsJob?.cancel()
        hideControlsJob = scope.launch {
            delay(delayMillis)
            controlsVisible = false
            // We'll let the more specific LaunchedEffect handle playPauseButton focus.
            // Focus player area if controls are hidden and no popups are shown.
            if (!showSpeedSelection && !showResolutionSelection) {
                try {
                    playerFocusRequester.requestFocus()
                } catch (e: IllegalStateException) {
                    println("PlayerScreen: Error requesting focus on playerFocusRequester: ${e.message}")
                }
            }
        }
    }
    val defaultShowControlsAndHideLater: () -> Unit = { showControlsAndHideLater(5000L) }


    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                Lifecycle.Event.ON_DESTROY -> exoPlayer.release()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isLoading = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onIsPlayingChanged(isPlayingParam: Boolean) {
                isPlaying = isPlayingParam
                if (isPlayingParam && !controlsVisible && !showSpeedSelection && !showResolutionSelection) {
                    try {
                        playerFocusRequester.requestFocus()
                    } catch (e: IllegalStateException) {
                        println("PlayerScreen: Error requesting focus on playerFocusRequester in onIsPlayingChanged: ${e.message}")
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
            ) {
                currentPosition = newPosition.positionMs
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                duration = exoPlayer.duration.coerceAtLeast(0L)
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                viewModel.switchQuality()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(isPlaying) {
        while (isActive && isPlaying) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(1000)
        }
    }

    fun formatTime(ms: Long): String {
        if (ms < 0) return "00:00"
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    LaunchedEffect(videoId) {
        viewModel.fetchVideoDetails(videoId)
    }

    LaunchedEffect(uiState.currentVideoUrl) {
        if (uiState.currentVideoUrl.isNotEmpty()) {
            val mediaItem = MediaItem.Builder().setUri(uiState.currentVideoUrl).build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    // This effect handles focusing the player area when controls/popups are hidden
    LaunchedEffect(controlsVisible, showSpeedSelection, showResolutionSelection) {
        if (!controlsVisible && !showSpeedSelection && !showResolutionSelection) {
            try {
                playerFocusRequester.requestFocus()
            } catch (e: IllegalStateException) {
                println("PlayerScreen: Error requesting focus on playerFocusRequester (global hide): ${e.message}")
            }
        }
    }

    BackHandler(enabled = controlsVisible || showSpeedSelection || showResolutionSelection) {
        if (showSpeedSelection) {
            showSpeedSelection = false
            controlsVisible = true
            return@BackHandler
        }
        if (showResolutionSelection) {
            showResolutionSelection = false
            controlsVisible = true
            return@BackHandler
        }
        if (controlsVisible) {
            controlsVisible = false
        }
    }


    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "加载失败: ${uiState.error}",
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                    Button(onClick = { viewModel.fetchVideoDetails(videoId) }) { Text("重试") }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { navController.popBackStack() }) { Text("返回") }
                }
            }
        } else {
            Box(modifier = Modifier
                .fillMaxSize()
                .onKeyEvent { keyEvent ->
//                        if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
//                            if (keyEvent.type == KeyEventType.KeyUp) {
//                                if (showSpeedSelection) {
//                                    showSpeedSelection = false
//                                    controlsVisible = true // Show main controls
//                                    scope.launch {
//                                        delay(50)
//                                        try {
//                                            speedButtonFocusRequester.requestFocus()
//                                        } catch (e: IllegalStateException) {
//                                            println("PlayerScreen: Error focusing speed button on back: ${e.message}")
//                                        }
//                                    }
//8                                    return@onKeyEvent true
//                                }
//                                if (showResolutionSelection) {
//                                    showResolutionSelection = false
//                                    controlsVisible = true // Show main controls
//                                    scope.launch {
//                                        delay(50)
//                                        try {
//                                            resolutionButtonFocusRequester.requestFocus()
//                                        } catch (e: IllegalStateException) {
//                                            println("PlayerScreen: Error focusing res button on back: ${e.message}")
//                                        }
//                                    }
//                                    return@onKeyEvent true
//                                }
//                                if (controlsVisible) {
//                                    controlsVisible = false
//                                    hideControlsJob?.cancel()
//                                    try {
//                                        playerFocusRequester.requestFocus()
//                                    } catch (e: IllegalStateException) {
//                                        println("PlayerScreen: Error focusing player on back from controls: ${e.message}")
//                                    }
//                                    return@onKeyEvent true
//                                }
//                                return@onKeyEvent true
//                            }
//                            // If none of the above, allow normal back navigation by returning false
//                            return@onKeyEvent controlsVisible || showSpeedSelection || showResolutionSelection
//                        }

                    // Handle media keys only if no popups are active
                    if (!showSpeedSelection && !showResolutionSelection && !controlsVisible) {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                    exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                                    defaultShowControlsAndHideLater()
                                    return@onKeyEvent true
                                }

                                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                    exoPlayer.play()
                                    defaultShowControlsAndHideLater()
                                    return@onKeyEvent true
                                }

                                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                    exoPlayer.pause()
                                    defaultShowControlsAndHideLater()
                                    return@onKeyEvent true
                                }

                                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                    if (longPressFastForwardJob?.isActive == true) return@onKeyEvent true

                                    longPressRewindJob?.cancel()
                                    longPressRewindJob = null

                                    seekDirection = 1
                                    val targetPosition =
                                        (exoPlayer.currentPosition + SEEK_INCREMENT_MS).coerceAtMost(
                                            duration
                                        )
                                    exoPlayer.seekTo(targetPosition)
                                    currentPosition = targetPosition
                                    seekProgressCount = 1

                                    longPressFastForwardJob = scope.launch {
                                        delay(INITIAL_LONG_PRESS_DELAY_MS)
                                        if (isActive) {
                                            while (isActive) {
                                                val newTarget =
                                                    (exoPlayer.currentPosition + SEEK_INCREMENT_MS).coerceAtMost(
                                                        duration
                                                    )
                                                if (exoPlayer.currentPosition == newTarget && newTarget == duration) break
                                                exoPlayer.seekTo(newTarget)
                                                currentPosition = newTarget
                                                seekProgressCount++
                                                delay(CONTINUOUS_SEEK_INTERVAL_MS)
                                            }
                                        }
                                    }
                                    return@onKeyEvent true
                                }

                                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                    if (longPressRewindJob?.isActive == true) return@onKeyEvent true

                                    longPressFastForwardJob?.cancel()
                                    longPressFastForwardJob = null

                                    seekDirection = -1
                                    val targetPosition =
                                        (exoPlayer.currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(
                                            0L
                                        )
                                    exoPlayer.seekTo(targetPosition)
                                    currentPosition = targetPosition
                                    seekProgressCount = 1

                                    longPressRewindJob = scope.launch {
                                        delay(INITIAL_LONG_PRESS_DELAY_MS)
                                        if (isActive) {
                                            while (isActive) {
                                                val newTarget =
                                                    (exoPlayer.currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(
                                                        0L
                                                    )
                                                if (exoPlayer.currentPosition == newTarget && newTarget == 0L) break
                                                exoPlayer.seekTo(newTarget)
                                                currentPosition = newTarget
                                                seekProgressCount++
                                                delay(CONTINUOUS_SEEK_INTERVAL_MS)
                                            }
                                        }
                                    }
                                    return@onKeyEvent true
                                }

                                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    defaultShowControlsAndHideLater()
                                    return@onKeyEvent true
                                }

                                else -> return@onKeyEvent false
                            }
                        } else if (keyEvent.type == KeyEventType.KeyUp) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                    longPressFastForwardJob?.cancel()
                                    longPressFastForwardJob = null
                                    if (seekDirection == 1) {
                                        seekDirection = null
                                        seekProgressCount = 0
                                        if (!exoPlayer.isPlaying && exoPlayer.playbackState != Player.STATE_ENDED) {
                                            exoPlayer.play()
                                        }
                                    }
                                    return@onKeyEvent true
                                }

                                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                    longPressRewindJob?.cancel()
                                    longPressRewindJob = null
                                    if (seekDirection == -1) {
                                        seekDirection = null
                                        seekProgressCount = 0
                                        if (!exoPlayer.isPlaying && exoPlayer.playbackState != Player.STATE_ENDED) {
                                            exoPlayer.play()
                                        }
                                    }
                                    return@onKeyEvent true
                                }

                                else -> return@onKeyEvent keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK
                            }
                        }
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
                .focusRequester(playerFocusRequester)
                .focusable()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused && !controlsVisible && !showSpeedSelection && !showResolutionSelection) {
                        defaultShowControlsAndHideLater()
                    }
                }) {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.player = exoPlayer // Set player here
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                }, update = { view ->
                    view.player = exoPlayer // Ensure player is updated
                })

                AnimatedVisibility(
                    visible = isLoading,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                AnimatedVisibility(
                    visible = seekDirection != null && seekProgressCount > 0,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        val seekText =
                            "${if (seekDirection == -1) "<<" else ">>"} ${seekProgressCount * (SEEK_INCREMENT_MS / 1000L)}s"
                        val iconSize = 64.dp
                        val iconTint = MaterialTheme.colorScheme.onSurface
                        if (seekDirection == -1) Icon(
                            Icons.Default.FastRewind,
                            "Rewinding",
                            Modifier.size(iconSize),
                            tint = iconTint
                        )
                        Text(
                            text = seekText,
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        if (seekDirection == 1) Icon(
                            Icons.Default.FastForward,
                            "Fast-Forwarding",
                            Modifier.size(iconSize),
                            tint = iconTint
                        )
                    }
                }

                AnimatedVisibility(
                    visible = controlsVisible && !showSpeedSelection && !showResolutionSelection,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                ) {
                    // This LaunchedEffect will run when this AnimatedVisibility becomes true
                    LaunchedEffect(Unit) { // Key 'Unit' means it runs once when composed/recomposed
                        delay(1000) // Small delay to ensure the button is ready
                        try {
                            playPauseButtonFocusRequester.requestFocus()
                        } catch (e: IllegalStateException) {
                            println("PlayerScreen: Error requesting focus on playPauseButton: ${e.message}")
                            // Fallback or log, button might not be ready yet
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(currentPosition),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Slider(
                                value = currentPosition.toFloat(),
                                onValueChange = {
                                    exoPlayer.seekTo(it.toLong())
                                    currentPosition =
                                        it.toLong() // Update currentPosition immediately for smoother UI
                                    defaultShowControlsAndHideLater()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(playbackSliderFocusRequester)
                                    .focusable()
                                    .onFocusChanged { if (it.isFocused) defaultShowControlsAndHideLater() }
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.nativeKeyEvent.keyCode) {
                                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                    val newPos =
                                                        (currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(
                                                            0L
                                                        )
                                                    exoPlayer.seekTo(newPos)
                                                    currentPosition = newPos
                                                    defaultShowControlsAndHideLater()
                                                    true
                                                }

                                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                    val newPos =
                                                        (currentPosition + SEEK_INCREMENT_MS).coerceAtMost(
                                                            duration
                                                        )
                                                    exoPlayer.seekTo(newPos)
                                                    currentPosition = newPos
                                                    defaultShowControlsAndHideLater()
                                                    true
                                                }

                                                else -> false
                                            }
                                        } else false
                                    },
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.secondary,
                                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
                                ),
                                valueRange = 0f..duration.toFloat().coerceAtLeast(0f)
                            )
                            Text(
                                text = formatTime(duration),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))


                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly, // Distribute space
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val targetPosition =
                                        (exoPlayer.currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(
                                            0L
                                        )
                                    exoPlayer.seekTo(targetPosition)
                                    currentPosition = targetPosition
                                    defaultShowControlsAndHideLater()
                                }, modifier = Modifier.focusable()
                            ) {
                                Icon(
                                    Icons.Filled.FastRewind,
                                    "Rewind",
                                    Modifier.size(48.dp),
                                    MaterialTheme.colorScheme.onSurface
                                )
                            }

                            IconButton(
                                onClick = {
                                    exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                                    defaultShowControlsAndHideLater()
                                },
                                modifier = Modifier.focusable(),
                                enabled = true,
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    if (isPlaying) "Pause" else "Play",
                                    Modifier.size(64.dp),
                                    MaterialTheme.colorScheme.onSurface
                                )
                            }

                            IconButton(
                                onClick = {
                                    val targetPosition =
                                        (exoPlayer.currentPosition + SEEK_INCREMENT_MS).coerceAtMost(
                                            duration
                                        )
                                    exoPlayer.seekTo(targetPosition)
                                    currentPosition = targetPosition
                                    defaultShowControlsAndHideLater()
                                },
                                modifier = Modifier
                                    .focusRequester(fastForwardButtonFocusRequester)
                                    .focusable()
                                    .onFocusChanged { if (it.isFocused) defaultShowControlsAndHideLater() },
                                enabled = true,
                            ) {
                                Icon(
                                    Icons.Filled.FastForward,
                                    "Fast Forward",
                                    Modifier.size(48.dp),
                                    MaterialTheme.colorScheme.onSurface
                                )
                            }

                            IconButton(
                                onClick = {
                                    controlsVisible = false // Hide main controls
                                    showSpeedSelection = true
                                    scope.launch {
                                        delay(50)
                                        try {
                                            firstSpeedOptionFocusRequester.requestFocus()
                                        } catch (e: IllegalStateException) {
                                            println("PlayerScreen: Error focusing first speed option: ${e.message}")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .focusRequester(speedButtonFocusRequester)
                                    .focusable()
                                    .onFocusChanged { if (it.isFocused) defaultShowControlsAndHideLater() },
                                enabled = true,
                            ) {
                                Icon(
                                    Icons.Filled.Speed,
                                    "Playback Speed",
                                    Modifier.size(48.dp),
                                    MaterialTheme.colorScheme.onSurface
                                )
                            }

                            IconButton(
                                onClick = {
                                    controlsVisible = false // Hide main controls
                                    showResolutionSelection = true
                                    scope.launch {
                                        delay(50)
                                        try {
                                            firstResolutionOptionFocusRequester.requestFocus()
                                        } catch (e: IllegalStateException) {
                                            println("PlayerScreen: Error focusing first res option: ${e.message}")
                                        }
                                    }
                                },
                                enabled = availableResolutions.size > 1, // Example: disable if only one res
                                modifier = Modifier
                                    .focusRequester(resolutionButtonFocusRequester)
                                    .focusable()
                                    .onFocusChanged { if (it.isFocused) defaultShowControlsAndHideLater() }) {
                                Icon(
                                    Icons.Filled.HighQuality,
                                    "Resolution",
                                    Modifier.size(48.dp),
                                    if (availableResolutions.size > 1) MaterialTheme.colorScheme.onSurface else Color.Gray
                                )
                            }
                        }
                    }
                }

                // Playback Speed Selection Popup
                AnimatedVisibility(
                    visible = showSpeedSelection,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.5f)
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                        .padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Select Playback Speed",
                            color = Color.White,
                            fontSize = 22.sp,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                        supportedSpeeds.forEachIndexed { index, speed ->
                            Button(
                                onClick = {
                                    exoPlayer.playbackParameters = PlaybackParameters(speed)
                                    currentPlaybackSpeed = speed
                                    showSpeedSelection = false
                                    controlsVisible = true // Show main controls
                                    scope.launch {
                                        delay(50)
                                        try {
                                            speedButtonFocusRequester.requestFocus()
                                        } catch (e: IllegalStateException) {
                                            println("PlayerScreen: Error focusing speed button from popup: ${e.message}")
                                        }
                                    }
                                    defaultShowControlsAndHideLater()
                                }, colors = ButtonDefaults.buttonColors(
                                    containerColor = if (speed == currentPlaybackSpeed) MaterialTheme.colorScheme.primary else Color.DarkGray,
                                    contentColor = Color.White
                                ), modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .then(
                                        if (index == 0) Modifier.focusRequester(
                                            firstSpeedOptionFocusRequester
                                        ) else Modifier
                                    )
                                    .onFocusChanged { if (it.isFocused) defaultShowControlsAndHideLater() }) {
                                Text(
                                    "${speed}x", fontSize = 18.sp
                                )
                            }
                        }
                    }
                }

                // Resolution Selection Popup (Simplified Example)
                AnimatedVisibility(
                    visible = showResolutionSelection,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.5f)
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                        .padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Select Resolution",
                            color = Color.White,
                            fontSize = 22.sp,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                        // Replace with your actual resolution logic
                        Button( // Example Button
                            onClick = {
                                showResolutionSelection = false
                                controlsVisible = true // Show main controls
                                scope.launch {
                                    delay(50)
                                    try {
                                        resolutionButtonFocusRequester.requestFocus()
                                    } catch (e: IllegalStateException) {
                                        println("PlayerScreen: Error focusing res button from popup: ${e.message}")
                                    }
                                }
                                defaultShowControlsAndHideLater()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .focusRequester(firstResolutionOptionFocusRequester)
                                .onFocusChanged { if (it.isFocused) defaultShowControlsAndHideLater() }) {
                            Text(
                                "Auto (Example)", fontSize = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}