package com.tanvrit.accounting.screens.reconciliation

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
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tanvrit.accounting.screens.common.ChipTone
import com.tanvrit.accounting.screens.common.DropdownPickerField
import com.tanvrit.accounting.screens.common.ErrorBanner
import com.tanvrit.accounting.screens.common.LoadingPane
import com.tanvrit.accounting.screens.common.MoneyText
import com.tanvrit.accounting.screens.common.ScreenHeader
import com.tanvrit.accounting.screens.common.StatusChip
import com.tanvrit.accounting.screens.common.formatMoney
import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.ReconciliationStatus
import com.tanvrit.core.feature.accounting.model.UnmatchedItem
import com.tanvrit.ui.component.lifecycle.rememberViewModel
import com.tanvrit.ui.component.premium.PremiumEmptyState
import com.tanvrit.ui.component.premium.PremiumListRow
import com.tanvrit.ui.theme.TanvritDesignSystem

/** Bank Reconciliation — Import → Match → Review → Complete, plus history. */
@Composable
fun ReconciliationScreen() {
    val viewModel = rememberViewModel { ReconciliationViewModel() }
    val state by viewModel.state.collectAsState()
    val spacing = TanvritDesignSystem.spacing

    Column(modifier = Modifier.fillMaxSize().padding(spacing.lg)) {
        ScreenHeader(
            title = "Bank Reconciliation",
            subtitle = if (state.sessionId.isBlank()) "Import a statement to begin" else "Session ${state.sessionId}",
        )

        state.error?.let {
            ErrorBanner(message = it, onRetry = { viewModel.clearMessages() })
            Spacer(Modifier.height(spacing.sm))
        }
        state.notice?.let {
            StatusChip(label = it, tone = ChipTone.Info)
            Spacer(Modifier.height(spacing.sm))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            DropdownPickerField(
                label = "Bank account",
                options = state.bankAccounts,
                selected = state.bankAccounts.firstOrNull { it.id == state.bankAccountId },
                onSelected = { account: Account -> viewModel.selectBankAccount(account.id) },
                modifier = Modifier.weight(1f),
                optionLabel = { "${it.accountCode} — ${it.name}" },
            )
        }
        Spacer(Modifier.height(spacing.md))

        TabRow(selectedTabIndex = state.activeTab.ordinal) {
            ReconciliationTab.entries.forEach { tab ->
                Tab(
                    selected = state.activeTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = { Text(tab.label) },
                )
            }
        }
        Spacer(Modifier.height(spacing.lg))

        if (state.isLoading) {
            LoadingPane(Modifier.weight(1f))
        } else {
            when (state.activeTab) {
                ReconciliationTab.IMPORT -> ImportTab(state, viewModel)
                ReconciliationTab.MATCH -> MatchTab(state, viewModel)
                ReconciliationTab.REVIEW -> ReviewTab(state, viewModel)
                ReconciliationTab.HISTORY -> HistoryTab(state)
            }
        }
    }
}

@Composable
private fun ImportTab(
    state: ReconciliationUiState,
    viewModel: ReconciliationViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(
            "Paste the bank statement CSV. Columns (header optional): date, narration, amount, balance, reference, type.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.csvInput,
            onValueChange = { viewModel.setCsvInput(it) },
            modifier = Modifier.fillMaxWidth().height(TanvritDesignSystem.spacing.massive * 3),
            label = { Text("Statement CSV") },
        )
        state.parseErrors.forEach { parseError ->
            Text(parseError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = { viewModel.importStatement() },
            enabled = state.csvInput.isNotBlank() && state.bankAccountId.isNotBlank(),
        ) { Text("Import statement") }
        Spacer(Modifier.height(spacing.lg))
    }
}

