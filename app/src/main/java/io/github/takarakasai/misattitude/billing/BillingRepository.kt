package io.github.takarakasai.misattitude.billing

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
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single source of truth for the user's "Pro upgrade" entitlement.
 *
 * Responsibilities:
 *
 *   1. Maintains a [BillingClient] connected to Google Play. Reconnects on
 *      transient disconnects.
 *   2. On every connect and on every purchase event, queries owned purchases
 *      and updates [proPurchased] accordingly.
 *   3. Persists the latest known entitlement to [SharedPreferences] so that
 *      the app launches with the correct ad-visibility state even before the
 *      first BillingClient round-trip completes (offline launch, slow Wi-Fi).
 *   4. Acknowledges new purchases within Google's 3-day SLA. An unacknowledged
 *      purchase is auto-refunded by Google, which would be a terrible UX.
 *   5. Exposes [launchPurchaseFlow] for the UI to initiate the Play purchase
 *      sheet; the result is delivered via the same [proPurchased] flow.
 *
 * **Threading**: BillingClient callbacks fire on a Google-managed worker
 * thread. We immediately hop onto an internal [CoroutineScope] (IO dispatcher)
 * so [proPurchased] emissions are safe to collect from any other coroutine.
 */
class BillingRepository(context: Context) {

    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ─── Observable entitlement state ────────────────────────────────────────
    //
    // Seeded from the persisted flag so that the very first composition on
    // launch already reflects what the user owned at the previous run end.
    // Once BillingClient connects we override with the authoritative value
    // from queryPurchasesAsync.
    private val _proPurchased = MutableStateFlow(prefs.getBoolean(KEY_PRO, false))
    val proPurchased: StateFlow<Boolean> = _proPurchased.asStateFlow()

    /** Cached ProductDetails for the Pro SKU, populated on connect.
     *  Needed to construct the BillingFlowParams when the user taps "Buy". */
    private var proProductDetails: ProductDetails? = null

