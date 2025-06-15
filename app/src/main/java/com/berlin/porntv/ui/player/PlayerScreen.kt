package com.berlin.porntv.ui.player

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.berlin.porntv.UnsafeOkHttpClient
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.util.MimeTypes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoId: String,
    navController: NavController,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var showQualitySelector by remember { mutableStateOf(false) }

    val unsafeOkHttpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient()
    val httpDataSourceFactory: DataSource.Factory = OkHttpDataSource.Factory(unsafeOkHttpClient)
    val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

    // 创建ExoPlayer实例
    val exoPlayer = remember {
        ExoPlayer.Builder(context).setMediaSourceFactory(mediaSourceFactory).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // 监听生命周期事件
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.pause()
                }

                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    exoPlayer.play()
                }

                androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> {
                    exoPlayer.release()
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    // 加载视频详情
    LaunchedEffect(videoId) {
        viewModel.fetchVideoDetails(videoId)
    }

    // 更新播放源
    LaunchedEffect(uiState.currentVideoUrl) {
        if (uiState.currentVideoUrl.isNotEmpty()) {
            val mediaItemBuilder = MediaItem.Builder()
                .setUri(uiState.currentVideoUrl)

            if (uiState.selectedQuality != null) {
                val type = if (uiState.selectedQuality?.protocol == "m3u8_native")
                    MimeTypes.APPLICATION_M3U8
                else MimeTypes.APPLICATION_MP4
                mediaItemBuilder.setMimeType(type)
            } else {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MP4)
            }

            exoPlayer.setMediaItem(mediaItemBuilder.build())
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            // 加载中
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            // 错误提示
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "加载失败: ${uiState.error}",
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )

                    Button(onClick = { viewModel.fetchVideoDetails(videoId) }) {
                        Text("重试")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = { navController.popBackStack() }) {
                        Text("返回")
                    }
                }
            }
        } else {
            // 视频播放器
            Column(modifier = Modifier.fillMaxSize()) {
                // 视频标题
                TopAppBar(
                    title = { Text(uiState.videoDetail?.title ?: "") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showQualitySelector = !showQualitySelector }) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                    },
                )

                // ExoPlayer视图
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                            useController = true
                            controllerAutoShow = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // 清晰度选择器
            if (showQualitySelector) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(32.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Card(
                        modifier = Modifier
                            .width(200.dp)
                            .padding(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "选择清晰度",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            LazyColumn {
                                items(uiState.videoDetail?.qualities ?: emptyList()) { quality ->
                                    val isSelected = quality == uiState.selectedQuality

                                    Button(
                                        onClick = {
                                            viewModel.selectQuality(quality)
                                            showQualitySelector = false
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Text(quality.label)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { showQualitySelector = false },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("关闭")
                            }
                        }
                    }
                }
            }
        }
    }
}