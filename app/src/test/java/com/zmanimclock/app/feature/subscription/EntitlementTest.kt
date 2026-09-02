package com.zmanimclock.app.feature.subscription

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The rule that decides whether a person's alarms ring.
 *
 * This is the highest-consequence pure logic in the app. Getting it wrong in
 * one direction gives the app away; getting it wrong in the OTHER direction
 * silently stops a paying user's שחרית alarm because their train went into a
 * tunnel. The asymmetry is deliberate and every case below pins one half of it.
 */
class EntitlementTest {

    private fun daysAgo(n: Long) =
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(n)

    // ---- an answer from Play always wins ---------------------------------

    @Test
    fun `an active subscription opens the app`() {
        val e = Entitlement(EntitlementState.ENTITLED, lastKnownEntitled = true, lastCheckMillis = daysAgo(0))
        assertTrue(e.allowsAccess)
        assertFalse("a real answer is not 'running on cache'", e.isRunningOnCache)
    }

    /**
     * The failure a serverless app is prone to: with no server-side
     * notification, a stale local YES could otherwise outlive the
     * subscription forever. An authoritative NO must beat the cache outright.
     */
    @Test
    fun `an authoritative no beats a cached yes`() {
        val e = Entitlement(
            state = EntitlementState.NOT_ENTITLED,
            lastKnownEntitled = true,
            lastCheckMillis = daysAgo(0),
        )
        assertFalse("a lapsed subscriber must not coast on the cache", e.allowsAccess)
    }

    // ---- "could not ask" is not "no" --------------------------------------

    /**
     * First launch with no network. Play has never answered, so there is
     * nothing to hold against the user — a paywall here would be the app
     * accusing someone of not paying before it ever checked.
     */
    @Test
    fun `unknown with no history ever opens the app`() {
        assertTrue(Entitlement.UNKNOWN_NO_HISTORY.allowsAccess)
    }

    @Test
    fun `a paying user offline keeps access inside the grace window`() {
        val e = Entitlement(
            state = EntitlementState.UNKNOWN,
            lastKnownEntitled = true,
            lastCheckMillis = daysAgo(Entitlement.OFFLINE_GRACE_DAYS - 1),
        )
        assertTrue("13 days offline must not switch the alarms off", e.allowsAccess)
        assertTrue("but the UI must be able to warn", e.isRunningOnCache)
    }

    @Test
    fun `the offline window is bounded so it cannot become a bypass`() {
        val e = Entitlement(
            state = EntitlementState.UNKNOWN,
            lastKnownEntitled = true,
            lastCheckMillis = daysAgo(Entitlement.OFFLINE_GRACE_DAYS + 1),
        )
        assertFalse(e.allowsAccess)
        assertTrue(e.offlineGraceExpired)
    }

    /**
     * The other half of the asymmetry: someone Play has already refused must
     * not be able to re-open the app by turning off the network.
     */
    @Test
    fun `unknown after a no stays closed`() {
        val e = Entitlement(
            state = EntitlementState.UNKNOWN,
            lastKnownEntitled = false,
            lastCheckMillis = daysAgo(1),
        )
        assertFalse("airplane mode is not a way back in", e.allowsAccess)
    }

    // ---- the warning state -----------------------------------------------

    /**
     * isRunningOnCache is what the UI uses to warn BEFORE anyone is locked
     * out, so it must be true only when access genuinely rests on the cache.
     */
    @Test
    fun `running-on-cache is true only while unknown and still allowed`() {
        val allowed = Entitlement(EntitlementState.UNKNOWN, lastKnownEntitled = true, lastCheckMillis = daysAgo(2))
        val denied = Entitlement(EntitlementState.UNKNOWN, lastKnownEntitled = false, lastCheckMillis = daysAgo(2))
        val answered = Entitlement(EntitlementState.ENTITLED, lastKnownEntitled = true, lastCheckMillis = daysAgo(0))

        assertTrue(allowed.isRunningOnCache)
        assertFalse("already denied, so nothing to warn about", denied.isRunningOnCache)
        assertFalse("a real answer needs no warning", answered.isRunningOnCache)
    }

    /**
     * A never-answered entitlement has no timestamp, and must not be treated
     * as an infinitely stale one — that would compute an expiry from epoch 0
     * and lock out every first launch.
     */
    @Test
    fun `no history is never treated as expired`() {
        assertFalse(Entitlement.UNKNOWN_NO_HISTORY.offlineGraceExpired)
        assertTrue(Entitlement.UNKNOWN_NO_HISTORY.allowsAccess)
    }

    @Test
    fun `the grace window is a sane length`() {
        assertTrue("long enough for a trip abroad", Entitlement.OFFLINE_GRACE_DAYS >= 7)
        assertTrue("short enough not to be a free ride", Entitlement.OFFLINE_GRACE_DAYS <= 30)
    }
}
