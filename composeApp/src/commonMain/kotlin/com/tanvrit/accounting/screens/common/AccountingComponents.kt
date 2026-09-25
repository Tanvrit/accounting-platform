package com.tanvrit.accounting.screens.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tanvrit.core.feature.money.Money
import com.tanvrit.core.feature.money.MoneyFormatter
import com.tanvrit.ui.theme.TanvritDesignSystem

/** Money display — ISO-exponent-aware (lakh/crore grouping for INR). */
fun formatMoney(
    money: Money,
    currencyCode: String = "INR",
): String = MoneyFormatter.format(money, currencyCode)

/** Parses a user-typed amount into [Money] minor units; blank/invalid → ZERO. */
fun parseMoneyInput(raw: String): Money = Money.fromDouble(raw.trim().replace(",", "").toDoubleOrNull() ?: 0.0)

/** `String.format` is not available in Kotlin common — decimal helpers instead. */
fun twoDecimals(value: Double): String {
    val rounded = kotlin.math.round(value * 100) / 100
    val whole = rounded.toLong()
    val fraction =
        kotlin.math
            .round((rounded - whole) * 100)
            .toLong()
            .let { if (it < 0) -it else it }
    return "$whole.${fraction.toString().padStart(2, '0')}"
}

/** Signed one-decimal percentage, e.g. "+4.2%" / "-1.0%". */
fun signedPercent(value: Double): String {
    val sign = if (value >= 0) "+" else ""
    return "$sign${twoDecimals(value).dropLast(1)}%"
}

/** Signed money text — positive in `colors.success`, negative in `colorScheme.error`. */
@Composable
fun MoneyText(
    money: Money,
    currencyCode: String = "INR",
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    colorizeSign: Boolean = false,
) {
    val colors = TanvritDesignSystem.colors
    val scheme = MaterialTheme.colorScheme
    val color =
        if (colorizeSign) {
            when {
                money < Money.ZERO -> scheme.error
                money > Money.ZERO -> colors.success
                else -> scheme.onSurface
            }
        } else {
            scheme.onSurface
        }
    Text(
        text = formatMoney(money, currencyCode),
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
    )
}

enum class ChipTone { Neutral, Success, Warning, Error, Info, Primary }

/** Compact status pill used across lists (POSTED / DRAFT / OPEN / …). */
@Composable
fun StatusChip(
    label: String,
    tone: ChipTone = ChipTone.Neutral,
    modifier: Modifier = Modifier,
) {
    val colors = TanvritDesignSystem.colors
    val scheme = MaterialTheme.colorScheme
    val (container, content) =
        when (tone) {
            ChipTone.Neutral -> scheme.surfaceVariant to scheme.onSurfaceVariant
            ChipTone.Success -> colors.successContainer to colors.success
            ChipTone.Warning -> colors.warningContainer to colors.warning
            ChipTone.Error -> scheme.errorContainer to scheme.error
            ChipTone.Info -> scheme.secondaryContainer to scheme.secondary
            ChipTone.Primary -> scheme.primaryContainer to scheme.primary
        }
    Surface(
        modifier = modifier,
        shape = TanvritDesignSystem.shapes.pill,
        color = container,
    ) {
        Text(
            text = label,
            modifier =
                Modifier.padding(
                    horizontal = TanvritDesignSystem.spacing.sm,
                    vertical = TanvritDesignSystem.spacing.xxs,
                ),
            color = content,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Full-width centered loading state. */
@Composable
fun LoadingPane(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Inline, non-fatal error surface with an optional retry. */
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val spacing = TanvritDesignSystem.spacing
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = TanvritDesignSystem.shapes.medium,
        color = scheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                color = scheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                Button(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

/** Consistent screen header: title + optional subtitle + trailing actions. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    val spacing = TanvritDesignSystem.spacing
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = spacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (actions != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) { actions() }
        }
    }
}

/**
 * Token-driven picker: a read-only-looking button + anchored DropdownMenu.
 * Deliberately avoids ExposedDropdownMenuBox so the API surface stays stable
 * across Compose Material3 alpha lines.
 */
@Composable
fun <T> DropdownPickerField(
    label: String,
    options: List<T>,
    selected: T?,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionLabel: (T) -> String = { it.toString() },
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = TanvritDesignSystem.shapes.medium,
        ) {
            Text(
                text = "$label: ${selected?.let(optionLabel) ?: "Select"}",
                maxLines = 1,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** AlertDialog wrapper kept to one call-site shape across screens. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            if (destructive) {
                Button(
                    onClick = onConfirm,
                    colors =
                        androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                ) { Text(confirmLabel) }
            } else {
                Button(onClick = onConfirm) { Text(confirmLabel) }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
