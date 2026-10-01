package com.tanvrit.accounting.screens.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tanvrit.ui.component.premium.GlassSheet
import com.tanvrit.ui.theme.TanvritDesignSystem

/**
 * The "?" cheat sheet — lists every registered shortcut grouped by section.
 * Opened from the global keyboard layer (SHIFT+"/" or CTRL+K).
 *
 * [title]/[subtitle] default to English; the nav shell passes the localized
 * `cheat.title` / `cheat.subtitle` strings (roadmap #15). The shortcut rows
 * themselves stay English in v1.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun KeyboardCheatSheetSheet(
    registry: ShortcutRegistry,
    onDismiss: () -> Unit,
    title: String = "Keyboard shortcuts",
    subtitle: String = "Tally-style speed entry. Desktop and web only (hardware keyboard).",
) {
    val spacing = TanvritDesignSystem.spacing
    GlassSheet(onDismiss = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs), modifier = Modifier.verticalScroll(rememberScrollState())) {
            registry.groups().forEach { (group, entries) ->
                Text(
                    text = group.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                entries.forEach { shortcut ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xxs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = shortcut.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = shortcut.chord,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(spacing.xs))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(spacing.xs))
            }
        }
    }
}
