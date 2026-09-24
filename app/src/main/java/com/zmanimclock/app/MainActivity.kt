package com.zmanimclock.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zmanimclock.app.feature.onboarding.OnboardingScreen
import com.zmanimclock.app.feature.subscription.AppAccess
import com.zmanimclock.app.feature.subscription.BillingRepository
import com.zmanimclock.app.feature.subscription.presentation.PaywallScreen
import com.zmanimclock.app.navigation.AppNavigation
import com.zmanimclock.app.ui.theme.ZmanimTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * FragmentActivity, not ComponentActivity (which a FragmentActivity also is —
 * every API used below is inherited unchanged): BiometricPrompt, used by the
 * Women's Area gate, attaches a headless Fragment to the host's
 * FragmentManager to survive configuration changes, which a plain
 * ComponentActivity does not have.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZmanimTheme {
                val access by viewModel.access.collectAsStateWithLifecycle()
                val isFirstLaunch by viewModel.isFirstLaunch.collectAsStateWithLifecycle()
                // THE GATE COMES FIRST, above onboarding. Under a Play-managed
                // trial nobody uses any part of the app without subscribing, so
                // walking a new user through five permission prompts and THEN
                // demanding a subscription would be both backwards and wasted
                // effort for anyone who declines.
                when (val gate = access) {
                    AppAccess.Checking -> Unit // asking Play — splash stays up
                    is AppAccess.Locked -> {
                        val offers by viewModel.offers.collectAsStateWithLifecycle()
                        val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
                        PaywallScreen(
                            offers = offers ?: gate.offers,
                            isRefreshing = refreshing,
                            onSubscribe = {
                                if (!viewModel.subscribe(this@MainActivity)) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "לא הצלחנו לפתוח את Google Play. נסה שוב.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    viewModel.refreshEntitlement()
                                }
                            },
                            onCheckAgain = viewModel::refreshEntitlement,
                            onManageSubscription = {
                                runCatching {
                                    startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(BillingRepository.manageSubscriptionUrl(packageName)),
                                        )
                                    )
                                }
                            },
                        )
                    }
                    AppAccess.Allowed -> when (isFirstLaunch) {
                        null -> Unit // prefs still loading — splash stays up
                        true -> OnboardingScreen(onDone = viewModel::finishOnboarding)
                        false -> AppNavigation()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-post the persistent status line on every open — defense-in-depth
        // if an aggressive OEM removed the ongoing notification after a kill.
        com.zmanimclock.app.scheduling.StatusNotificationReceiver.ping(this)
        // Ask Play on every resume, not only at process start. Returning from
        // Play's checkout sheet, or from cancelling on Play's subscriptions
        // page, IS a resume — and a process can live for days, so a
        // start-only check would let a cancelled subscription run on until the
        // next cold start.
        viewModel.refreshEntitlement()
    }
}
