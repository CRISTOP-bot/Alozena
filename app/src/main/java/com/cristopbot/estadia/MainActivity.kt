package com.cristopbot.estadia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = getSharedPreferences("estadia_preferences", MODE_PRIVATE)
        setContent {
            var textScale by remember {
                mutableFloatStateOf(settings.getFloat("text_scale", 1f).coerceIn(0.9f, 1.15f))
            }
            AlozenaTheme(textScale) {
                AlozenaApp(
                    textScale = textScale,
                    onTextScaleChange = { scale ->
                        textScale = scale.coerceIn(0.9f, 1.15f)
                        settings.edit().putFloat("text_scale", textScale).apply()
                    }
                )
            }
        }
    }
}
