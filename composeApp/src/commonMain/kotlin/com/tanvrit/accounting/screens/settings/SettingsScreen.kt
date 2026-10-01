package com.tanvrit.accounting.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.tanvrit.accounting.data.NumberingSeries
import com.tanvrit.accounting.data.VoucherNumbering
import com.tanvrit.accounting.screens.common.ChipTone
import com.tanvrit.accounting.screens.common.DropdownPickerField
import com.tanvrit.accounting.screens.common.ScreenHeader
import com.tanvrit.accounting.screens.common.StatusChip
import com.tanvrit.accounting.screens.i18n.AppLanguage
import com.tanvrit.core.feature.accounting.model.VoucherType
import com.tanvrit.ui.component.lifecycle.rememberViewModel
import com.tanvrit.ui.theme.TanvritDesignSystem
import com.tanvrit.ui.theme.TanvritThemeMode

/** System Settings — numbering series, registrations, defaults, appearance. */
@Composable
fun SettingsScreen() {
    val viewModel = rememberViewModel { SettingsViewModel() }
    val state by viewModel.state.collectAsState()
    val spacing = TanvritDesignSystem.spacing

    LazyColumn(modifier = Modifier.fillMaxSize().padding(spacing.lg)) {
        item {
            ScreenHeader(title = "System Settings", subtitle = "Numbering, registrations, defaults, appearance")
        }
        item {
            if (state.saved) {
                StatusChip(label = "Settings saved", tone = ChipTone.Success)
                Spacer(Modifier.height(spacing.sm))
            }
        }

        item { SectionTitle("Appearance") }
        item {
            SettingsCard {
                Text("Theme", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    TanvritThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.titlecase() }) },
                        )
                    }
                }
                // Roadmap #15 — v1 localizes the nav bar + cheat sheet header;
                // screen bodies stay English for now.
                DropdownPickerField(
                    label = "Language",
                    options = AppLanguage.entries,
                    selected = state.language,
                    onSelected = { viewModel.setLanguage(it) },
                    modifier = Modifier.fillMaxWidth(),
                    optionLabel = { it.label },
                )
            }
        }

        item { SectionTitle("Company registrations") }
        item {
            SettingsCard {
                OutlinedTextField(
                    value = state.settings.gstin,
                    onValueChange = { v -> viewModel.update { it.copy(gstin = v.uppercase()) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("GSTIN") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.settings.tan,
                    onValueChange = { v -> viewModel.update { it.copy(tan = v.uppercase()) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("TAN") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.settings.baseCurrency,
                    onValueChange = { v -> viewModel.update { it.copy(baseCurrency = v.uppercase()) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base currency (ISO 4217)") },
                    singleLine = true,
                )
            }
        }

        item { SectionTitle("Numbering series") }
        item {
            SettingsCard {
                NumberingField("Sales invoice prefix", state.settings.salesPrefix) { v ->
                    viewModel.update { it.copy(salesPrefix = v) }
                }
                NumberingField("Purchase bill prefix", state.settings.purchasePrefix) { v ->
                    viewModel.update { it.copy(purchasePrefix = v) }
                }
                NumberingField("Receipt prefix", state.settings.receiptPrefix) { v ->
                    viewModel.update { it.copy(receiptPrefix = v) }
                }
                NumberingField("Payment prefix", state.settings.paymentPrefix) { v ->
                    viewModel.update { it.copy(paymentPrefix = v) }
                }
                NumberingField("Journal prefix", state.settings.journalPrefix) { v ->
                    viewModel.update { it.copy(journalPrefix = v) }
                }
            }
        }

        item { SectionTitle("Voucher numbering") }
        item {
            SettingsCard {
                Text(
                    "Per voucher type, for this business. The next number is auto-filled on a new " +
                        "voucher and consumed when it is saved. These defaults seed from the " +
                        "prefixes above until a series is edited here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VoucherNumbering.editableTypes.forEach { type ->
                    NumberingSeriesRow(
                        type = type,
                        series = state.numberingFor(type),
                        onChange = { transform -> viewModel.updateNumbering(type, transform) },
                    )
                }
            }
        }

        item { SectionTitle("Compliance automation") }
        item {
            SettingsCard {
                ToggleRow(
                    label = "E-invoice (IRN) enabled",
                    hint = "Generate IRN + signed QR on posted sales vouchers",
                    checked = state.settings.einvoiceEnabled,
                ) { checked -> viewModel.update { it.copy(einvoiceEnabled = checked) } }
                ToggleRow(
                    label = "E-way bill enabled",
                    hint = "Generate EWB for goods movement on posted vouchers",
                    checked = state.settings.ewayBillEnabled,
                ) { checked -> viewModel.update { it.copy(ewayBillEnabled = checked) } }
                ToggleRow(
                    label = "Auto-fetch FX rates",
                    hint = "Pull RBI reference rates daily for non-base currencies",
                    checked = state.settings.autoFetchFxRates,
                ) { checked -> viewModel.update { it.copy(autoFetchFxRates = checked) } }
            }
        }

        item { SectionTitle("About") }
        item {
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Tanvrit Accounting", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Version ${com.tanvrit.accounting.BuildIdentity.VERSION_NAME} · " +
                                "SDK ${com.tanvrit.accounting.BuildIdentity.SDK_VERSION}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = TanvritDesignSystem.colors.success,
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(spacing.lg))
            Button(onClick = { viewModel.markSaved() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            Spacer(Modifier.height(spacing.xxl))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = TanvritDesignSystem.spacing.sm),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val spacing = TanvritDesignSystem.spacing
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = spacing.lg),
        shape = TanvritDesignSystem.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = TanvritDesignSystem.elevation.e1,
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) { content() }
    }
}

@Composable
private fun NumberingField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
    )
}

/** One voucher type's numbering series: prefix / next number / padding width / suffix + live preview. */
@Composable
private fun NumberingSeriesRow(
    type: VoucherType,
    series: NumberingSeries,
    onChange: ((NumberingSeries) -> NumberingSeries) -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(type.code, style = MaterialTheme.typography.labelLarge)
        Text(
            "Next: ${VoucherNumbering.format(series)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        OutlinedTextField(
            value = series.prefix,
            onValueChange = { v -> onChange { it.copy(prefix = v.trim().uppercase()) } },
            modifier = Modifier.weight(1.2f),
            label = { Text("Prefix") },
            singleLine = true,
        )
        OutlinedTextField(
            value = series.nextNumber.toString(),
            onValueChange = { v ->
                v.filter(Char::isDigit).toIntOrNull()?.let { n -> onChange { it.copy(nextNumber = n) } }
            },
            modifier = Modifier.weight(1f),
            label = { Text("Next #") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        OutlinedTextField(
            value = series.width.toString(),
            onValueChange = { v ->
                v.filter(Char::isDigit).toIntOrNull()?.let { n -> onChange { it.copy(width = n) } }
            },
            modifier = Modifier.weight(0.9f),
            label = { Text("Pad width") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        OutlinedTextField(
            value = series.suffix,
            onValueChange = { v -> onChange { it.copy(suffix = v.trim().uppercase()) } },
            modifier = Modifier.weight(1f),
            label = { Text("Suffix") },
            singleLine = true,
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    hint: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
