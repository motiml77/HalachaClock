package com.zmanimclock.app.feature.subscription

/**
 * The three strings that must match Google Play Console CHARACTER FOR
 * CHARACTER. A typo here does not fail loudly: `queryProductDetailsAsync`
 * simply returns nothing for the unknown id, the paywall finds no offer, and
 * the app looks like it has no subscription for sale at all.
 *
 * None of the three can be renamed once the subscription has been created in
 * the console, so they are fixed for the life of the product.
 */
object BillingIds {

    /** Monetize with Play → Products → Subscriptions → "Product ID". */
    const val SUBSCRIPTION_PRODUCT_ID = "halacha_clock_monthly"

    /** The base plan inside that subscription — the recurring monthly charge. */
    const val BASE_PLAN_ID = "monthly-autorenew"

    /**
     * The free-month OFFER attached to the base plan.
     *
     * Only used to recognise the trial in what Play hands back. Eligibility is
     * decided by PLAY, on its servers, from the criterion configured on this
     * offer — never by the app. When the user has already had their month, Play
     * simply omits this offer from the product details, which is exactly how
     * the paywall knows to stop advertising a free trial.
     */
    const val FREE_TRIAL_OFFER_ID = "free-month"
}
