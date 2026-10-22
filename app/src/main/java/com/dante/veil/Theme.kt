package com.dante.veil

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun VeilTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(
            primary = Color(0xFFB4A7FF),
            secondary = Color(0xFFFF8AD8),
            tertiary = Color(0xFF67E8F9)
        )
        else -> lightColorScheme(
            primary = Color(0xFF5B3FE0),
            secondary = Color(0xFFC2186B),
            tertiary = Color(0xFF0891B2)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
