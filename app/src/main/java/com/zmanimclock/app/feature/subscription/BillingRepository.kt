package com.zmanimclock.app.feature.subscription

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * The app's whole relationship with Google Play billing.
 *
 * NO BACKEND, ON PURPOSE. A server would be the textbook answer, but this app
 * is published by one person, stores nothing about anybody, and sells a single
 * product at a couple of shekels a month. A backend would cost more than it
 * could protect and would newly make the author a controller of personal data.
 * So entitlement is decided from what Play tells this device, and the design
 * below is shaped entirely around the two things that costs us.
 *
 * FIRST: THERE IS NO EXPIRY DATE IN A CLIENT PURCHASE. [Purchase] carries no
 * expiry field at all, so "is it still valid" cannot be computed locally —
 * PRESENCE IN THE QUERY IS THE ONLY TRUTH. Play returns a subscription while
 * it is active, in its free trial, cancelled-but-not-yet-expired, or in its
 * grace period, and stops returning it once it is on hold, paused, expired or
 * refunded. So the rule really is that simple, and any local flag is a cache,
 * never an authority — see [EntitlementStore].
 *
 * SECOND: WE MUST DISTINGUISH "NO" FROM "COULD NOT ASK". Google publishes no
 * offline guarantee for queryPurchasesAsync and no cache TTL, so a failed
 * query is not a negative answer. Treating it as one would switch off the
 * alarms of a paying user who happens to be on a plane. Hence the response
 * code is branched on before anything is revoked; see [readEntitlement].
 */
