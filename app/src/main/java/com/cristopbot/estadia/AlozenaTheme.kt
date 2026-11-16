package com.cristopbot.estadia

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color

internal val Coral = Color(0xFF2F5B73)
internal val Ink = Color(0xFF172233)
internal val Muted = Color(0xFF5F6E81)
internal val Soft = Color(0xFFF4F6F8)
internal val BlueForUi = Color(0xFF2F5B73)
internal val SubtleCard = Color(0xFFF7F9FB)
internal val BorderSoft = Color(0xFFE3E8EE)

@Composable
internal fun AlozenaTheme(textScale: Float, content: @Composable () -> Unit) {
    val systemDensity = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides systemDensity.copy(fontScale = systemDensity.fontScale * textScale)) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Coral,
                onPrimary = Color.White,
                background = Color(0xFFF7F8FA),
                surface = Color.White,
                onSurface = Ink,
                secondary = Color(0xFF6F8BA4)
            ),
            content = content
        )
    }
}

