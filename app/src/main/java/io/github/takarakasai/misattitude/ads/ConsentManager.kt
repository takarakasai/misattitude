package io.github.takarakasai.misattitude.ads

import android.app.Activity
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Thin wrapper around Google's User Messaging Platform (UMP) SDK, which is the
 * official channel for GDPR / EU consent collection on Android.
 *
 * Why this needs to exist at all:
 *
 *   * AdMob's policy requires apps to surface a consent form to users in the
 *     EU / EEA / UK before serving personalised ads. Without it, fill rate in
 *     those regions drops to ~0 and the publisher account can be flagged.
 *   * UMP also handles the "U.S. State privacy law" disclosures (CCPA et al)
 *     and IDFA/GAID equivalents on Android. Calling it covers all of these
 *     in one go.
 *   * The SDK is region-aware: outside any regulated region the form simply
 *     never appears, so it's safe to call unconditionally on every launch.
 *
 * Flow:
 *
 *   1. [requestConsentIfNeeded] is invoked from [MainActivity.onCreate],
 *      before [MobileAds.initialize].
 *   2. UMP fetches the latest consent state from the network.
 *   3. If a form is needed (user is in a regulated region and hasn't yet
 *      provided consent / has revoked it) it's shown modally.
 *   4. Regardless of outcome, [onReady] fires. The Mobile Ads SDK queries
 *      [ConsentInformation.canRequestAds] internally before loading ads, so
 *      no additional gating is needed in our AdBanner code.
 *
 * Debug aid: set [DEBUG_FORCE_GEOGRAPHY_EEA] to true to force the SDK to
 * behave as if every test device were in the EEA (the form will show
 * regardless of physical location). Useful for verifying the dialog visually.
 */
object ConsentManager {

    private const val TAG = "ConsentManager"

    /** Flip to true locally to force the EU dialog on all test devices.
     *  MUST be false in production builds. */
    private const val DEBUG_FORCE_GEOGRAPHY_EEA = false

    /**
     * Trigger a one-time consent refresh from the UMP SDK. Safe to call on every
     * Activity create — the SDK caches the latest decision and only re-shows the
     * form when something material has changed.
     *
     * @param activity   the hosting Activity; the form (if any) is shown over it
     * @param onReady    invoked when UMP has finished — either after the user
     *                   dismisses the form, or immediately if no form is required.
     *                   The Mobile Ads SDK reads consent state lazily, so callers
     *                   typically use this callback to gate `MobileAds.initialize`
     *                   and any ad-load that should not run before consent.
     */
    fun requestConsentIfNeeded(activity: Activity, onReady: () -> Unit) {
        val paramsBuilder = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)

        if (DEBUG_FORCE_GEOGRAPHY_EEA) {
            val debugSettings = ConsentDebugSettings.Builder(activity)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                // Note: in a real test setup you'd also call addTestDeviceHashedId(...)
                // with the SHA-1 of your test device's advertising ID. The hash is
                // printed in logcat the first time UMP runs, then you paste it back
                // here. Until then the debug geography is ignored on the device.
                .build()
            paramsBuilder.setConsentDebugSettings(debugSettings)
        }

        val params = paramsBuilder.build()
        val consentInformation: ConsentInformation =
            UserMessagingPlatform.getConsentInformation(activity)

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                // Consent info refreshed. Show the form if UMP says we need to.
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error: code=${formError.errorCode} msg=${formError.message}")
                    }
                    onReady()
                }
            },
            { requestError ->
                // Network failure or similar. Best practice: still call onReady so
                // the rest of the app proceeds; AdMob will simply serve
                // non-personalised ads (or no ads in regulated regions).
                Log.w(TAG, "Consent info update failed: code=${requestError.errorCode} msg=${requestError.message}")
                onReady()
            },
        )
    }
}
