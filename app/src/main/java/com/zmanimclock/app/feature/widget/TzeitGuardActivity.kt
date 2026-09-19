package com.zmanimclock.app.feature.widget

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.zmanimclock.app.BuildConfig
import com.zmanimclock.app.MainActivity
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.feature.subscription.AccessPolicy
import com.zmanimclock.app.feature.subscription.AppAccess
import com.zmanimclock.app.feature.subscription.EntitlementStore
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.feature.zmanim.presentation.TzeitGuardController
import com.zmanimclock.app.feature.zmanim.presentation.TzeitGuardDialog
import com.zmanimclock.app.feature.zmanim.presentation.ZmanimViewModel
import com.zmanimclock.app.feature.zmanim.presentation.guardArmedAt
import com.zmanimclock.app.ui.theme.ZmanimTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The widget's שומר לערבית button opens THIS: the very dialog the zmanim
 * screen's badge opens, floating over the launcher.
 *
 * A dialog, not a one-tap arm, on purpose. A widget sits on a home screen that
 * gets brushed by thumbs and pockets, and the two mistakes are not equal: an
 * accidental arm is one extra ten-second alert, an accidental CANCEL silently
 * strips someone of the safeguard they set. The dialog is also where the
 * choices live — the default two minutes past צאת הכוכבים, a time of one's own,
 * and cancelling an armed one — so the button needs no rules of its own.
 *
 * Everything it does goes through [TzeitGuardController], the same object the
 * zmanim screen uses, so the two can never arm different alarms.
 */
@AndroidEntryPoint
class TzeitGuardActivity : ComponentActivity() {

    @Inject lateinit var guardController: TzeitGuardController
    @Inject lateinit var entitlementStore: EntitlementStore

    private val viewModel: ZmanimViewModel by viewModels()

    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The button is drawn only while the app is unlocked, but a widget can
        // be tapped a moment after the subscription lapses. An alarm armed now
        // would never ring (AccessPolicy.alarmsAllowed), so send the user to
        // the front door instead of letting them believe they are covered.
        val unlocked = AccessPolicy.appAccess(
            BuildConfig.PAYWALL_ENABLED,
            entitlementStore.cached(),
            offers = null,
            firstCheckDone = true,
        ) == AppAccess.Allowed
        if (!unlocked) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent {
            ZmanimTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                // Read once, here, rather than from the ViewModel's flow: that
                // one starts empty and would offer "הפעל" for the instant
                // before it learned a guard is already armed.
                var guardLoaded by remember { mutableStateOf(false) }
                var guard by remember { mutableStateOf<AlarmEntity?>(null) }
                LaunchedEffect(Unit) {
                    guard = guardController.current()
                    guardLoaded = true
                }

                val tzeit = state.rows.firstOrNull { it.kind == ZmanKind.TZEIT_LECHUMRA }?.time

                when {
                    state.loading || !guardLoaded -> Box(
                        // Tap anywhere to leave: a transparent activity with
                        // nothing to tap would otherwise trap the touch until
                        // the day's zmanim finish loading.
                        modifier = Modifier.fillMaxSize().clickable { finish() },
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    tzeit == null -> LaunchedEffect(Unit) {
                        Toast.makeText(
                            applicationContext,
                            "לא ניתן לחשב את צאת הכוכבים כרגע",
                            Toast.LENGTH_SHORT,
                        ).show()
                        finish()
                    }

                    else -> TzeitGuardDialog(
                        zmanTime = tzeit,
                        armedAt = guardArmedAt(guard),
                        onArm = { hour, minute ->
                            finishAfter("שומר לערבית הופעל ל-%02d:%02d".format(hour, minute)) {
                                guardController.arm(hour, minute)
                            }
                        },
                        onCancelGuard = {
                            finishAfter("שומר לערבית בוטל") { guardController.cancel() }
                        },
                        onDismiss = { finish() },
                    )
                }
            }
        }
    }

    /**
     * Runs [work] to completion BEFORE closing, then redraws the widgets so the
     * button shows its new state. Closing first would let the activity's scope
     * die with the write half done.
     */
    private fun finishAfter(message: String, work: suspend () -> Unit) {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            val done = try {
                work()
                true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            ZmanWidgetProvider.refresh(applicationContext)
            Toast.makeText(
                applicationContext,
                if (done) message else "לא הצלחנו לעדכן את שומר לערבית — נסה שוב",
                Toast.LENGTH_SHORT,
            ).show()
            finish()
        }
    }
}
