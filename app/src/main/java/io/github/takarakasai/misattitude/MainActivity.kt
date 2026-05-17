package io.github.takarakasai.misattitude

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.google.android.filament.utils.Utils
import com.google.android.gms.ads.MobileAds
import io.github.takarakasai.misattitude.ads.ConsentManager
import io.github.takarakasai.misattitude.ui.MainScreen
import io.github.takarakasai.misattitude.ui.theme.MisattitudeTheme
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {

    companion object {
        init {
            // Loads the Filament native libraries. Must run before any Filament call.
            Utils.init()
        }

        // Process-scoped flag: AdMob SDK must be initialised exactly once per
        // process. ConsentManager fires its callback synchronously when no form
        // is needed and asynchronously after the user dismisses one — using a
        // simple AtomicBoolean handles both paths safely.
        private val mobileAdsInitialised = AtomicBoolean(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15 (API 35) は edge-to-edge をデフォルト強制するため、
        // 明示的に enableEdgeToEdge() を呼んで挙動を予測可能にする。
        // 実コンテンツへのインセット適用は MainScreen 側で systemBarsPadding() を使う。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Consent → MobileAds.initialize → first ad request.
        //
        // We gate MobileAds.initialize behind ConsentManager so that the
        // SDK's first network request happens with the user's consent state
        // already known. This is what AdMob's documentation recommends and
        // what is now policy-required for EU/EEA/UK users; outside those
        // regions UMP no-ops and the callback fires immediately.
        ConsentManager.requestConsentIfNeeded(this) {
            if (mobileAdsInitialised.compareAndSet(false, true)) {
                MobileAds.initialize(this) { /* init complete */ }
            }
        }

        setContent {
            MisattitudeTheme {
                MainScreen()
            }
        }
    }
}
