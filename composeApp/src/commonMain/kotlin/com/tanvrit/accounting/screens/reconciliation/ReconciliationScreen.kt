package com.tanvrit.accounting.screens.reconciliation

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.tanvrit.ui.component.premium.GlassSheet
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
            actions = {
                OutlinedButton(onClick = { viewModel.openRulesSheet() }) {
                    Icon(Icons.AutoMirrored.Outlined.Rule, contentDescription = null)
                    Spacer(Modifier.width(TanvritDesignSystem.spacing.xs))
                    Text("Rules")
                }
            },
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

        PrimaryTabRow(selectedTabIndex = state.activeTab.ordinal) {
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

    if (state.showRulesSheet) {
        BankRulesSheet(
            state = state,
            onAdd = viewModel::addBankRule,
            onDelete = viewModel::deleteBankRule,
            onDismiss = { viewModel.closeRulesSheet() },
        )
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
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Button(
                onClick = { viewModel.importStatement() },
                enabled = state.csvInput.isNotBlank() && state.bankAccountId.isNotBlank(),
            ) { Text("Import statement") }
            OutlinedButton(
                onClick = { viewModel.applyBankRules() },
                enabled = state.csvInput.isNotBlank() && state.rules.isNotEmpty(),
            ) { Text("Apply rules (local)") }
        }
        if (state.ruleSuggestions.isNotEmpty()) {
            Text(
                "Rule suggestions — local only, not sent to the server",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            state.ruleSuggestions.forEach { suggestion ->
                PremiumListRow(
                    title = suggestion.narration.ifBlank { "(blank narration)" },
                    subtitle = "Suggest: ${suggestion.suggestedAccountCode} — ${suggestion.suggestedAccountName}",
                    leading = { StatusChip(label = "rule-suggested (local)", tone = ChipTone.Info) },
                    isStandalone = false,
                )
            }
        }
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

/**
 * Bank-rules manager (roadmap #16 lite) — list/add/delete local narration →
 * account rules. Client-local only: rules persist via `BankRulesStore` and
 * never touch the server; the server-side auto-match is unchanged.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BankRulesSheet(
    state: ReconciliationUiState,
    onAdd: (BankRuleMatchType, String, String, Int) -> String?,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    var matchType by remember { mutableStateOf(BankRuleMatchType.CONTAINS) }
    var pattern by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf("") }
    var priorityText by remember { mutableStateOf("10") }
    var formError by remember { mutableStateOf<String?>(null) }

    GlassSheet(onDismiss = onDismiss) {
        Text("Bank rules", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(spacing.xs))
        Text(
            "Local narration → account suggestions (roadmap #16 lite). Applied on demand to the pasted " +
                "statement only — nothing is sent to the server or posted to the ledger.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.md))

        DropdownPickerField(
            label = "Match",
            options = BankRuleMatchType.entries,
            selected = matchType,
            onSelected = { matchType = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(spacing.sm))
        OutlinedTextField(
            value = pattern,
            onValueChange = { pattern = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Pattern (matched against narration)") },
            singleLine = true,
        )
        Spacer(Modifier.height(spacing.sm))
        DropdownPickerField(
            label = "Suggest account",
            options = state.accounts.sortedBy { it.accountCode },
            selected = state.accounts.firstOrNull { it.id == accountId },
            onSelected = { account: Account -> accountId = account.id },
            modifier = Modifier.fillMaxWidth(),
            optionLabel = { "${it.accountCode} — ${it.name}" },
        )
        Spacer(Modifier.height(spacing.sm))
        OutlinedTextField(
            value = priorityText,
            onValueChange = { v -> priorityText = v.filter(Char::isDigit) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Priority (lower wins)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        formError?.let {
            Spacer(Modifier.height(spacing.xs))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(spacing.md))
        Button(
            onClick = {
                val error = onAdd(matchType, pattern, accountId, priorityText.toIntOrNull() ?: 10)
                if (error == null) {
                    pattern = ""
                    formError = null
                } else {
                    formError = error
                }
            },
            enabled = pattern.isNotBlank() && accountId.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add rule") }

        Spacer(Modifier.height(spacing.lg))
        state.rules.forEach { rule ->
            PremiumListRow(
                title = "${rule.matchType.name} \"${rule.pattern}\"",
                subtitle = "→ ${rule.suggestedAccountCode} · priority ${rule.priority}",
                trailing = {
                    IconButton(onClick = { onDelete(rule.id) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete rule")
                    }
                },
                isStandalone = false,
            )
        }
    }
}

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
