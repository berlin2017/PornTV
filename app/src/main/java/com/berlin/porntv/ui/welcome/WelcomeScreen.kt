package com.berlin.porntv.ui.welcome

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        WelcomeAnimation(navController)
    }
}

@Composable
fun WelcomeAnimation(navController: NavController) {
    val scale = remember { Animatable(0.3f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(key1 = true) {
        // 放大动画
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = EaseOutQuad)
        )
        // 淡入动画
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800)
        )
        // 等待3秒后导航到主页
        delay(3000)
        navController.navigate("home") {
            popUpTo("welcome") { inclusive = true }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.alpha(alpha.value)
    ) {
        // Logo动画
//        Image(
//            painter = painterResource(id = R.drawable.app_logo),
//            contentDescription = "App Logo",
//            modifier = Modifier
//                .size(200.dp)
//                .scale(scale.value)
//        )

        Spacer(modifier = Modifier.height(32.dp))

        // 应用名称
        Text(
            text = "TV视频应用",
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 加载指示器
        LoadingDots()
    }
}

@Composable
fun LoadingDots() {
    val infiniteTransition = rememberInfiniteTransition()

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(16.dp)
    ) {
        repeat(3) { index ->
            val delay = index * 150

            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 0.5f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1000
                        0.5f at 0
                        1.0f at 300 + delay
                        0.5f at 600 + delay
                    },
                    repeatMode = RepeatMode.Restart
                )
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(12.dp * scale)
                    .background(MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small)
            )
        }
    }
}