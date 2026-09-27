package com.tanvrit.accounting.screens.itcWorkspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.tanvrit.accounting.screens.common.ChipTone
import com.tanvrit.accounting.screens.common.ErrorBanner
import com.tanvrit.accounting.screens.common.LoadingPane
import com.tanvrit.accounting.screens.common.MoneyText
import com.tanvrit.accounting.screens.common.ScreenHeader
import com.tanvrit.accounting.screens.common.StatusChip
import com.tanvrit.core.feature.money.Money
import com.tanvrit.ui.component.lifecycle.rememberViewModel
import com.tanvrit.ui.component.premium.PremiumEmptyState
import com.tanvrit.ui.theme.TanvritDesignSystem

/** ITC workspace (roadmap #8) — manual GSTR-2B paste + client-side match vs purchase register. */
@Composable
fun ItcWorkspaceScreen() {
    val viewModel = rememberViewModel { ItcWorkspaceViewModel() }
    val state by viewModel.state.collectAsState()
    val spacing = TanvritDesignSystem.spacing
    val clipboard = LocalClipboardManager.current

    Column(modifier = Modifier.fillMaxSize().padding(spacing.lg)) {
        ScreenHeader(
            title = "ITC workspace · GSTR-2B recon",
            subtitle = "Manual import — portal pull is server-tracked (roadmap #8)",
            actions = {
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(viewModel.mismatchesCsv())) },
                    enabled = state.results.isNotEmpty(),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(spacing.xs))
                    Text("Copy mismatches CSV")
                }
            },
        )

        state.error?.let {
            ErrorBanner(message = it, onRetry = { viewModel.clearError() })
            Spacer(Modifier.height(spacing.sm))
        }
        state.notice?.let {
            StatusChip(label = it, tone = ChipTone.Info)
            Spacer(Modifier.height(spacing.sm))
        }

        when {
            state.businessId.isBlank() ->
                PremiumEmptyState(
                    icon = Icons.Outlined.FactCheck,
                    title = "No business selected",
                    description = "Pick a business to reconcile its input tax credit.",
                )
            else -> {
                ItcImportCard(state = state, onPaste = viewModel::setPaste, onMatch = viewModel::importAndMatch)
                Spacer(Modifier.height(spacing.md))
                if (state.isLoading) {
                    LoadingPane(Modifier.weight(1f))
                } else if (state.results.isNotEmpty()) {
                    ItcResultBody(state = state, onToggleBucket = viewModel::toggleBucket)
                }
            }
        }
    }
}

@Composable
private fun ItcImportCard(
    state: ItcWorkspaceUiState,
    onPaste: (String) -> Unit,
    onMatch: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    Surface(
        shape = TanvritDesignSystem.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = TanvritDesignSystem.elevation.e1,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Paste the GSTR-2B export (offline-tool JSON or CSV)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (state.businessGstin.isNotBlank()) {
                    StatusChip(
                        label = "Books GSTIN: ${state.businessGstin}",
                        tone = ChipTone.Neutral,
                    )
                }
            }
            Spacer(Modifier.height(spacing.sm))
            OutlinedTextField(
                value = state.pasteText,
                onValueChange = onPaste,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 10,
                placeholder = {
                    Text(
                        "{\"data\":{\"docdata\":{\"b2b\":[…]}}} — or a CSV with supplier GSTIN, invoice number, taxable, IGST, CGST, SGST columns",
                    )
                },
            )
            Spacer(Modifier.height(spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                state.detectedFormat?.let { format ->
                    StatusChip(label = "Detected: $format · ${state.parsedRows} rows", tone = ChipTone.Info)
                    Spacer(Modifier.width(spacing.sm))
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onMatch, enabled = state.pasteText.isNotBlank()) {
                    Icon(Icons.Outlined.Upload, contentDescription = null)
                    Spacer(Modifier.width(spacing.xs))
                    Text("Import & match")
                }
            }
            state.parseErrors.takeIf { it.isNotEmpty() }?.let { errors ->
                Spacer(Modifier.height(spacing.xs))
                errors.take(3).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (errors.size > 3) {
                    Text(
                        "+${errors.size - 3} more parse issues",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ItcResultBody(
    state: ItcWorkspaceUiState,
    onToggleBucket: (ItcBucket) -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    val summary = ItcMatcher.summarize(state.results)
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
        ItcBucket.entries.forEach { bucket ->
            val s = summary[bucket]
            FilterChip(
                selected = bucket in state.bucketFilter,
                onClick = { onToggleBucket(bucket) },
                label = { Text("${bucket.label}${s?.let { " · ${it.first}" } ?: ""}") },
            )
        }
    }
    Spacer(Modifier.height(spacing.sm))

    LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        items(
            state.results.filter { it.bucket in state.bucketFilter },
            key = { it.key + it.supplierLabel + (it.voucher?.id ?: "") },
        ) { row ->
            ItcResultRow(row)
        }
    }
}

@Composable
private fun ItcResultRow(row: ItcMatchRow) {
    val spacing = TanvritDesignSystem.spacing
    Surface(
        shape = TanvritDesignSystem.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = TanvritDesignSystem.elevation.e0,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(
                        label = row.bucket.label,
                        tone =
                            when (row.bucket) {
                                ItcBucket.MATCHED -> ChipTone.Success
                                ItcBucket.MISSING_IN_BOOKS -> ChipTone.Error
                                ItcBucket.AMOUNT_MISMATCH, ItcBucket.DUPLICATE_IN_BOOKS -> ChipTone.Warning
                                ItcBucket.BOOKS_ONLY -> ChipTone.Neutral
                            },
                    )
                    Spacer(Modifier.width(spacing.xs))
                    Text(
                        row.supplierLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(spacing.xxs))
                Text(
                    buildString {
                        append(
                            row.row?.invoiceNumber ?: row.voucher
                                ?.referenceId
                                .orEmpty()
                                .ifBlank { row.voucher?.voucherNumber.orEmpty() },
                        )
                        val date = row.row?.invoiceDate ?: row.voucher?.date.orEmpty()
                        if (date.isNotBlank()) append(" · ").append(date)
                        row.voucher
                            ?.id
                            ?.takeIf { it.isNotBlank() }
                            ?.let { append(" · voucher in books") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("2B / books ITC", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    MoneyText(money = moneyOfMinor(row.twoBTaxMinorUnits))
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(money = moneyOfMinor(row.booksTaxMinorUnits))
                    if (row.diffMinorUnits != 0L) {
                        MoneyText(money = moneyOfMinor(row.diffMinorUnits), bold = true, colorizeSign = true)
                    }
                }
            }
        }
    }
}

private fun moneyOfMinor(minorUnits: Long): Money = Money.fromDouble(minorUnits / 100.0)
