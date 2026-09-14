package com.zmanimclock.app.feature.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Who may open the app, and whose alarms may ring, once the free month is over.
 *
 * The owner's requirement, verbatim: "אני רוצה שאחרי 30 יום זה ידרוש תשלום
 * בפועל! אל תתן לזה לחמוק". Before this existed the billing client asked Play
 * and stored the answer, and nothing read it — every install got everything
 * forever. These cases pin both halves: nobody slips through, and nobody who
 * pays loses an alarm.
 */
class AccessPolicyTest {

    private fun daysAgo(n: Long) = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(n)

    private val subscribed = Entitlement(EntitlementState.ENTITLED, true, daysAgo(0))
    private val refused = Entitlement(EntitlementState.NOT_ENTITLED, false, daysAgo(0))

    // ---- the closed-testing build ------------------------------------------

    @Test
    fun `with the paywall off, nothing is ever locked`() {
        for (e in listOf(subscribed, refused, Entitlement.UNKNOWN_NO_HISTORY)) {
            assertEquals(AppAccess.Allowed, AccessPolicy.appAccess(false, e, null, firstCheckDone = true))
            assertTrue(AccessPolicy.alarmsAllowed(false, e))
        }
    }

    // ---- the app ------------------------------------------------------------

    @Test
    fun `a subscriber gets in`() {
        assertEquals(AppAccess.Allowed, AccessPolicy.appAccess(true, subscribed, null, true))
    }

    @Test
    fun `Play saying no locks the app`() {
        assertTrue(AccessPolicy.appAccess(true, refused, null, true) is AppAccess.Locked)
    }

    /**
     * The trial is over and the user cancelled, or the card was declined past
     * grace and account hold: Play stops returning the purchase. The cache
     * still says YES from yesterday — Play's NO must win.
     */
    @Test
    fun `a lapsed subscriber cannot coast on yesterday's yes`() {
        val lapsed = Entitlement(EntitlementState.NOT_ENTITLED, lastKnownEntitled = true, lastCheckMillis = daysAgo(1))
        assertTrue(AccessPolicy.appAccess(true, lapsed, null, true) is AppAccess.Locked)
    }

    @Test
    fun `before the first answer, show nothing rather than guess`() {
        assertEquals(
            AppAccess.Checking,
            AccessPolicy.appAccess(true, Entitlement.UNKNOWN_NO_HISTORY, null, firstCheckDone = false),
        )
    }

    /**
     * The leak this closes: a device where Play Billing never answers. Under
     * Entitlement.allowsAccess alone, "never answered" opens the app, and it
     * would stay open forever.
     */
    @Test
    fun `never answered and done asking is locked, not free`() {
        assertTrue(
            AccessPolicy.appAccess(true, Entitlement.UNKNOWN_NO_HISTORY, null, firstCheckDone = true)
                is AppAccess.Locked,
        )
    }

    @Test
    fun `a subscriber offline inside the grace window keeps the app`() {
        val offline = Entitlement(EntitlementState.UNKNOWN, true, daysAgo(Entitlement.OFFLINE_GRACE_DAYS - 1))
        assertEquals(AppAccess.Allowed, AccessPolicy.appAccess(true, offline, null, true))
    }

    @Test
    fun `airplane mode is not a way past a no`() {
        val offlineAfterNo = Entitlement(EntitlementState.UNKNOWN, false, daysAgo(1))
        assertTrue(AccessPolicy.appAccess(true, offlineAfterNo, null, true) is AppAccess.Locked)
    }

    @Test
    fun `staying offline forever is not a way past the grace window`() {
        val stale = Entitlement(EntitlementState.UNKNOWN, true, daysAgo(Entitlement.OFFLINE_GRACE_DAYS + 1))
        assertTrue(AccessPolicy.appAccess(true, stale, null, true) is AppAccess.Locked)
    }

    // ---- the alarms ---------------------------------------------------------

    @Test
    fun `alarms stop on Play's no`() {
        assertFalse(AccessPolicy.alarmsAllowed(true, Entitlement(EntitlementState.UNKNOWN, false, daysAgo(0))))
    }

