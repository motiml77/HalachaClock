package com.zmanimclock.app.feature.subscription

import org.junit.Test

/**
 * The guard on the defect that made the entire billing layer unconstructible.
 *
 * `PendingPurchasesParams.Builder.build()` begins, in the 9.1.0 bytecode, with
 *
 *     getfield  enableOneTimeProducts:Z
 *     ifne      17
 *     new       IllegalArgumentException
 *     ldc       "Pending purchases for one-time products must be supported."
 *     athrow
 *
 * so omitting `enableOneTimeProducts()` throws unconditionally — and it threw
 * from a property initialiser, meaning the BillingRepository CONSTRUCTOR could
 * never complete on any device. It stayed invisible only because Hilt bindings
 * are lazy and nothing injected the class yet; it would have surfaced on the
 * commit that wired up the paywall, looking like the paywall's fault.
 *
 * This calls the real [pendingPurchasesParams] rather than rebuilding the
 * params here, so deleting the flag from production code fails this test.
 * It runs on the plain JVM because PendingPurchasesParams touches nothing in
 * android.*, which is also why it is the only piece of the client that can be
 * covered without Robolectric.
 */
class BillingParamsTest {

    @Test
    fun `the pending-purchases params this app builds are accepted`() {
        // Throwing IS the failure. No assertion needed beyond completing.
        pendingPurchasesParams()
    }

    /** Documents the trap, so nobody "simplifies" the flag away again. */
    @Test(expected = IllegalArgumentException::class)
    fun `omitting one-time products is rejected by the library`() {
        com.android.billingclient.api.PendingPurchasesParams.newBuilder().build()
    }
}