@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: EntitlementStore,
) {

    private val connectMutex = Mutex()

    private val _entitlement = MutableStateFlow(store.cached())

    /** The current entitlement, seeded from cache so the UI never flashes. */
    val entitlement: StateFlow<Entitlement> = _entitlement.asStateFlow()

    private val _offers = MutableStateFlow<SubscriptionOffers?>(null)

    /** What is purchasable, once Play has been asked. Null until then. */
    val offers: StateFlow<SubscriptionOffers?> = _offers.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(context)
        // Purchases can also arrive OUTSIDE a checkout this app started — a
        // pending payment clearing later, or the same account subscribing on
        // another device — so they are delivered here rather than as a return
        // value from launchPurchaseFlow.
        .setListener { result, purchases ->
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK ->
                    // NOT authoritative. A listener callback carries only the
                    // purchases THAT UPDATE touched, never the account's full
                    // set, so an empty list here does not mean "owns nothing".
                    // Passing it as authoritative would let a pending payment
                    // clearing, or any future second product, write NOT_ENTITLED
                    // into device-protected storage — which BootReceiver reads
                    // in Direct Boot, and which no later cache may override.
                    // A paying subscriber's alarms would stop re-arming after
                    // the next reboot, silently. Only readEntitlement()'s full
                    // query is allowed to say NO.
                    applyPurchases(purchases, authoritative = false)

                // The user backed out of the sheet. Says nothing about
                // entitlement, so deliberately not treated as a NO.
                BillingClient.BillingResponseCode.USER_CANCELED -> Unit

                // Everything else is a real checkout failure — a stale offer
                // token, a declined card, a dropped service. This listener is
                // the ONLY channel that carries the outcome, and there is no
                // server to inspect instead, so it gets logged with the debug
                // message or the first live failure is uninvestigable.
                else -> Log.w(
                    TAG,
                    "purchasesUpdated: " + result.responseCode + " " + result.debugMessage,
                )
            }
        }
        // enableOneTimeProducts() is NOT optional, despite this app selling no
        // one-time product. PendingPurchasesParams.Builder.build() opens with
        // `if (!enableOneTimeProducts) throw IllegalArgumentException(...)` —
        // confirmed in the 9.1.0 bytecode — and BillingClient.Builder.build()
        // independently rejects null params once a PurchasesUpdatedListener is
        // set, so the call cannot simply be dropped either. Without this the
        // constructor throws on every device and nothing here ever runs.
        // enablePrepaidPlans() is deliberately absent: BASE_PLAN_ID is
        // auto-renewing, so it would be inert.
        .enablePendingPurchases(pendingPurchasesParams())
        // Play's own reconnection with backoff. Without it a dropped service
        // (a Play Store self-update is the common cause) stays dropped, every
        // later query fails, and every user looks unentitled at once.
        .enableAutoServiceReconnection()
        .build()

    /**
     * Asks Play what this account owns, and records the answer.
     *
     * Safe to call on every app start and every resume; it is the only thing
     * that ever moves entitlement.
     */
    suspend fun refresh() {
        if (!connect()) {
            // Could not even reach Play. Explicitly NOT a negative answer.
            _entitlement.value = store.cached().copy(state = EntitlementState.UNKNOWN)
            return
        }
        readEntitlement()
        readOffers()
    }

    /**
     * Connects if needed. False means Play is unreachable right now.
     *
     * Serialised, because refresh() is explicitly invited from both app start
     * and onResume. Two startConnection calls racing make BillingClientImpl
     * answer the second one synchronously with DEVELOPER_ERROR ("Client is
     * already in the process of connecting"), which would read here as
     * "Play unreachable". The mutex makes that branch unreachable from our own
     * code — which is the fix. Matching on response code 5 instead would not
     * be: the library shares that constant across many unrelated results,
     * separable only by an internal debug string free to change in any release.
     */
    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return connectMutex.withLock {
            // Re-check inside the lock: the connection we queued behind may
            // have landed, including one whose own caller was cancelled after
            // it had already succeeded.
            if (client.isReady) return@withLock true
            awaitConnection()
        }
    }

    private suspend fun awaitConnection(): Boolean =
        suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                private var resumed = false
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (resumed) return
                    resumed = true
                    val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                    if (!ok) {
                        // Previously discarded, which was the only reason a
                        // failed connect was hard to diagnose in the field.
                        Log.w(TAG, "connect: " + result.responseCode + " " + result.debugMessage)
                    }
                    cont.resume(ok)
                }
                override fun onBillingServiceDisconnected() {
                    // Auto-reconnection handles the retry; this only matters if
                    // it fires before setup ever finished.
                    if (resumed) return
                    resumed = true
                    cont.resume(false)
                }
            })
        }

    /**
     * The entitlement query, and the branch that keeps an unreachable Play
     * from looking like a refusal.
     */
    private suspend fun readEntitlement() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val answer = suspendCancellableCoroutine { cont ->
            client.queryPurchasesAsync(params) { r, p -> cont.resume(r to p) }
        }
        val result = answer.first
        val purchases = answer.second

        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                // An authoritative answer — including an authoritative NO when
                // the list comes back empty.
                applyPurchases(purchases, authoritative = true)

            // Play could not be reached or could not answer. Not a NO.
            BillingClient.BillingResponseCode.NETWORK_ERROR,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.SERVICE_TIMEOUT,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
                _entitlement.value = store.cached().copy(state = EntitlementState.UNKNOWN)

            else -> {
                // DEVELOPER_ERROR, FEATURE_NOT_SUPPORTED and friends are our
                // bugs or an unsupported device, never the user's fault, so
                // they must not lock anyone out either.
                Log.w(TAG, "queryPurchases: unexpected code " + result.responseCode)
                _entitlement.value = store.cached().copy(state = EntitlementState.UNKNOWN)
            }
        }
    }

    /**
     * Turns Play's purchase list into an entitlement, and acknowledges
     * anything new.
     *
     * THE GRANT RULE: purchased AND not suspended. [Purchase.isSuspended]
     * covers a subscription Play has put on hold pending payment recovery —
     * the account is still attached to the product but the user is not paying
     * for the moment, so it must not grant access.
     */
    private fun applyPurchases(purchases: List<Purchase>?, authoritative: Boolean) {
        val active = purchases.orEmpty().filter { p ->
            p.products.contains(BillingIds.SUBSCRIPTION_PRODUCT_ID) &&
                p.purchaseState == Purchase.PurchaseState.PURCHASED &&
                !p.isSuspended
        }

        active.filterNot { it.isAcknowledged }.forEach(::acknowledge)

        val entitled = active.isNotEmpty()
        // A partial list may GRANT but must never REVOKE.
        if (!authoritative && !entitled) return
        store.record(entitled)
        _entitlement.value = Entitlement(
            state = if (entitled) EntitlementState.ENTITLED else EntitlementState.NOT_ENTITLED,
            lastKnownEntitled = entitled,
            lastCheckMillis = System.currentTimeMillis(),
        )
    }

    /**
     * A purchase not acknowledged within three days is AUTOMATICALLY REFUNDED
     * by Google and the subscription revoked. Every sweep re-checks, so a
     * failure here is retried on the next app start rather than lost.
     */
    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "acknowledge failed: " + result.responseCode)
            }
        }
    }

    /** Loads what is on sale, including whether this account still gets a trial. */
    private suspend fun readOffers() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(BillingIds.SUBSCRIPTION_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        val details = suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { result, queryResult ->
                cont.resume(
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        queryResult.productDetailsList.firstOrNull()
                    } else {
                        null
                    }
                )
            }
        }
        // Only replace on success. Assigning null on a failed query would
        // regress a StateFlow that already held a good price back to "nothing
        // for sale", blanking the paywall's buy button because the network
        // blinked.
        details?.let { _offers.value = SubscriptionOffers.from(it) }
    }

    /**
     * Opens Play's checkout sheet. The result does NOT come back from here —
     * it arrives through the purchases listener, because a purchase can also
     * complete long after this call returns.
     */
    fun launchPurchaseFlow(activity: Activity, offerToken: String): Boolean {
        val product = _offers.value?.productDetails ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    private companion object {
        const val TAG = "BillingRepository"
    }
}

