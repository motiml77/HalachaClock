package com.zmanimclock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zmanimclock.app.navigation.AppNavigation
import com.zmanimclock.app.scheduling.ZmanimForegroundService
import com.zmanimclock.app.ui.theme.ZmanimTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start the persistent zmanim notification service (if enabled in settings)
        ZmanimForegroundService.startIfEnabled(this)

        setContent {
            ZmanimTheme {
                AppNavigation()
            }
        }
    }
}
