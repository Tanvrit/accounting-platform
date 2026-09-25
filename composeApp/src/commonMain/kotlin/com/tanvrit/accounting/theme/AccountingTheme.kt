package com.tanvrit.accounting.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.tanvrit.ui.locale.UiStrings
import com.tanvrit.ui.theme.TanvritTheme
import com.tanvrit.ui.theme.TanvritThemeMode

/**
 * Accounting brand palette — deep ledger green primary, slate secondary,
 * amber accents. These are the ONLY raw colors in the app; every screen reads
 * `MaterialTheme.colorScheme.*` / `TanvritDesignSystem.*` semantic tokens.
 */
private val AccountingLightScheme =
    lightColorScheme(
        primary = Color(0xFF14532D),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD9E8DC),
        onPrimaryContainer = Color(0xFF0B3A20),
        secondary = Color(0xFF155E75),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD2E7EE),
        onSecondaryContainer = Color(0xFF0B3A47),
        tertiary = Color(0xFF92400E),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF3E0CB),
        onTertiaryContainer = Color(0xFF4A2405),
        background = Color(0xFFFCFCFC),
        onBackground = Color(0xFF1C1B1F),
        surface = Color(0xFFFCFCFC),
        onSurface = Color(0xFF1C1B1F),
        surfaceVariant = Color(0xFFF0F0F0),
        onSurfaceVariant = Color(0xFF49454F),
        outline = Color(0xFFCAC4D0),
        outlineVariant = Color(0xFFE0E0E0),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    )

private val AccountingDarkScheme =
    darkColorScheme(
        primary = Color(0xFF8FD6A4),
        onPrimary = Color(0xFF0C3720),
        primaryContainer = Color(0xFF1E5533),
        onPrimaryContainer = Color(0xFFE2F3E8),
        secondary = Color(0xFF9AD1DF),
        onSecondary = Color(0xFF112F3A),
        secondaryContainer = Color(0xFF265060),
        onSecondaryContainer = Color(0xFFE3F2F6),
        tertiary = Color(0xFFF2C294),
        onTertiary = Color(0xFF4A2405),
        tertiaryContainer = Color(0xFF6B4114),
        onTertiaryContainer = Color(0xFFFBEBD9),
        background = Color(0xFF16181D),
        onBackground = Color(0xFFE6E1E5),
        surface = Color(0xFF16181D),
        onSurface = Color(0xFFE6E1E5),
        surfaceVariant = Color(0xFF2A2D34),
        onSurfaceVariant = Color(0xFFCAC4D0),
        outline = Color(0xFF938F99),
        outlineVariant = Color(0xFF3A3D45),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    )

/**
 * AccountingTheme — nests the app's [MaterialTheme] inside the SDK's
 * [TanvritTheme] so SDK CompositionLocals (TanvritDesignSystem spacing /
 * shapes / elevation / blur / icons / haptics, `Modifier.tanvritPress`, glass)
 * keep working while the accounting color scheme drives `colorScheme`.
 *
 * Dark/light follows the persisted `TanvritTheme` mode (set from
 * Settings → Appearance), hydrated once on first composition.
 */
@Composable
fun AccountingTheme(content: @Composable () -> Unit) {
    remember {
        TanvritTheme.load()
        // Register the SDK :ui string bundle — without it SDK strings render
        // as raw keys (e.g. "picker_hour"). Idempotent.
        UiStrings.ensureRegistered()
        true
    }

    val mode by TanvritTheme.mode.collectAsState()
    val systemInDarkTheme = isSystemInDarkTheme()
    val darkTheme =
        when (mode) {
            TanvritThemeMode.DARK, TanvritThemeMode.BLACK -> true
            TanvritThemeMode.LIGHT -> false
            TanvritThemeMode.SYSTEM -> systemInDarkTheme
        }

    TanvritTheme(darkTheme = darkTheme, useBlack = mode == TanvritThemeMode.BLACK) {
        MaterialTheme(
            colorScheme = if (darkTheme) AccountingDarkScheme else AccountingLightScheme,
            content = content,
        )
    }
}