@Composable
private fun MatchTab(
    state: ReconciliationUiState,
    viewModel: ReconciliationViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    val match = state.matchResult
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Button(onClick = { viewModel.autoMatch() }, enabled = state.sessionId.isNotBlank()) { Text("Auto-match") }
            OutlinedButton(
                onClick = { viewModel.manualMatch() },
                enabled = state.selectedStatementItemId.isNotBlank() && state.selectedBookItemId.isNotBlank(),
            ) { Text("Match selected pair") }
        }

        if (match == null) {
            PremiumEmptyState(
                icon = Icons.Outlined.Sync,
                title = "No matches yet",
                description = "Run auto-match, or select one statement item and one book item below, then \"Match selected pair\".",
            )
            return@Column
        }

        Text("Unmatched statement items", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (match.unmatchedStatement.isEmpty()) {
            Text("None", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        match.unmatchedStatement.forEach { item ->
            UnmatchedRow(
                item = item,
                selected = state.selectedStatementItemId == item.itemId,
                onClick = { viewModel.selectStatementItem(item.itemId) },
                side = "Statement",
            )
        }
        Spacer(Modifier.height(spacing.sm))
        Text("Unmatched book items", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (match.unmatchedBook.isEmpty()) {
            Text("None", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        match.unmatchedBook.forEach { item ->
            UnmatchedRow(
                item = item,
                selected = state.selectedBookItemId == item.itemId,
                onClick = { viewModel.selectBookItem(item.itemId) },
                side = "Book",
            )
        }
    }
}

@Composable
private fun UnmatchedRow(
    item: UnmatchedItem,
    selected: Boolean,
    onClick: () -> Unit,
    side: String,
) {
    PremiumListRow(
        title = item.narration.ifBlank { item.reference },
        subtitle = "$side · ${item.date}",
        leading = {
            StatusChip(
                label = if (selected) "SELECTED" else item.typeFallback(),
                tone =
                    if (selected) {
                        ChipTone.Primary
                    } else if (item.statementItem != null) {
                        ChipTone.Warning
                    } else {
                        ChipTone.Neutral
                    },
            )
        },
        trailing = { MoneyText(money = item.amount, bold = true) },
        onClick = onClick,
        isStandalone = false,
    )
}

private fun UnmatchedItem.typeFallback(): String = statementItem?.type ?: "BOOK"

@Composable
private fun ReviewTab(
    state: ReconciliationUiState,
    viewModel: ReconciliationViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    val completed = state.completed
    val match = state.matchResult
    Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        if (match != null) {
            PremiumListRow(
                title = "Match summary",
                subtitle = "${match.totalMatches} matched · ${match.totalUnmatched} unmatched",
                trailing = { StatusChip(label = "${(match.matchScore * 100).toInt()}%", tone = ChipTone.Info) },
            )
        }
        if (completed != null) {
            PremiumListRow(
                title = "Statement vs book",
                subtitle =
                    "Statement closing ${formatMoney(completed.statementClosingBalance)} · " +
                        "Book closing ${formatMoney(completed.bookClosingBalance)}",
                trailing = {
                    StatusChip(
                        label = completed.status.code,
                        tone =
                            when (completed.status) {
                                ReconciliationStatus.COMPLETED -> ChipTone.Success
                                ReconciliationStatus.IN_PROGRESS -> ChipTone.Warning
                                ReconciliationStatus.DISCREPANCY -> ChipTone.Error
                            },
                    )
                },
            )
            PremiumListRow(
                title = "Difference",
                trailing = { MoneyText(money = completed.difference, bold = true, colorizeSign = true) },
            )
        }
        Button(
            onClick = { viewModel.completeReconciliation() },
            enabled = state.sessionId.isNotBlank() && completed == null,
        ) { Text("Complete reconciliation") }
    }
}

@Composable
private fun HistoryTab(state: ReconciliationUiState) {
    val spacing = TanvritDesignSystem.spacing
    if (state.history.isEmpty()) {
        PremiumEmptyState(
            icon = Icons.Outlined.Sync,
            title = "No past reconciliations",
            description = "Completed reconciliations for this bank account appear here.",
        )
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        items(state.history, key = { it.id }) { item ->
            PremiumListRow(
                title = item.statementDate.ifBlank { item.statementPeriodEnd },
                subtitle = "Book ${formatMoney(item.bookClosingBalance)} · Statement ${formatMoney(item.statementClosingBalance)}",
                trailing = {
                    StatusChip(
                        label = item.status.code,
                        tone =
                            when (item.status) {
                                ReconciliationStatus.COMPLETED -> ChipTone.Success
                                ReconciliationStatus.IN_PROGRESS -> ChipTone.Warning
                                ReconciliationStatus.DISCREPANCY -> ChipTone.Error
                            },
                    )
                },
                isStandalone = false,
            )
        }
    }
}
