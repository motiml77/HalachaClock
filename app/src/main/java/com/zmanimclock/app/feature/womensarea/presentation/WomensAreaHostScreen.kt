package com.zmanimclock.app.feature.womensarea.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Routes between: switched off / locked / unlocked content. This is
 * the real access control for the feature — the bottom-nav tab's visibility
 * is only cosmetic (see AppNavigation), every entry into this route goes
 * through here again.
 *
 * Re-locks on ON_STOP (app backgrounded) — ties the gate ViewModel's
 * in-memory unlocked flag to this host's own lifecycle rather than the
 * ViewModel's, since the ViewModel would otherwise survive a simple
 * background/foreground cycle unchanged. Does NOT re-lock on simple
 * tab-hopping within a session (switching to another bottom tab and back) —
 * that never triggers ON_STOP.
 */
@Composable
fun WomensAreaHostScreen(onOpenHistory: () -> Unit) {
    val gateViewModel: WomensAreaGateViewModel = hiltViewModel()
    val security by gateViewModel.security.collectAsStateWithLifecycle()
    val unlocked by gateViewModel.unlocked.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) gateViewModel.lock()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        // The route is registered regardless of the Settings switch; with the
        // switch off there is no tab leading here, but say so rather than
        // show the area anyway.
        !security.enabled -> {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("האיזור הנשי כבוי. אפשר להפעיל אותו בהגדרות.", textAlign = TextAlign.Center)
            }
        }
        !unlocked -> {
            WomensAreaGateScreen(onUnlocked = gateViewModel::unlock)
        }
        else -> {
            WomensAreaScreen(onOpenHistory = onOpenHistory)
        }
    }
}