    @Test
    fun `a subscriber's alarms ring`() {
        assertTrue(AccessPolicy.alarmsAllowed(true, Entitlement(EntitlementState.UNKNOWN, true, daysAgo(0))))
    }

    /**
     * The deliberate asymmetry. The app screen locks after the grace window;
     * the alarm does not, because a subscriber whose phone was offline for two
     * weeks must still be woken — only an authoritative NO silences an alarm.
     */
    @Test
    fun `a stale yes still rings well past the app's grace window`() {
        // 30 days offline: the screen is locked (14-day grace), the alarm is not.
        val stale = Entitlement(EntitlementState.UNKNOWN, true, daysAgo(30))
        assertTrue(AccessPolicy.alarmsAllowed(true, stale))
        assertTrue(AccessPolicy.appAccess(true, stale, null, true) is AppAccess.Locked)
    }

    /**
     * The bypass an adversarial review found: take the trial, cancel it,
     * disable the Play Store. Play is then never reachable, the NO is never
     * recorded, and an unbounded stale YES kept every alarm ringing for good.
     */
    @Test
    fun `a stale yes does not ring forever`() {
        val tooOld = Entitlement(EntitlementState.UNKNOWN, true, daysAgo(AccessPolicy.ALARM_STALE_YES_MAX_DAYS + 1))
        assertFalse(AccessPolicy.alarmsAllowed(true, tooOld))
        assertTrue(
            "the notification must say 'could not verify', not 'your subscription ended'",
            AccessPolicy.blockedOnlyForLackOfVerification(true, tooOld),
        )
    }

    @Test
    fun `the alarm ceiling sits exactly at its boundary`() {
        val now = System.currentTimeMillis()
        val max = AccessPolicy.ALARM_STALE_YES_MAX_MILLIS
        assertTrue(AccessPolicy.alarmsAllowed(true, Entitlement(EntitlementState.UNKNOWN, true, now - max), now))
        assertFalse(AccessPolicy.alarmsAllowed(true, Entitlement(EntitlementState.UNKNOWN, true, now - max - 1), now))
    }

    @Test
    fun `a real no is never reported as merely unverified`() {
        assertFalse(AccessPolicy.blockedOnlyForLackOfVerification(true, Entitlement(EntitlementState.UNKNOWN, false, daysAgo(0))))
        assertFalse(
            "a recent yes is not blocked at all",
            AccessPolicy.blockedOnlyForLackOfVerification(true, Entitlement(EntitlementState.UNKNOWN, true, daysAgo(0))),
        )
    }

    @Test
    fun `the alarm ceiling outlasts the app's grace window`() {
        assertTrue(AccessPolicy.ALARM_STALE_YES_MAX_DAYS > Entitlement.OFFLINE_GRACE_DAYS)
    }

    @Test
    fun `never asked does not silence alarms`() {
        assertTrue(AccessPolicy.alarmsAllowed(true, Entitlement.UNKNOWN_NO_HISTORY))
    }

    // ---- the trial length shown on the paywall -----------------------------

    @Test
    fun `play billing periods read as natural Hebrew`() {
        assertEquals("חודש", hebrewPeriod("P1M"))
        assertEquals("30 יום", hebrewPeriod("P30D"))
        assertEquals("14 יום", hebrewPeriod("P14D"))
        assertEquals("7 ימים", hebrewPeriod("P7D"))
        assertEquals("שבוע", hebrewPeriod("P1W"))
        assertEquals("שבועיים", hebrewPeriod("P2W"))
        assertEquals("שנה", hebrewPeriod("P1Y"))
    }

    @Test
    fun `an unrecognised period is null, never printed raw`() {
        assertNull(hebrewPeriod(null))
        assertNull(hebrewPeriod(""))
        assertNull(hebrewPeriod("P1M2D"))
        assertNull(hebrewPeriod("P0D"))
        assertNull(hebrewPeriod("banana"))
    }
}
