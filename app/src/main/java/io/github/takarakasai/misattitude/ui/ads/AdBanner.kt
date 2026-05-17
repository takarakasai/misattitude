package io.github.takarakasai.misattitude.ui.ads

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Test-mode ad unit IDs published by Google. These can be used freely during
 * development — they always serve test ads and never accrue revenue. Replace
 * both with real ad unit IDs (created in the AdMob console) before promoting
 * the build to production.
 *
 * Reference: https://developers.google.com/admob/android/test-ads
 */
object AdMobUnitIds {
    /** Banner — test unit. */
    const val BANNER_TEST = "ca-app-pub-3940256099942544/6300978111"
}

/**
 * AdMob banner ad host for Compose.
 *
 * Why an [AndroidView] wrapper rather than a Compose-native widget: AdMob's
 * SDK is built around the legacy View hierarchy ([AdView]) and there is no
 * Compose-first equivalent. The wrapper:
 *
 *   1. Creates one [AdView] per Composable instance, sized to an "adaptive
 *      banner" that picks an ideal height for the current device width (more
 *      polished than the fixed 320×50 [AdSize.BANNER]).
 *   2. Forwards Activity/Fragment lifecycle events to the AdView so impression
 *      tracking, ad refresh, and resource cleanup behave correctly when the
 *      app goes to background or the host destroys this Composable.
 *   3. Loads exactly one ad request when the AdView is created; the SDK
 *      auto-refreshes on its own schedule afterwards.
 *
 * Show / hide policy lives at the call site (see [MainScreen]): when the user
 * owns the Pro upgrade we simply skip rendering this Composable entirely,
 * which destroys the AdView (no further requests) and reclaims the screen
 * row to zero height.
 *
 * @param adUnitId The AdMob ad unit ID. Use [AdMobUnitIds.BANNER_TEST] during
 *   development to receive Google's reserved test ads.
 */
@Composable
fun AdBanner(
    adUnitId: String = AdMobUnitIds.BANNER_TEST,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val adSize = remember(context, configuration.screenWidthDp) {
        adaptiveBannerAdSize(context, configuration.screenWidthDp)
    }
    val adView = remember(adUnitId, adSize) {
        AdView(context).apply {
            setAdSize(adSize)
            this.adUnitId = adUnitId
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(lifecycleOwner, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> adView.resume()
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                Lifecycle.Event.ON_DESTROY -> adView.destroy()
                else -> { /* not interested */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Belt-and-braces: even if the Activity itself isn't being
            // destroyed, when this Composable leaves the tree we don't want
            // a dangling AdView holding network resources.
            adView.destroy()
        }
    }
    Box(modifier = modifier.fillMaxWidth()) {
        AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Pick an AdMob "adaptive banner" sized for the current device's screen width.
 * Adaptive banners are AdMob's recommendation since 2020+: they're roughly the
 * same height as a fixed 320×50 banner on phones but stretch to full width on
 * larger devices and adjust height for tall aspect ratios.
 *
 * The width is supplied by the caller (typically [LocalConfiguration].screenWidthDp)
 * which avoids the deprecated WindowManager.defaultDisplay path and stays
 * Composition-aware: when the device rotates or window size changes, the
 * caller's `remember(... screenWidthDp)` re-runs and we pick a fresh size.
 */
private fun adaptiveBannerAdSize(context: Context, widthDp: Int): AdSize {
    val effectiveWidthDp = widthDp.coerceAtLeast(50)
    return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, effectiveWidthDp)
}
