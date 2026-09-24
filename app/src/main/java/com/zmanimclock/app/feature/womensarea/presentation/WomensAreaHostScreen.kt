package com.zmanimclock.app.feature.womensarea.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Routes between: PIN not yet set up / locked / unlocked content. This is
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
        // Setting a PIN for the first time already proves she knows it —
        // unlock() here rather than making her immediately re-enter it on
        // the gate screen she'd otherwise fall straight into.
        !security.setupComplete -> {
            WomensAreaSetupScreen(onDone = { gateViewModel.unlock() })
        }
        !unlocked -> {
            WomensAreaGateScreen(onUnlocked = {})
        }
        else -> {
            WomensAreaScreen(onOpenHistory = onOpenHistory)
        }
    }
}
