package com.berlin.porntv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.berlin.porntv.ui.home.HomeScreen
import com.berlin.porntv.ui.player.PlayerScreen
import com.berlin.porntv.ui.theme.TvAppTheme
import com.berlin.porntv.ui.welcome.WelcomeScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvAppTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "welcome",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("welcome") {
                        WelcomeScreen(navController = navController)
                    }
                    composable("home") {
                        HomeScreen(navController = navController)
                    }
                    composable("player/{videoId}") { backStackEntry ->
                        val videoId = backStackEntry.arguments?.getString("videoId") ?: ""
                        PlayerScreen(videoId = "https://cn.pornhub.com/view_video.php?viewkey=$videoId", navController = navController)
                    }
                }
            }
        }
    }
}