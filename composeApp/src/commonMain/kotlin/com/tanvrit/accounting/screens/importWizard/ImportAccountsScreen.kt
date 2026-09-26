package com.tanvrit.accounting.screens.importWizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tanvrit.accounting.screens.common.ChipTone
import com.tanvrit.accounting.screens.common.ConfirmDialog
import com.tanvrit.accounting.screens.common.ErrorBanner
import com.tanvrit.accounting.screens.common.LoadingPane
import com.tanvrit.accounting.screens.common.MoneyText
import com.tanvrit.accounting.screens.common.ScreenHeader
import com.tanvrit.accounting.screens.common.StatusChip
import com.tanvrit.core.feature.money.Money
import com.tanvrit.ui.component.lifecycle.rememberViewModel
import com.tanvrit.ui.component.premium.PremiumEmptyState
import com.tanvrit.ui.component.premium.PremiumListRow
import com.tanvrit.ui.theme.TanvritDesignSystem

/** Import wizard — paste a chart of accounts (with opening balances) from Tally/Excel. */
@Composable
fun ImportAccountsScreen(onBack: () -> Unit = {}) {
    val viewModel = rememberViewModel { ImportAccountsViewModel() }
    val state by viewModel.state.collectAsState()
    val spacing = TanvritDesignSystem.spacing

    Column(modifier = Modifier.fillMaxSize().padding(spacing.lg)) {
        ScreenHeader(
            title = "Import accounts",
            subtitle =
                when (state.stage) {
                    ImportStage.UPLOAD -> "Paste CSV or TSV from Tally / Excel"
                    ImportStage.PREVIEW -> "Review before anything is created"
                    ImportStage.IMPORTING -> "Sending to the server…"
                    ImportStage.DONE -> "Import finished"
                },
            actions = {
                OutlinedButton(onClick = onBack) { Text("Back") }
            },
        )

        state.error?.let {
            ErrorBanner(message = it, onRetry = { viewModel.clearError() })
            Spacer(Modifier.height(spacing.lg))
        }

        when (state.stage) {
            ImportStage.UPLOAD -> UploadStep(state, viewModel)
            ImportStage.PREVIEW -> PreviewStep(state, viewModel)
            ImportStage.IMPORTING -> ImportingStep(state)
            ImportStage.DONE -> DoneStep(state, viewModel)
        }
    }

    if (state.confirmVisible) {
        val sendCount = if (state.skipInvalid) state.parsed?.validRows?.size ?: 0 else state.parsed?.rows?.size ?: 0
        ConfirmDialog(
            title = "Import accounts",
            message =
                "Create $sendCount accounts in this business? " +
                    "Codes that already exist are rejected by the server.",
            confirmLabel = "Import",
            onConfirm = { viewModel.runImport() },
            onDismiss = { viewModel.hideConfirm() },
        )
    }
}

@Composable
private fun UploadStep(
    state: ImportAccountsUiState,
    viewModel: ImportAccountsViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(
            "Paste chart-of-accounts rows. Columns (header optional, case-insensitive): " +
                "name, code, type or Tally group (e.g. \"Sundry Debtors\"), parent code, " +
                "opening balance, side (DR/CR), narration.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.rawText,
            onValueChange = { viewModel.onTextChange(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Accounts CSV / TSV") },
            minLines = 6,
            maxLines = 12,
        )
        state.parsed?.let { parsed ->
            if (parsed.rows.isEmpty()) {
                Text(
                    "Nothing to parse yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    StatusChip(label = parsed.delimiterLabel, tone = ChipTone.Info)
                    StatusChip(label = "${parsed.rows.size} rows", tone = ChipTone.Neutral)
                    StatusChip(label = "${parsed.validRows.size} OK", tone = ChipTone.Success)
                    if (parsed.invalidCount > 0) {
                        StatusChip(label = "${parsed.invalidCount} invalid", tone = ChipTone.Error)
                    }
                }
            }
        }
        Row {
            Button(
                onClick = { viewModel.toPreview() },
                enabled = state.parsed?.validRows?.isNotEmpty() == true,
            ) { Text("Preview") }
        }
    }
}

@Composable
private fun PreviewStep(
    state: ImportAccountsUiState,
    viewModel: ImportAccountsViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    val parsed = state.parsed ?: return
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.skipInvalid,
                onClick = { viewModel.toggleSkipInvalid() },
                label = { Text("Skip invalid rows") },
            )
            if (!state.skipInvalid && parsed.invalidCount > 0) {
                Text(
                    "${parsed.invalidCount} rows have errors — import will be blocked until fixed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            OutlinedButton(onClick = { viewModel.backToUpload() }) { Text("Back") }
            Button(
                onClick = { viewModel.showConfirm() },
                enabled = parsed.validRows.isNotEmpty(),
            ) { Text("Import ${parsed.validRows.size} accounts") }
        }
        Spacer(Modifier.height(spacing.md))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            items(parsed.rows, key = { it.rowNumber }) { row ->
                ImportPreviewRow(row)
            }
        }
    }
}

@Composable
private fun ImportPreviewRow(row: AccountImportRow) {
    val title =
        listOf(row.code, row.name)
            .filter { it.isNotBlank() }
            .joinToString(" — ")
            .ifBlank { "(row ${row.rowNumber})" }
    val subtitle =
        buildList {
            add(row.type.code)
            row.side?.let { add(it.name) }
            if (row.parentCode.isNotBlank()) add("parent ${row.parentCode}")
            if (row.narration.isNotBlank()) add(row.narration)
        }.joinToString(" · ")
    PremiumListRow(
        title = title,
        subtitle = subtitle,
        leading = {
            StatusChip(
                label = if (row.isValid) "OK" else row.errors.first(),
                tone = if (row.isValid) ChipTone.Success else ChipTone.Error,
            )
        },
        trailing = {
            if (row.opening != Money.ZERO) {
                MoneyText(money = row.opening, bold = true, colorizeSign = true)
            }
        },
        isStandalone = false,
    )
}

@Composable
private fun ImportingStep(state: ImportAccountsUiState) {
    val spacing = TanvritDesignSystem.spacing
    Column(modifier = Modifier.fillMaxSize()) {
        LoadingPane(Modifier.weight(1f))
        Text(
            "Importing chunk ${state.chunksDone + 1} of ${state.chunksTotal}…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = spacing.lg),
        )
    }
}

@Composable
private fun DoneStep(
    state: ImportAccountsUiState,
    viewModel: ImportAccountsViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            StatusChip(label = "${state.importedCount} imported", tone = ChipTone.Success)
            if (state.skippedInvalidCount > 0) {
                StatusChip(label = "${state.skippedInvalidCount} skipped", tone = ChipTone.Warning)
            }
            if (state.failedCount > 0) {
                StatusChip(label = "${state.failedCount} failed", tone = ChipTone.Error)
            }
            StatusChip(label = "${state.sendCount} sent", tone = ChipTone.Neutral)
        }
        state.openingNote?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.messages.forEach { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (message.startsWith("Chunk failed") || message.startsWith("Row ")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        if (state.importedCount == 0 && state.failedCount == 0 && state.messages.isEmpty()) {
            PremiumEmptyState(
                icon = Icons.Outlined.Upload,
                title = "Nothing imported",
                description = "No account rows were sent. Go back and paste a chart of accounts.",
            )
        }
        Text(
            "Open Chart of Accounts and refresh to see the imported accounts.",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row {
            OutlinedButton(onClick = { viewModel.startOver() }) { Text("Import more") }
        }
    }
}
