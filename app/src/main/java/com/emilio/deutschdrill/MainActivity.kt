package com.emilio.deutschdrill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.emilio.deutschdrill.ui.drill.DrillScreen
import com.emilio.deutschdrill.ui.home.HomeScreen
import com.emilio.deutschdrill.ui.library.LibraryScreen
import com.emilio.deutschdrill.ui.settings.SettingsScreen
import com.emilio.deutschdrill.ui.theme.DrillTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as DrillApp
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle()
            DrillTheme(settings.theme) {
                Surface(Modifier.fillMaxSize()) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                app = app,
                                onStart = { nav.navigate("drill") },
                                onLibrary = { nav.navigate("library") },
                                onSettings = { nav.navigate("settings") },
                            )
                        }
                        composable(
                            "drill?tag={tag}",
                            arguments = listOf(navArgument("tag") { type = NavType.StringType; nullable = true; defaultValue = null }),
                        ) { entry ->
                            DrillScreen(
                                app = app,
                                tag = entry.arguments?.getString("tag"),
                                onClose = { nav.popBackStack() },
                            )
                        }
                        composable("library") {
                            LibraryScreen(
                                app = app,
                                onBack = { nav.popBackStack() },
                                onDrillTag = { tag -> nav.navigate("drill?tag=" + android.net.Uri.encode(tag)) },
                            )
                        }
                        composable("settings") {
                            SettingsScreen(app = app, onBack = { nav.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
