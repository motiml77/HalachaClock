package com.zmanimclock.app.feature.subscription.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCardOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zmanimclock.app.R
import com.zmanimclock.app.feature.subscription.SubscriptionOffers
import com.zmanimclock.app.feature.subscription.hebrewPeriod
import com.zmanimclock.app.ui.theme.Ext

/**
 * The front door once the paywall is on: subscribe, or check again.
 *
 * WHAT PLAY'S SUBSCRIPTIONS POLICY REQUIRES ON THIS SCREEN, and where each is:
 *  - the price and billing period            → the price card
 *  - the free-trial length, and that it turns into a paid subscription
 *                                            → the "no charge today" banner + the disclosure
 *  - that it renews automatically until cancelled → the disclosure
 *  - how to cancel                           → the disclosure + "ניהול מנוי"
 * Every one of those is read from Play's own offer, never from a constant, so
 * the screen cannot promise what checkout then refuses to sell.
 *
 * THE "NO CHARGE TODAY" BANNER exists as its own loud, high-contrast element —
 * not just a line inside the small-print disclosure — because the one thing a
 * subscriber must never wonder, at the exact moment they are asked to hand over
 * a card, is "am I paying right now?". The disclosure below it still carries
 * the complete legal terms Play requires; the banner is the same fact, said
 * once in large type before it is said again in full.
 *
 * NOTHING HERE DECIDES ACCESS. It is shown when AccessPolicy says Locked and
 * disappears when the purchase listener records a subscription; the buttons
 * only ask Play things.
 */
@Composable
fun PaywallScreen(
    offers: SubscriptionOffers?,
    isRefreshing: Boolean,
    onSubscribe: () -> Unit,
    onCheckAgain: () -> Unit,
    onManageSubscription: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ext = Ext.colors
    val trial = hebrewPeriod(offers?.trialPeriod)
    val period = hebrewPeriod(offers?.billingPeriod) ?: "חודש"
    val perPeriod = if (period.first().isDigit()) "ל-$period" else "ל$period"
    val price = offers?.formattedPrice

    Surface(color = cs.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ---- Hero: the same navy gradient the home screen opens with,
            // so the very first thing a locked-out subscriber sees still
            // looks like THIS app, not a generic checkout wall.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(ext.heroTop, ext.heroBottom)))
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(ext.heroInner, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Alarm,
                        contentDescription = null,
                        tint = ext.accentGold,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = cs.onPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "כל השעונים וההתראות לפי זמני ההלכה — במקום אחד",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ext.heroLabel,
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 22.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    color = cs.surfaceContainerHigh,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        listOf(
                            "שעונים מעוררים לפי זמן הלכתי, שמתעדכנים כל יום",
                            "התראת כניסת שבת ושומר לערבית בלחיצה אחת",
                            "הנץ הנראה מטבלאות חי לתפילת ותיקין",
                            "ווידג'טים ושורת הזמן הבא במסך הנעילה",
                        ).forEach { line ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = cs.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(line, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // ---- The loud, unmissable answer to "am I paying right now?" ----
                if (trial != null) {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        color = ext.windowOpen,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(cs.onPrimary.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.CreditCardOff,
                                    contentDescription = null,
                                    tint = cs.onPrimary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    "לא תחויב עכשיו",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.onPrimary,
                                )
                                Text(
                                    "התשלום הראשון רק בתום $trial — ורק אם לא תבטל לפני כן",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onPrimary,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Surface(
                    color = cs.primaryContainer,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(vertical = 18.dp, horizontal = 18.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when {
                            offers == null && isRefreshing -> {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                                Spacer(Modifier.height(8.dp))
                                Text("טוען את פרטי המנוי…", color = cs.onPrimaryContainer)
                            }
                            offers == null -> {
                                // NOT "no internet". Measured on an emulator with a
                                // working connection: Play answered BILLING_UNAVAILABLE
                                // ("In-app billing API version 3 is not supported on
                                // this device"). The real causes are an outdated Play
                                // Store, no Google account in it, or an app not
                                // installed from Play — so the screen names all three
                                // instead of sending someone to fix a Wi-Fi that works.
                                Text(
                                    "לא הצלחנו להתחבר לחנות Google Play",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = cs.onPrimaryContainer,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "ודא שיש חיבור לאינטרנט, שחנות Play מעודכנת ושאתה מחובר בה לחשבון Google, ונסה שוב",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onPrimaryContainer,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            trial != null -> {
                                Surface(
                                    color = ext.accentGold,
                                    shape = RoundedCornerShape(50),
                                ) {
                                    Text(
                                        "$trial ראשון חינם",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = ext.onAccentGold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                price?.let {
                                    Text(
                                        "ואחר כך $it $perPeriod",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = cs.onPrimaryContainer,
                                    )
                                }
                            }
                            else -> Text(
                                price?.let { "$it $perPeriod" } ?: "מנוי חודשי",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = cs.onPrimaryContainer,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = if (offers == null) onCheckAgain else onSubscribe,
                    enabled = !(offers == null && isRefreshing),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(
                        when {
                            offers == null -> "נסה שוב"
                            trial != null -> "התחל $trial חינם"
                            else -> "הירשם למנוי"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // The disclosure. Plain words, every time, not behind a link.
                if (offers != null && price != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        buildString {
                            if (trial != null) {
                                append("בתום $trial הניסיון תחויב אוטומטית $price $perPeriod. ")
                                append("לא תחויב אם תבטל לפני סוף תקופת הניסיון. ")
                            }
                            append("המנוי מתחדש אוטומטית עד שתבטל. ")
                            append("אפשר לבטל בכל עת ב-Google Play ← תשלומים ומינויים ← מינויים.")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(14.dp))
                TextButton(onClick = onCheckAgain, enabled = !isRefreshing) {
                    Text("כבר נרשמתי — בדוק שוב")
                }
                TextButton(onClick = onManageSubscription) {
                    Text("ניהול מנוי ב-Google Play")
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