/**
 * The pending-purchases configuration this app's [BillingClient] is built with.
 *
 * Extracted to file scope for one reason: it is the only part of the client
 * construction that needs no [android.content.Context], so it is the only part
 * a plain JVM unit test can exercise — and it is exactly the part that was
 * wrong. A test that rebuilt these params itself would pin nothing, since it
 * would be checking its own copy; BillingParamsTest calls THIS function.
 */
internal fun pendingPurchasesParams(): PendingPurchasesParams =
    PendingPurchasesParams.newBuilder()
        // NOT optional, despite this app selling no one-time product.
        .enableOneTimeProducts()
        .build()

/**
 * What Play is currently willing to sell THIS account.
 *
 * The free trial is not a flag we set — it is an offer Play either includes or
 * omits, having already decided eligibility from the account's history across
 * every device the user owns. So [trialOfferToken] being null IS the answer
 * "this person has already had their free month", and the paywall must read it
 * that way rather than advertising a trial the checkout would then refuse.
 */
data class SubscriptionOffers(
    val productDetails: ProductDetails,
    /** Non-null only while Play still considers this account trial-eligible. */
    val trialOfferToken: String?,
    /** The plain recurring plan. Always present if the product exists. */
    val standardOfferToken: String?,
    /** e.g. "2.20 ILS" — Play's own localised string, never built by us. */
    val formattedPrice: String?,
) {
    val hasFreeTrial: Boolean get() = trialOfferToken != null

    /** What to actually buy: the trial when it is on the table, else the plan. */
    val bestOfferToken: String? get() = trialOfferToken ?: standardOfferToken

    companion object {
        fun from(details: ProductDetails): SubscriptionOffers {
            val offers = details.subscriptionOfferDetails.orEmpty()
                .filter { it.basePlanId == BillingIds.BASE_PLAN_ID }

            // A trial is a pricing phase that costs nothing. Matched on the
            // PRICE as well as the id, so a renamed offer in the console still
            // works and a zero-price phase is never missed.
            val trial = offers.firstOrNull { offer ->
                offer.offerId == BillingIds.FREE_TRIAL_OFFER_ID &&
                    offer.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }
            } ?: offers.firstOrNull { offer ->
                offer.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }
            }

            // The base plan itself carries a null offerId, per the Play API.
            val standard = offers.firstOrNull { it.offerId == null } ?: offers.lastOrNull()

            val price = standard?.pricingPhases?.pricingPhaseList
                ?.lastOrNull { it.priceAmountMicros > 0L }?.formattedPrice
                ?: offers.flatMap { it.pricingPhases.pricingPhaseList }
                    .firstOrNull { it.priceAmountMicros > 0L }?.formattedPrice

            return SubscriptionOffers(
                productDetails = details,
                trialOfferToken = trial?.offerToken,
                standardOfferToken = standard?.offerToken,
                formattedPrice = price,
            )
        }
    }
}
