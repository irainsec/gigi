package com.aman.gigi.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import com.aman.gigi.data.client.ConnectionBootstrapManager
import com.android.billingclient.api.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BillingEntryPoint {
        fun bootstrapManager(): ConnectionBootstrapManager
    }

    private val bootstrapManager: ConnectionBootstrapManager by lazy {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            BillingEntryPoint::class.java
        ).bootstrapManager()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl = run {
        val wsUri = URI(com.aman.gigi.BuildConfig.SERVER_URL)
        val scheme = if (wsUri.scheme.equals("wss", ignoreCase = true)) "https" else "http"
        URI(scheme, wsUri.userInfo, wsUri.host, if (wsUri.port == -1) -1 else wsUri.port, null, null, null)
            .toString().trimEnd('/')
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()

    /** productId → localized formatted price (e.g. "₹99.00"), filled by [queryPrices]. */
    private val _prices = MutableStateFlow<Map<String, String>>(emptyMap())
    val prices = _prices.asStateFlow()

    /** SENDING while a purchase is being verified with the server, then SENT/ERROR. */
    private val _verificationState = MutableStateFlow<VerificationState>(VerificationState.Idle)
    val verificationState = _verificationState.asStateFlow()

    sealed class VerificationState {
        data object Idle : VerificationState()
        data object Verifying : VerificationState()
        data object Verified : VerificationState()
        data class Failed(val reason: String) : VerificationState()
    }

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _isReady.value = true
                    // Re-verify any subscription Google still considers active — this
                    // restores purchases on reinstall and rescues unacknowledged ones.
                    restorePurchases()
                }
            }
            override fun onBillingServiceDisconnected() {
                _isReady.value = false
            }
        })
    }

    /** Fetches the localized recurring price for each subscription so UI never hardcodes amounts. */
    fun queryPrices(productIds: List<String>) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
            )
            .build()
        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val found = productDetailsList.mapNotNull { details ->
                    val price = details.subscriptionOfferDetails
                        ?.firstOrNull()
                        ?.pricingPhases?.pricingPhaseList
                        ?.lastOrNull { it.priceAmountMicros > 0 }
                        ?.formattedPrice
                    price?.let { details.productId to it }
                }.toMap()
                if (found.isNotEmpty()) {
                    _prices.value = _prices.value + found
                }
            }
        }
    }

    fun purchaseSubscription(activity: Activity, productId: String) {
        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken

                if (offerToken != null) {
                    val productDetailsParamsList = listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .setOfferToken(offerToken)
                            .build()
                    )

                    val billingFlowParams = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(productDetailsParamsList)
                        .build()

                    billingClient.launchBillingFlow(activity, billingFlowParams)
                } else {
                    Log.e(TAG, "No offer token found for subscription")
                }
            } else {
                Log.e(TAG, "Product details query failed: ${billingResult.debugMessage}")
            }
        }
    }

    fun restorePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases
                    .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .forEach { verifyWithServer(it) }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .forEach { verifyWithServer(it) }
        } else if (billingResult.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.e(TAG, "Purchase failed: ${billingResult.debugMessage}")
        }
    }

    /**
     * Sends the purchase token to the server, which verifies it with the Google Play
     * Developer API, acknowledges it, and upgrades the member's tier. The refreshed
     * plan then flows back through the normal bootstrap → AppConfig path.
     */
    private fun verifyWithServer(purchase: Purchase) {
        val productId = purchase.products.firstOrNull() ?: return
        val sessionToken = bootstrapManager.memberIdentity.value?.authToken
        if (sessionToken.isNullOrBlank()) {
            Log.w(TAG, "No session token available; cannot verify purchase yet")
            return
        }

        scope.launch {
            _verificationState.value = VerificationState.Verifying
            try {
                val body = JSONObject()
                    .put("productId", productId)
                    .put("purchaseToken", purchase.purchaseToken)
                    .toString()
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$baseUrl/api/billing/verify-purchase")
                    .addHeader("x-session-token", sessionToken)
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.i(TAG, "✅ Purchase verified for $productId")
                        _verificationState.value = VerificationState.Verified
                        bootstrapManager.refreshFromServer("billing_purchase_verified")
                    } else {
                        val msg = response.body?.string()?.take(200) ?: "HTTP ${response.code}"
                        Log.e(TAG, "❌ Purchase verification rejected: $msg")
                        _verificationState.value = VerificationState.Failed("Verification failed")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Purchase verification error", e)
                _verificationState.value = VerificationState.Failed(e.message ?: "Network error")
            }
        }
    }

    companion object {
        private const val TAG = "Billing"
    }
}
