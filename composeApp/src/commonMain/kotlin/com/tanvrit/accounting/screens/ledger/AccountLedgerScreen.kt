package com.tanvrit.accounting.screens.ledger

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
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.tanvrit.core.feature.accounting.model.FiscalPeriod
import com.tanvrit.core.feature.accounting.model.Voucher
import com.tanvrit.core.feature.accounting.model.VoucherStatus
import com.tanvrit.core.feature.money.Money
import com.tanvrit.ui.component.lifecycle.rememberViewModel
import com.tanvrit.ui.component.premium.GlassSheet
import com.tanvrit.ui.component.premium.PremiumEmptyState
import com.tanvrit.ui.component.premium.PremiumListRow
import com.tanvrit.ui.theme.TanvritDesignSystem

/**
 * Account Ledger — the drill-down target behind Trial Balance rows and the
 * Chart of Accounts "View ledger" action: a filterable list of every posting
 * against one account, with a running balance; tapping a line opens the
 * read-only voucher detail sheet.
 */
@Composable
fun AccountLedgerScreen(
    accountId: String,
    onBack: (() -> Unit)? = null,
) {
    val viewModel = rememberViewModel(accountId) { AccountLedgerViewModel(accountId) }
    val state by viewModel.state.collectAsState()
    val spacing = TanvritDesignSystem.spacing

    Column(modifier = Modifier.fillMaxSize().padding(spacing.lg)) {
        ScreenHeader(
            title = state.account?.let { "${it.accountCode} — ${it.name}" } ?: "Account ledger",
            subtitle = "Every posting, with running balance",
            actions = {
                if (onBack != null) {
                    OutlinedButton(onClick = onBack) { Text("Back") }
                }
                OutlinedButton(onClick = { viewModel.refresh() }, enabled = !state.isLoading) { Text("Refresh") }
            },
        )

        state.error?.let {
            ErrorBanner(message = it, onRetry = { viewModel.refresh() })
            Spacer(Modifier.height(spacing.sm))
        }

        state.account?.let { account ->
            LedgerSummaryCard(state = state, account = account)
            Spacer(Modifier.height(spacing.md))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            DropdownPickerField(
                label = "Period",
                options = state.periods,
                selected = state.periods.firstOrNull { it.id == state.selectedPeriodId },
                onSelected = { period: FiscalPeriod -> viewModel.selectPeriod(period.id) },
                modifier = Modifier.weight(1f),
                optionLabel = { it.name },
            )
            OutlinedTextField(
                value = state.fromDate,
                onValueChange = { viewModel.setFromDate(it) },
                modifier = Modifier.weight(1f),
                label = { Text("From (YYYY-MM-DD)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.toDate,
                onValueChange = { viewModel.setToDate(it) },
                modifier = Modifier.weight(1f),
                label = { Text("To (YYYY-MM-DD)") },
                singleLine = true,
            )
            Button(onClick = { viewModel.refresh() }) { Text("Run") }
        }
        Spacer(Modifier.height(spacing.lg))

        when {
            state.isLoading && state.lines.isEmpty() -> LoadingPane(Modifier.weight(1f))
            state.isEmpty ->
                PremiumEmptyState(
                    icon = Icons.Outlined.Receipt,
                    title = "No postings",
                    description = "No posted vouchers for this account in the selected range.",
                )
            else -> LedgerList(state = state, onLineClick = viewModel::openVoucher)
        }
    }

    state.selectedVoucher?.let { voucher ->
        VoucherDetailSheet(voucher = voucher, onDismiss = viewModel::closeVoucher)
    }
}

/** Header card: account type chip, opening/closing balance, trial-balance cross-check. */
@Composable
private fun LedgerSummaryCard(
    state: AccountLedgerUiState,
    account: Account,
) {
    val spacing = TanvritDesignSystem.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TanvritDesignSystem.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = TanvritDesignSystem.elevation.e1,
    ) {
        Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Account type",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusChip(label = account.type.code, tone = ChipTone.Info)
            }
            BalanceRow(label = "Opening balance", money = state.openingBalance)
            BalanceRow(label = "Closing balance", money = state.closingBalance, bold = true)
            state.trialBalanceClosing?.let { trialBalance ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Per trial balance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatusChip(
                        label = formatMoney(trialBalance) + if (trialBalance == state.closingBalance) " · matches" else " · differs",
                        tone = if (trialBalance == state.closingBalance) ChipTone.Success else ChipTone.Warning,
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceRow(
    label: String,
    money: Money,
    bold: Boolean = false,
) {
    val (absolute, side) = money.toDrCr()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoneyText(money = absolute, bold = true)
            Text(
                text = " $side",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LedgerList(
    state: AccountLedgerUiState,
    onLineClick: (String) -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        items(state.lines, key = { it.entry.entryId }) { line ->
            PremiumListRow(
                title = line.entry.voucherNumber.ifBlank { line.entry.voucherId },
                subtitle = "${line.entry.date} · ${line.entry.voucherType}",
                supporting = line.entry.narration.ifBlank { null },
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                        LedgerAmountColumn(label = "Dr", money = line.entry.debit)
                        LedgerAmountColumn(label = "Cr", money = line.entry.credit)
                        LedgerBalanceColumn(balance = line.runningBalance)
                    }
                },
                onClick = { onLineClick(line.entry.voucherId) },
                isStandalone = false,
            )
        }
    }
}

@Composable
private fun LedgerAmountColumn(
    label: String,
    money: Money,
) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (money.isZero) {
            Text("—", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            MoneyText(money = money, bold = true)
        }
    }
}

@Composable
private fun LedgerBalanceColumn(balance: Money) {
    val (absolute, side) = balance.toDrCr()
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = "Balance",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoneyText(money = absolute, bold = true, colorizeSign = true)
            Text(
                text = " $side",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Read-only voucher detail — header plus its legs with Dr/Cr and totals. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoucherDetailSheet(
    voucher: Voucher,
    onDismiss: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    GlassSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(
                text = voucher.voucherNumber.ifBlank { voucher.id },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            StatusChip(label = voucher.voucherType.code, tone = ChipTone.Info)
            StatusChip(
                label = voucher.status.code,
                tone =
                    when (voucher.status) {
                        VoucherStatus.POSTED -> ChipTone.Success
                        VoucherStatus.DRAFT -> ChipTone.Neutral
                        VoucherStatus.REVERSED -> ChipTone.Warning
                        VoucherStatus.CANCELLED -> ChipTone.Error
                    },
            )
        }
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = voucher.date + if (voucher.narration.isNotBlank()) " · ${voucher.narration}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.lg))

        Text("Line items", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(spacing.xs))
        voucher.lineItems.forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xxs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${line.accountCode} — ${line.accountName}", style = MaterialTheme.typography.bodyMedium)
                    if (line.narration.isNotBlank()) {
                        Text(
                            line.narration,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LedgerAmountColumn(label = "Dr", money = line.debit)
                    LedgerAmountColumn(label = "Cr", money = line.credit)
                }
            }
        }
        Spacer(Modifier.height(spacing.md))

        val totalDebit = voucher.lineItems.fold(Money.ZERO) { acc, line -> acc + line.debit }
        val totalCredit = voucher.lineItems.fold(Money.ZERO) { acc, line -> acc + line.credit }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total debit", fontWeight = FontWeight.SemiBold)
            MoneyText(money = totalDebit, bold = true)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total credit", fontWeight = FontWeight.SemiBold)
            MoneyText(money = totalCredit, bold = true)
        }
        StatusChip(
            label = if (totalDebit == totalCredit) "BALANCED" else "OUT OF BALANCE",
            tone = if (totalDebit == totalCredit) ChipTone.Success else ChipTone.Error,
        )
    }
}
