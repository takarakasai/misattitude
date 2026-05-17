package io.github.takarakasai.misattitude

import android.app.Application
import io.github.takarakasai.misattitude.billing.BillingRepository

/**
 * Owns process-scoped singletons that need a [Context] before any Activity
 * is created. Currently:
 *
 *   * [billingRepository] — connects to Google Play Billing exactly once
 *     and exposes the user's Pro-entitlement state via a StateFlow that
 *     [AttitudeViewModel] consumes.
 *
 * Registered via `android:name=".MisattitudeApplication"` on the
 * `<application>` element in AndroidManifest.xml.
 */
class MisattitudeApplication : Application() {

    lateinit var billingRepository: BillingRepository
        private set

    override fun onCreate() {
        super.onCreate()
        billingRepository = BillingRepository(this).also { it.connect() }
    }
}