    // ─── BillingClient setup ────────────────────────────────────────────────

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                // Expected; nothing to do beyond logging.
                Log.d(TAG, "Purchase cancelled by user")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // Race condition: the local UI offered the purchase (e.g. the
                // entitlement query hadn't completed yet, or SharedPreferences
                // was stale) but Google's records show the user already owns
                // the SKU. Resync from Google so the UI snaps to Pro state
                // without making the user retry / contact support.
                Log.i(TAG, "ITEM_ALREADY_OWNED — resyncing entitlement from Google")
                scope.launch { refreshPurchases() }
            }
            else -> {
                Log.w(TAG, "Purchase flow error: ${result.responseCode} ${result.debugMessage}")
            }
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    // ─── Lifecycle ──────────────────────────────────────────────────────────

    /** Open the BillingClient connection. Idempotent — calling on an already
     *  connected client is a no-op. */
    fun connect() {
        if (billingClient.isReady) return
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        queryProProductDetails()
                        refreshPurchases()
                    }
                } else {
                    Log.w(TAG, "Billing setup failed: ${billingResult.responseCode} ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // BillingClient's documentation: implement your own retry policy
                // here. For an app this small, we let the next user action
                // re-trigger connect() rather than auto-retrying.
                Log.d(TAG, "Billing service disconnected")
            }
        })
    }

    // ─── Public API for the UI ──────────────────────────────────────────────

    /** Launch the Google Play purchase sheet for the Pro upgrade. Result is
     *  delivered asynchronously through [proPurchased]. No-op if the product
     *  details haven't loaded yet (BillingClient still connecting). */
    fun launchPurchaseFlow(activity: Activity) {
        // QA mode short-circuit: the device's Google account legitimately owns
        // the SKU but we're forcing the Free UI for screenshotting. Letting
        // the real Play sheet open would just yield ITEM_ALREADY_OWNED, which
        // looks like a bug to the QA tester. Instead, no-op with a log.
        if (DEBUG_FORCE_FREE && io.github.takarakasai.misattitude.BuildConfig.DEBUG) {
            Log.i(TAG, "DEBUG_FORCE_FREE active — suppressing real purchase flow")
            return
        }
        val details = proProductDetails
        if (details == null) {
            Log.w(TAG, "Pro product details not yet loaded; ignoring purchase request")
            connect()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()
        billingClient.launchBillingFlow(activity, params)
    }

    /** Manually trigger a refresh from Google Play — used by the "Restore
     *  purchases" UI button so users who switched devices can recover
     *  ownership without paying again. */
    fun restorePurchases() {
        scope.launch { refreshPurchases() }
    }

    /** The displayable price string for the Pro SKU, e.g. "¥800" or "$5.00".
     *  Empty until ProductDetails has been queried. The Composable button
     *  shows this so the user knows what they're paying before tapping. */
    val proPriceFormatted: String
        get() = proProductDetails
            ?.oneTimePurchaseOfferDetails
            ?.formattedPrice
            .orEmpty()

    // ─── Internal helpers ───────────────────────────────────────────────────

    private suspend fun queryProProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_PRO_REMOVE_ADS)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        val result = billingClient.queryProductDetails(params)
        val list = result.productDetailsList
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(
                TAG,
                "queryProductDetails failed: ${result.billingResult.responseCode} ${result.billingResult.debugMessage}",
            )
            return
        }
        proProductDetails = list?.firstOrNull { it.productId == PRODUCT_PRO_REMOVE_ADS }
        if (proProductDetails == null) {
            Log.w(TAG, "Pro product details missing — is the product 'Active' in Play Console?")
        }
    }

    private suspend fun refreshPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = billingClient.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "queryPurchases failed: ${result.billingResult.responseCode}")
            return
        }
        var owned = false
        for (purchase in result.purchasesList) {
            if (PRODUCT_PRO_REMOVE_ADS in purchase.products &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            ) {
                owned = true
                if (!purchase.isAcknowledged) {
                    acknowledge(purchase)
                }
            }
        }
        updateEntitlement(owned)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (PRODUCT_PRO_REMOVE_ADS !in purchase.products) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        scope.launch {
            if (!purchase.isAcknowledged) {
                acknowledge(purchase)
            }
            updateEntitlement(true)
        }
    }

    private suspend fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        val result = billingClient.acknowledgePurchase(params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "acknowledge failed: ${result.responseCode} ${result.debugMessage}")
        }
    }

    private fun updateEntitlement(pro: Boolean) {
        // Always persist the *real* entitlement to prefs so DEBUG_FORCE_FREE
        // can be flipped on/off without poisoning the cache. The override
        // only affects the observable StateFlow.
        prefs.edit().putBoolean(KEY_PRO, pro).apply()
        val effective = if (DEBUG_FORCE_FREE && io.github.takarakasai.misattitude.BuildConfig.DEBUG) {
            false
        } else {
            pro
        }
        _proPurchased.value = effective
    }

    companion object {
        private const val TAG = "BillingRepository"

        /**
         * Set to `true` ONLY when you need to see the free-tier UI on a device
         * that owns the Pro SKU (e.g. screenshotting the Pro-gated screens
         * during QA). Has no effect outside debug builds. **Must be false in
         * any release build**, including internal-testing AABs.
         */
        private const val DEBUG_FORCE_FREE = false

        /** Must match the product ID configured in Play Console → Monetisation
         *  → Products → "Managed in-app products". Choose **non-consumable**
         *  / managed; consumable would let the user repurchase, which we
         *  don't want for a one-shot ad-removal entitlement. */
        const val PRODUCT_PRO_REMOVE_ADS = "misattitude_pro_remove_ads"

        private const val PREFS_NAME = "misattitude_billing"
        private const val KEY_PRO = "pro_purchased"
    }
}
