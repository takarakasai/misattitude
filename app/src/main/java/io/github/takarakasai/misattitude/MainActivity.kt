package io.github.takarakasai.misattitude

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.takarakasai.misattitude.ui.MainScreen
import io.github.takarakasai.misattitude.ui.theme.MisattitudeTheme
import com.google.android.filament.utils.Utils

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
        setContent {
            MisattitudeTheme {
                MainScreen()
            }
        }
    }
}
