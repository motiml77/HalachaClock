package com.zmanimclock.app.feature.subscription

/**
 * What the app shows at its front door.
 *
 * Three states, not two, for the same reason [EntitlementState] has three:
 * before Play has ever answered on this install, "locked" would flash a
 * paywall at a subscriber for the second it takes to ask, and "allowed" would
 * flash the whole app at a non-subscriber before slamming it shut.
 */
sealed interface AppAccess {
    /** Asking Play for the first time on this install. Show nothing yet. */
    data object Checking : AppAccess

    data object Allowed : AppAccess

    /** Subscribe to continue. [offers] is null until Play has said what is on sale. */
    data class Locked(val offers: SubscriptionOffers?) : AppAccess
}

/**
 * THE rules for who may use the app and whose alarms may ring.
 *
 * Pure and parameterised — the build flag included — so every branch is pinned
 * by AccessPolicyTest without a device, a Play Store or a build variant. Every
 * gate in the app calls one of these two functions and nothing else decides.
 *
 * THE MODEL, as the owner chose it: the free month is Google Play's own trial
 * offer, taken at checkout with a payment method on file, and Play charges
 * automatically when it ends. Nobody uses the app without having gone through
 * that checkout. Eligibility for the free month is Play's decision, remembered
 * per Google account across reinstalls and devices — which is exactly what a
 * local "first launch + 30 days" counter could never do, since uninstalling
 * wipes it.
 *
 * TWO DIFFERENT QUESTIONS, TWO DIFFERENT RULES. Opening the app and ringing an
 * alarm are held to different standards on purpose, because the costs of a
 * wrong answer are not symmetric:
 *  - The APP locks on anything short of a confirmed or recently-confirmed
 *    subscription. A wrong lock there costs a subscriber one "check again"
 *    tap once they have signal.
 *  - The ALARMS stop only on an authoritative NO from Play. A subscriber whose
 *    phone has been offline past the grace window must still be woken for
 *    שחרית; locking the screen is recoverable, a missed alarm is not.
 */
object AccessPolicy {

    /**
     * Whether the app UI may open.
     *
     * @param paywallEnabled BuildConfig.PAYWALL_ENABLED — false in closed-testing
     *   builds, so testers are never asked for a card during their 14 days.
     * @param firstCheckDone whether the first Play query of this process has
     *   finished or timed out. Only consulted while nothing is known.
     */
    fun appAccess(
        paywallEnabled: Boolean,
        entitlement: Entitlement,
        offers: SubscriptionOffers?,
        firstCheckDone: Boolean,
    ): AppAccess {
        if (!paywallEnabled) return AppAccess.Allowed
        return when (entitlement.state) {
            EntitlementState.ENTITLED -> AppAccess.Allowed
            EntitlementState.NOT_ENTITLED -> AppAccess.Locked(offers)
            EntitlementState.UNKNOWN -> when {
                // NEVER ANSWERED ON THIS INSTALL, AND DONE ASKING. This is
                // deliberately stricter than Entitlement.allowsAccess, which
                // opens the app here. That rule predates a paywall on day one:
                // under a Play-managed trial nobody has anything to lose by
                // waiting for Play, because nobody can use the app without
                // Play's checkout anyway — and opening it would hand the whole
                // app, forever, to any device on which Play Billing never
                // answers.
                entitlement.lastCheckMillis == 0L ->
                    if (firstCheckDone) AppAccess.Locked(offers) else AppAccess.Checking
                // A past answer exists: a recent YES keeps a subscriber in
                // while offline, a NO or an expired YES does not.
                entitlement.allowsAccess -> AppAccess.Allowed
                else -> AppAccess.Locked(offers)
            }
        }
    }

    /**
     * Whether alarms may be armed and may ring.
     *
     * Read from the device-protected cache, because the callers — the boot
     * receiver before first unlock, and the trigger receiver at the moment an
     * alarm fires — can reach neither Play nor credential-protected storage.
     *
     * Blocks on Play's last real answer having been NO, or on a YES so old it
     * can no longer be believed. Never-asked allows (the UI is locked in that
     * case, so no alarm can have been created).
     *
     * A STALE YES RINGS — BUT NOT FOREVER. It outlasts the app's 14-day grace
     * on purpose (see the class comment: a subscriber offline for two weeks
     * must still be woken), up to [ALARM_STALE_YES_MAX_DAYS]. Unbounded, which
     * this was until an adversarial review, it was a permanent bypass: start
     * the trial, cancel it, disable the Play Store app — every query then fails
     * to connect, nothing ever records the NO, and the alarms (the core of the
     * product, recomputed daily) run free for good. 45 days is a month-long
     * trip abroad with room to spare, and three times the UI's grace window.
     */
    fun alarmsAllowed(
        paywallEnabled: Boolean,
        cached: Entitlement,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!paywallEnabled) return true
        if (cached.lastCheckMillis == 0L) return true
        if (!cached.lastKnownEntitled) return false
        return nowMillis - cached.lastCheckMillis <= ALARM_STALE_YES_MAX_MILLIS
    }

    /**
     * True when alarms are blocked NOT because Play said no, but because a
     * yes has gone unconfirmed past [ALARM_STALE_YES_MAX_DAYS]. The
     * notification must say "we could not verify", not "your subscription
     * ended" — the second would be a false accusation to a paying subscriber
     * who has simply had no signal.
     */
    fun blockedOnlyForLackOfVerification(
        paywallEnabled: Boolean,
        cached: Entitlement,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = !alarmsAllowed(paywallEnabled, cached, nowMillis) && cached.lastKnownEntitled

    const val ALARM_STALE_YES_MAX_DAYS = 45L
    const val ALARM_STALE_YES_MAX_MILLIS = ALARM_STALE_YES_MAX_DAYS * 24 * 60 * 60 * 1000L
}

/**
 * An ISO-8601 billing period from Play ("P1M", "P30D", "P1W") as the Hebrew a
 * paywall shows: "חודש", "30 יום", "שבוע". Null for anything unrecognised, so
 * the screen can fall back to a generic line rather than print "P1M".
 *
 * The trial length is shown from PLAY'S offer, never from a constant. If the
 * console offer is ever changed to a week, the screen changes with it — a
 * paywall promising a month that checkout then sells as a week is exactly the
 * kind of mismatch Play's subscriptions policy rejects apps for.
 *
 * Hebrew counting: 2–10 take the plural ("7 ימים"), above ten the singular
 * ("30 יום", "14 יום"), which is how a Hebrew speaker actually says it.
 */
fun hebrewPeriod(iso: String?): String? {
    val match = Regex("^P(\\d+)([DWMY])$").matchEntire(iso?.trim().orEmpty()) ?: return null
    val n = match.groupValues[1].toIntOrNull() ?: return null
    if (n <= 0) return null
    return when (match.groupValues[2]) {
        "D" -> when {
            n == 1 -> "יום"
            n == 2 -> "יומיים"
            n <= 10 -> "$n ימים"
            else -> "$n יום"
        }
        "W" -> when {
            n == 1 -> "שבוע"
            n == 2 -> "שבועיים"
            else -> "$n שבועות"
        }
        "M" -> when {
            n == 1 -> "חודש"
            n == 2 -> "חודשיים"
            else -> "$n חודשים"
        }
        "Y" -> if (n == 1) "שנה" else if (n == 2) "שנתיים" else "$n שנים"
        else -> null
    }
}
