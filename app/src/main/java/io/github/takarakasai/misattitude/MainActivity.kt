package io.github.takarakasai.misattitude

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.google.android.filament.utils.Utils
import com.google.android.gms.ads.MobileAds
import io.github.takarakasai.misattitude.ui.MainScreen
import io.github.takarakasai.misattitude.ui.theme.MisattitudeTheme

class MainActivity : ComponentActivity() {

    companion object {
        init {
            // Loads the Filament native libraries. Must run before any Filament call.
            Utils.init()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15 (API 35) は edge-to-edge をデフォルト強制するため、
        // 明示的に enableEdgeToEdge() を呼んで挙動を予測可能にする。
        // 実コンテンツへのインセット適用は MainScreen 側で systemBarsPadding() を使う。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Google Mobile Ads SDK initialisation.
        //
        // Why here in MainActivity.onCreate rather than a custom Application
        // subclass: the SDK supports lazy init from any Context as long as it
        // runs before the first AdView load. Doing it here avoids adding an
        // Application class to the manifest for one line of code, and keeps
        // "all third-party SDK init" visible in one place. The callback is
        // intentionally a no-op — we have no mediation adapters to inspect.
        MobileAds.initialize(this) { /* init complete */ }

        setContent {
            MisattitudeTheme {
                MainScreen()
            }
        }
    }
}
