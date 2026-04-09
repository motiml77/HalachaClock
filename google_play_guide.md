# Google Play Store Publishing Guide - ZmanimClock App

## Table of Contents
1. [Developer Account Setup](#1-developer-account-setup)
2. [App Technical Requirements](#2-app-technical-requirements)
3. [Store Listing Assets](#3-store-listing-assets)
4. [Privacy & Data Safety](#4-privacy--data-safety)
5. [Content Rating & Category](#5-content-rating--category)
6. [Pre-Launch Checklist](#6-pre-launch-checklist)
7. [Publishing Timeline](#7-publishing-timeline)
8. [Monetization Architecture](#8-monetization-architecture)
9. [Google Play Billing Implementation](#9-google-play-billing-implementation)
10. [Firebase Remote Config for Feature Flags](#10-firebase-remote-config-for-feature-flags)
11. [Premium Feature Strategy for a Zmanim App](#11-premium-feature-strategy-for-a-zmanim-app)
12. [Alternative Monetization](#12-alternative-monetization)
13. [Israeli Developer Tax & Payment](#13-israeli-developer-tax--payment)
14. [Step-by-Step Action Plan](#14-step-by-step-action-plan)

---

## 1. Developer Account Setup

### Registration
- **Cost**: One-time fee of **$25 USD** (non-refundable)
- **URL**: https://play.google.com/console/signup
- **Payment**: Credit or debit card

### Account Types
- **Personal account**: Requires government-issued ID for identity verification
- **Organization account**: Requires a D-U-N-S number + business documentation

For an individual developer in Israel, a **personal account** is the simplest path.

### Verification Process
1. Provide a private email address and phone number (verified via OTP)
2. Provide a public-facing email for your Play Store listing
3. Upload government-issued identity document
4. Create or link a Google Payments profile (for receiving revenue)

### Timeline
- Account creation: **Immediate** after payment
- Identity verification: **24-72 hours** typically, can take up to 7 days
- You cannot publish apps until verification is complete

### Important
- All developers must complete verification. Unverified accounts and their apps will be removed from Google Play.

---

## 2. App Technical Requirements

### Target SDK
- **Current requirement (August 2025+)**: New apps and updates must target **Android 15 (API level 35)**
- Your app already targets `targetSdk = 35` -- you are compliant
- `minSdk = 26` (Android 8.0) is fine, no minimum SDK restriction from Google

### Build Format
- **AAB (Android App Bundle)** is **mandatory** for all apps on Google Play since 2021
- APKs are no longer accepted for new apps
- AAB lets Google Play generate optimized APKs per device

### 64-bit Requirement
- All apps must include 64-bit native libraries
- If your app is pure Kotlin/Java (no NDK), this is automatically satisfied

### Play App Signing
- **Mandatory** for new apps since August 2021
- Google manages your app signing key
- You upload with an "upload key" that you manage locally
- If your upload key is compromised, you can reset it without affecting users

### Key Permissions Requiring Justification

#### SCHEDULE_EXACT_ALARM
- On Android 14+, this permission is **denied by default** for newly installed apps targeting API 33+
- For a zmanim/prayer times app, you have two options:
  - **USE_EXACT_ALARM** (normal permission, auto-granted): Only for apps whose **core functionality** is alarm/timer/calendar. A zmanim app with prayer time notifications **qualifies** for this.
  - **SCHEDULE_EXACT_ALARM** (runtime permission): Must check `canScheduleExactAlarms()` before scheduling, and handle the case where user hasn't granted it.
- **Recommendation**: Use `USE_EXACT_ALARM` since your app is fundamentally a time/alarm app. In your Play Console declaration, explain that the app alerts users to specific halachic times throughout the day.

#### ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION
- Must justify in Play Console why you need location
- **Justification**: "The app calculates Jewish prayer times (zmanim) based on the user's geographic coordinates. Accurate location is required because prayer times vary by latitude, longitude, and elevation."
- Must declare location usage in the Data Safety form

#### POST_NOTIFICATIONS (Android 13+)
- Runtime permission -- must request at runtime
- Must handle denial gracefully (app should work without notifications)

---

## 3. Store Listing Assets

### App Icon
- **Size**: 512 x 512 px
- **Format**: 32-bit PNG (with alpha)
- **Shape**: Full square (Google Play applies masking automatically)
- No text overlays with ranking claims or misleading badges

### Feature Graphic
- **Size**: 1024 x 500 px
- **Format**: JPEG or 24-bit PNG (no alpha/transparency)
- **Max file size**: 1 MB
- Keep focal point centered; edges may be cropped on different devices

### Screenshots
- **Minimum**: 4 screenshots required
- **Maximum**: 8 per device type (phone, 7" tablet, 10" tablet)
- **Resolution**: Minimum 1080px on one side
- **Recommended**: 1080 x 1920 px (portrait) or 1920 x 1080 px (landscape)
- First 2-3 screenshots are most important (visible without scrolling)
- Add **alt text** to each screenshot for accessibility

### Text Fields
- **App name**: Max 30 characters (e.g., "ZmanimClock - Jewish Times")
- **Short description**: Max 80 characters
  - Example: "Accurate halachic zmanim, Shabbat times, and prayer notifications for your location"
- **Full description**: Max 4000 characters
  - Include keywords: zmanim, Jewish prayer times, Shabbat, halachic times, sunrise, sunset
  - Describe features, supported halachic opinions, widget, etc.

### Languages
- **Primary**: Hebrew (he)
- **Secondary**: English (en-US)
- Consider: French (fr), Spanish (es) for larger Jewish communities worldwide

---

## 4. Privacy & Data Safety

### Privacy Policy (REQUIRED)
- Must be hosted on a publicly accessible URL
- Must cover: what data you collect, how you use it, how to contact you
- Free options: host on GitHub Pages, Google Sites, or Firebase Hosting
- For a zmanim app, you likely collect:
  - **Location data** (for zmanim calculation)
  - **Device identifiers** (if using Firebase/analytics)

### Data Safety Form
You must complete this in Play Console. For ZmanimClock, likely declarations:

| Data Type | Collected? | Shared? | Purpose |
|-----------|-----------|---------|---------|
| Approximate location | Yes | No | App functionality (zmanim calculation) |
| Precise location | Yes | No | App functionality (zmanim calculation) |
| Crash logs | Yes (if using Crashlytics) | Yes (with Firebase) | Analytics, crash reporting |
| App interactions | Maybe | No | Analytics |

- Even if you collect NO data, you must still complete the form and link your privacy policy

---

## 5. Content Rating & Category

### Content Rating Questionnaire
- Fill out the IARC questionnaire in Play Console
- For a religious zmanim app with no violence, gambling, or user-generated content:
  - Expected rating: **Everyone** (ESRB) / **PEGI 3** / **Rated for 3+**
- Answer all questions honestly about content nature

### Category Selection
- **Best fit**: **Lifestyle** or **Tools**
  - **Lifestyle**: If positioning as a daily Jewish life companion
  - **Tools**: If positioning as a utility/calculator
- Other zmanim apps typically use **Lifestyle** or **Books & Reference**

### Religious App Policy
- Google Play has **no restrictions** on religious content
- No special content policy issues for a Jewish prayer times app
- Avoid any content that could be seen as hate speech toward other religions
- A pure zmanim/halachic times app will have zero policy issues

---

## 6. Pre-Launch Checklist

### Testing Tracks (use before production release)
1. **Internal testing**: Up to 100 testers, no review required, instant deployment
2. **Closed testing**: Invite-only, requires brief review
3. **Open testing**: Anyone can join, requires review
4. **Production**: Full public release, full review

**Recommended flow**: Internal -> Closed (beta) -> Production

### Pre-Launch Report
- Google Play automatically runs your app on physical devices (Firebase Test Lab)
- Checks for crashes, security vulnerabilities, accessibility issues
- Available after uploading to any track
- Fix all critical issues before production release

### Crash Reporting
- Integrate **Firebase Crashlytics** before publishing
- Google Play Console also shows Android vitals (crash rate, ANR rate)
- Target: crash-free rate > 99.5%

### Final Checklist Before Submitting
- [ ] AAB builds successfully in release mode
- [ ] ProGuard/R8 does not break functionality
- [ ] All permissions declared and justified
- [ ] Privacy policy URL is live and accessible
- [ ] Data safety form completed
- [ ] Content rating questionnaire completed
- [ ] App icon (512x512) uploaded
- [ ] Feature graphic (1024x500) uploaded
- [ ] At least 4 phone screenshots uploaded
- [ ] Short description and full description written
- [ ] App tested on multiple devices/API levels
- [ ] Version code is 1 (or appropriate)
- [ ] Signing configured with Play App Signing

---

## 7. Publishing Timeline

| Step | Duration |
|------|----------|
| Developer account creation + payment | 10 minutes |
| Identity verification | 1-7 days |
| Prepare store listing assets | 1-3 days |
| Upload AAB to internal testing | 10 minutes |
| Internal testing phase | 1-7 days (your choice) |
| Submit to production review | 10 minutes |
| Google review (first app) | 3-7 days (can be longer for new accounts) |
| Subsequent updates review | A few hours to 3 days |

**Total from start to live**: Approximately **1-2 weeks** for a first-time developer.

### Common Rejection Reasons
1. Missing or inadequate privacy policy
2. Permission usage not justified (especially location)
3. Broken functionality or crashes
4. Misleading store listing (screenshots don't match app)
5. Intellectual property violations (using others' trademarks)

---

## 8. Monetization Architecture

### Design Philosophy: Build for Free, Prepare for Premium

The key insight: **add the billing infrastructure now** so that when you want to monetize, you only need to:
1. Create products in Play Console (no code change)
2. Flip a Remote Config flag (no app update)

### Architecture Overview

```
[Firebase Remote Config] --> controls which features require payment
[Google Play Billing]    --> handles purchase flow
[Local purchase cache]   --> remembers what user bought (Room DB)
```

### What Should Be Free vs Premium in a Zmanim App

**Always Free (core value)**:
- Basic daily zmanim (sunrise, sunset, shkia, netz, chatzot)
- Shabbat candle lighting times
- Current Hebrew date
- Basic notifications for key zmanim

**Potential Premium Features**:
- Multiple zmanim opinions (Rabbeinu Tam, GR"A, etc.)
- Advanced widget customization
- Multiple location tracking
- Detailed halachic information for each zman
- Custom notification sounds/vibration patterns
- Calendar integration
- Sefira counter with custom reminders
- Ad-free experience (if ads are added to free tier)
- Compass/direction to Jerusalem
- Zmanim for upcoming week/month view

---

## 9. Google Play Billing Implementation

### Step 1: Add Dependencies

In `app/build.gradle.kts`:

```kotlin
dependencies {
    // Google Play Billing
    val billing_version = "7.1.1"  // Use 7.x for stability; 8.x available but newer
    implementation("com.android.billingclient:billing:$billing_version")
    implementation("com.android.billingclient:billing-ktx:$billing_version")

    // Firebase Remote Config (for feature flags)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-config-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
}
```

### Step 2: BillingManager Class

```kotlin
package com.zmanimclock.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchasesUpdatedListener {

    private lateinit var billingClient: BillingClient

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium

    private val _availableProducts = MutableStateFlow<List<ProductDetails>>(emptyList())
    val availableProducts: StateFlow<List<ProductDetails>> = _availableProducts

    companion object {
        // Define product IDs -- these must match what you create in Play Console
        const val PREMIUM_MONTHLY = "premium_monthly"
        const val PREMIUM_YEARLY = "premium_yearly"
        const val PREMIUM_LIFETIME = "premium_lifetime"
    }

    fun initialize() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .enablePrepaidPlans()
                    .build()
            )
            .build()

        connectToPlayBilling()
    }

    private fun connectToPlayBilling() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryExistingPurchases()
                    queryAvailableProducts()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Retry connection with exponential backoff in production
            }
        })
    }

    private fun queryAvailableProducts() {
        // Query subscriptions
        val subParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_MONTHLY)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_YEARLY)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(subParams) { result, productDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _availableProducts.value = productDetailsList
            }
        }

        // Query one-time products
        val otpParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_LIFETIME)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(otpParams) { result, productDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _availableProducts.value = _availableProducts.value + productDetailsList
            }
        }
    }

    private fun queryExistingPurchases() {
        // Check for existing subscriptions
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _isPremium.value = purchases.any {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
            }
        }

        // Check for lifetime purchase
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchases.any {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    it.products.contains(PREMIUM_LIFETIME)
                }) {
                    _isPremium.value = true
                }
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        val offerToken = productDetails.subscriptionOfferDetails
            ?.firstOrNull()?.offerToken

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .apply {
                    if (offerToken != null) setOfferToken(offerToken)
                }
                .build()
        )

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(
        result: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        acknowledgePurchase(purchase)
                        _isPremium.value = true
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                // User canceled -- do nothing
            }
            else -> {
                // Handle other error codes
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { /* handle result */ }
        }
    }
}
```

### Step 3: Premium Feature Gate

```kotlin
package com.zmanimclock.app.billing

import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumFeatureManager @Inject constructor(
    private val billingManager: BillingManager,
    private val remoteConfigManager: RemoteConfigManager
) {
    val isPremium: StateFlow<Boolean> = billingManager.isPremium

    /**
     * Check if a specific feature requires premium.
     * This is controlled by Firebase Remote Config so you can
     * change it without an app update.
     */
    fun isFeatureLocked(featureKey: String): Boolean {
        // If user is premium, nothing is locked
        if (billingManager.isPremium.value) return false

        // Check Remote Config to see if this feature requires premium
        return remoteConfigManager.getBoolean("premium_required_$featureKey")
    }

    /**
     * Check if monetization is enabled at all.
     * Start with this as false. When ready to monetize,
     * flip it to true in Firebase Remote Config.
     */
    fun isMonetizationEnabled(): Boolean {
        return remoteConfigManager.getBoolean("monetization_enabled")
    }
}
```

### Step 4: Adding Products in Play Console (No App Update Needed)

When you're ready to monetize:

1. Go to **Play Console > Your App > Monetize > Products > Subscriptions**
2. Create subscription products:
   - `premium_monthly` - Monthly premium (e.g., 9.90 ILS/month)
   - `premium_yearly` - Yearly premium (e.g., 79.90 ILS/year)
3. Go to **Monetize > Products > In-app products**
   - `premium_lifetime` - One-time lifetime purchase (e.g., 149.90 ILS)
4. Activate the products
5. In Firebase Remote Config, set `monetization_enabled` = `true`

The app will detect the products and show the purchase UI without any code update.

---

## 10. Firebase Remote Config for Feature Flags

### Step 1: Setup Firebase

```kotlin
// In your Application class or Hilt module
package com.zmanimclock.app.billing

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteConfigManager @Inject constructor() {

    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    // Default values -- all features free, monetization off
    private val defaults = mapOf(
        "monetization_enabled" to false,
        "premium_required_multiple_locations" to false,
        "premium_required_advanced_widget" to false,
        "premium_required_custom_notifications" to false,
        "premium_required_weekly_view" to false,
        "premium_required_multiple_opinions" to false,
        "show_ads" to false,
        "premium_price_monthly" to "9.90",
        "premium_price_yearly" to "79.90",
    )

    fun initialize() {
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600) // 1 hour in production
            .build()

        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(defaults)

        // Fetch and activate
        remoteConfig.fetchAndActivate()
    }

    fun getBoolean(key: String): Boolean {
        return remoteConfig.getBoolean(key)
    }

    fun getString(key: String): String {
        return remoteConfig.getString(key)
    }

    fun getLong(key: String): Long {
        return remoteConfig.getLong(key)
    }
}
```

### Step 2: Usage in UI

```kotlin
@Composable
fun ZmanimFeature(
    featureKey: String,
    premiumFeatureManager: PremiumFeatureManager,
    onUpgradeClick: () -> Unit,
    content: @Composable () -> Unit
) {
    if (premiumFeatureManager.isFeatureLocked(featureKey)) {
        // Show locked state with upgrade button
        PremiumLockedOverlay(onUpgradeClick = onUpgradeClick)
    } else {
        content()
    }
}
```

### Monetization Activation Flow (No App Update)

1. **Firebase Console** > Remote Config
2. Set `monetization_enabled` = `true`
3. Set individual feature flags (e.g., `premium_required_weekly_view` = `true`)
4. Publish changes
5. App fetches new config within 1 hour and starts showing premium gates

---

## 11. Premium Feature Strategy for a Zmanim App

### Pricing Recommendations (Israel Market)

| Plan | Price (ILS) | Price (USD) |
|------|------------|-------------|
| Monthly | 9.90 | 2.99 |
| Yearly | 59.90 | 17.99 |
| Lifetime | 129.90 | 39.99 |

### Phased Monetization Rollout

**Phase 1 (Launch)**: Everything free, no ads
- Build user base and reviews
- Collect usage data via analytics
- Duration: 3-6 months

**Phase 2 (Soft monetization)**: Add optional premium
- Keep all current features free
- Add new premium-only features (advanced widgets, multiple locations)
- Use Remote Config to enable gradually

**Phase 3 (Full monetization)**: Lock some features
- Move advanced features behind paywall
- Consider ad-supported free tier
- Keep core zmanim always free

---

## 12. Alternative Monetization

### External Payments (USA)
- Following the Epic vs. Google ruling (2025), US developers can now use alternative billing systems
- Google may eventually charge a service fee on external transactions, but currently is not enforcing this
- For a small zmanim app, using Google Play Billing is simpler

### Ad-Supported Model
- Use Google AdMob for banner ads on free tier
- Premium users get ad-free experience
- Recommendation: Avoid intrusive ads in a religious app; small banner at bottom is acceptable

### Donations
- Google Play Billing can be used for "tip jar" one-time purchases
- Create non-consumable in-app products at various price points

### Google Play Policy on External Payments
- In the US: Alternative billing is now allowed
- In the EEA: External offers program available
- In Israel: Standard Google Play Billing is required; no alternative billing program
- **Recommendation for Israeli developer**: Use standard Google Play Billing for simplicity and compliance

---

## 13. Israeli Developer Tax & Payment

### Payment Setup
- Link a valid Israeli bank account in your Google Payments profile
- Google pays developers via bank transfer
- Payments are made monthly (threshold: $100 or local equivalent)
- Google takes a **15% commission** on the first $1M in annual revenue (reduced from 30%)

### VAT (Israel)
- Israel VAT rate: **18%** (as of January 2025, increased from 17%)
- Google handles VAT collection from end users in most countries
- For Israeli customers: Google collects and remits VAT
- You receive revenue **minus** Google's commission
- You are responsible for reporting your income to Israeli tax authorities

### Tax Withholding
- Google may withhold tax on payments from Israeli customers unless you provide a tax exemption letter from the Israel Tax Authority (Rashut HaMisim)
- Provide your Israeli tax ID (Mispar Osek) in Play Console
- Consult with an Israeli accountant (ro'eh heshbon) about:
  - Reporting app income as self-employment (atzma'i) or business
  - Ma'am (VAT) obligations if registered as an osek murshe
  - Income tax (mas hachnasa) on app revenue

### Tax Documents Needed
- Teudat Zehut number for identity verification
- Mispar Osek (business registration number) if applicable
- Israeli bank account details (IBAN)
- Tax exemption letter from Rashut HaMisim (optional, to avoid withholding)

---

## 14. Step-by-Step Action Plan

### Week 1: Account & Assets
- [ ] Pay $25 and create Google Play Developer Account
- [ ] Submit identity verification documents
- [ ] Design app icon (512x512)
- [ ] Design feature graphic (1024x500)
- [ ] Take 4-8 screenshots of the app
- [ ] Write privacy policy and host it online
- [ ] Write short description (80 chars) and full description (4000 chars)

### Week 2: Technical Preparation
- [ ] Add Firebase to the project (Crashlytics + Remote Config + Analytics)
- [ ] Add Google Play Billing Library dependency (inactive, for future use)
- [ ] Create `BillingManager`, `PremiumFeatureManager`, `RemoteConfigManager` classes
- [ ] Ensure all permissions are properly declared and handled
- [ ] Test `USE_EXACT_ALARM` behavior on Android 14+ devices
- [ ] Test location permission flow
- [ ] Test notification permission flow (Android 13+)
- [ ] Build release AAB with proper ProGuard rules
- [ ] Test release build thoroughly

### Week 3: Store Listing & Testing
- [ ] Upload AAB to **internal testing** track
- [ ] Complete Data Safety form
- [ ] Complete content rating questionnaire
- [ ] Fill in all store listing information
- [ ] Set up pricing (free) and distribution (all countries)
- [ ] Review pre-launch report for crashes
- [ ] Test on internal track with 5-10 testers

### Week 4: Launch
- [ ] Fix any issues found in testing
- [ ] Promote to **production** track
- [ ] Wait for Google review (3-7 days for first app)
- [ ] Once approved: app is live
- [ ] Monitor Android Vitals in Play Console
- [ ] Monitor crash reports in Firebase Crashlytics

### Future: Monetization Activation
- [ ] Create subscription and in-app products in Play Console
- [ ] Set prices for all target countries
- [ ] Test purchases using license testers (internal testing track)
- [ ] Flip `monetization_enabled` flag in Firebase Remote Config
- [ ] Monitor conversion rates and adjust pricing

---

## Appendix A: ProGuard Rules for Billing

Add to `proguard-rules.pro`:

```proguard
# Google Play Billing
-keep class com.android.vending.billing.** { *; }
-keep class com.android.billingclient.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-keepattributes *Annotation*
```

## Appendix B: Useful Links

- Play Console: https://play.google.com/console
- Play Billing docs: https://developer.android.com/google/play/billing
- Firebase Remote Config: https://firebase.google.com/docs/remote-config
- Target SDK requirements: https://developer.android.com/google/play/requirements/target-sdk
- Data safety form: https://support.google.com/googleplay/android-developer/answer/10787469
- Content rating: https://support.google.com/googleplay/android-developer/answer/9859655

## Appendix C: Estimated Costs Summary

| Item | Cost | Frequency |
|------|------|-----------|
| Developer account | $25 | One-time |
| Firebase (Spark plan) | Free | Monthly (up to quotas) |
| Privacy policy hosting (GitHub Pages) | Free | - |
| Google Play commission | 15% of revenue | Per transaction |
| Israeli accountant consultation | ~500-1000 ILS | Annual |
| **Total upfront cost** | **$25** | **One-time** |
