package io.github.takarakasai.misattitude

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        super.onCreate(savedInstanceState)
        setContent {
            MisattitudeTheme {
                MainScreen()
            }
        }
    }
}
