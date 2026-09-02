package com.zmanimclock.app.feature.subscription

/**
 * Whether this device may use the app, and how sure we are.
 *
 * THE THIRD STATE IS THE WHOLE POINT. A two-valued entitlement (subscribed /
 * not) forces a wrong answer the moment Play cannot be reached: either you lock
 * out a paying customer on a plane, or you hand the app to anyone who turns off
 * wifi. Google publishes no offline guarantee and no cache TTL for
 * `queryPurchasesAsync`, so "we could not ask" is a real, common, and
 * completely different situation from "we asked and the answer was no" — and
 * this app rings alarms people daven by, so the two must never collapse.
 *
 * Only [NOT_ENTITLED] is an answer from Play. [UNKNOWN] is the absence of one.
 */
enum class EntitlementState {
    /**
     * Play returned an active subscription. Includes the free trial, a
     * subscription the user has already cancelled but whose paid period has
     * not run out, and one in its grace period after a failed payment —
     * `queryPurchasesAsync` returns all three, and all three are paid-for time
     * the user is owed.
     */
    ENTITLED,

    /**
     * Play answered, and there is no subscription. Authoritative: expired,
     * refunded, on account hold, paused, or never bought. This is the only
     * state that may take features away.
     */
    NOT_ENTITLED,

    /**
     * Play could not be asked — no network, Play services unavailable or
     * disconnected, or the query timed out. Never authoritative. Resolved
     * against the last known good answer; see [Entitlement.allowsAccess].
     */
    UNKNOWN,
}

/**
 * A resolved entitlement: what Play said (or failed to say) plus how long ago
 * we last got a real answer.
 *
 * @param lastKnownEntitled the last authoritative answer we ever received,
 *   used to resolve [EntitlementState.UNKNOWN].
 * @param lastCheckMillis when that authoritative answer arrived, epoch millis,
 *   or 0 if Play has never once answered on this install.
 */
data class Entitlement(
    val state: EntitlementState,
    val lastKnownEntitled: Boolean = false,
    val lastCheckMillis: Long = 0L,
) {
    /**
     * Whether the app should let the user in, resolving [UNKNOWN] against the
     * cache.
     *
     * The rules, in the order they are applied:
     *
     * 1. A real answer wins outright. [ENTITLED] opens, [NOT_ENTITLED] closes,
     *    and no cached value can override either. This is what keeps a lapsed
     *    subscriber from coasting forever on a stale local flag — the exact
     *    failure a serverless app is prone to, since it receives no
     *    server-side notification when a subscription ends.
     *
     * 2. [UNKNOWN] with no answer ever received opens the app. A first launch
     *    in airplane mode must not look like a paywall; Play will be asked
     *    again within seconds of the device finding a network.
     *
     * 3. [UNKNOWN] after a previous YES keeps access for [OFFLINE_GRACE_DAYS].
     *    A paying customer abroad with no data keeps their alarms. The window
     *    is bounded so it cannot become a permanent bypass.
     *
     * 4. [UNKNOWN] after a previous NO stays closed. Someone already locked out
     *    cannot re-open the app by switching off the network.
     */
    val allowsAccess: Boolean
        get() = when (state) {
            EntitlementState.ENTITLED -> true
            EntitlementState.NOT_ENTITLED -> false
            EntitlementState.UNKNOWN -> when {
                // NEVER ASKED. Rule 2 above. `lastKnownEntitled` defaults to
                // false, so folding this case into the expression below would
                // paywall every first launch that happens to be offline — the
                // app accusing someone of not paying before it had ever
                // checked. It is called out as its own branch because the
                // short version reads as if it handles it, and does not.
                lastCheckMillis == 0L -> true
                else -> lastKnownEntitled && !offlineGraceExpired
            }
        }

    /** True once the offline grace window has run out on a stale YES. */
    val offlineGraceExpired: Boolean
        get() = lastCheckMillis > 0L &&
            System.currentTimeMillis() - lastCheckMillis > OFFLINE_GRACE_MILLIS

    /**
     * True while access rests on the cache rather than on an answer — the
     * condition the UI warns about before it ever locks anyone out.
     */
    val isRunningOnCache: Boolean
        get() = state == EntitlementState.UNKNOWN && allowsAccess

    companion object {
        /**
         * How long a paying user keeps access while Play is unreachable.
         *
         * Fourteen days covers a trip abroad with no data and a phone whose
         * Play services are wedged until the next system update. It is also
         * the longest a non-payer could hold the app by staying offline —
         * which at this price is worth far less than one missed שחרית.
         */
        const val OFFLINE_GRACE_DAYS = 14L
        const val OFFLINE_GRACE_MILLIS = OFFLINE_GRACE_DAYS * 24 * 60 * 60 * 1000L

        /** Before Play has ever been asked on this install. */
        val UNKNOWN_NO_HISTORY = Entitlement(EntitlementState.UNKNOWN)
    }
}
