package com.zmanimclock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zmanimclock.app.feature.onboarding.OnboardingScreen
import com.zmanimclock.app.navigation.AppNavigation
import com.zmanimclock.app.ui.theme.ZmanimTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZmanimTheme {
                val isFirstLaunch by viewModel.isFirstLaunch.collectAsStateWithLifecycle()
                when (isFirstLaunch) {
                    null -> Unit // prefs still loading — splash stays up
                    true -> OnboardingScreen(onDone = viewModel::finishOnboarding)
                    false -> AppNavigation()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-post the persistent status line on every open — defense-in-depth
        // if an aggressive OEM removed the ongoing notification after a kill.
        com.zmanimclock.app.scheduling.StatusNotificationReceiver.ping(this)
    }
}
