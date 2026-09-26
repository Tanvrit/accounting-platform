package com.tanvrit.accounting.screens.reports

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
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import com.tanvrit.accounting.screens.common.twoDecimals
import com.tanvrit.core.feature.accounting.model.AccountBalance
import com.tanvrit.core.feature.accounting.model.CashFlowActivity
import com.tanvrit.core.feature.accounting.model.FiscalPeriod
import com.tanvrit.core.feature.accounting.model.TrialBalanceRow
import com.tanvrit.ui.component.lifecycle.rememberViewModel
import com.tanvrit.ui.component.premium.PremiumEmptyState
import com.tanvrit.ui.component.premium.PremiumListRow
import com.tanvrit.ui.theme.TanvritDesignSystem

/** Reports — Trial Balance, P&L, Balance Sheet, Cash Flow, Ratios + export. */
@Composable
fun ReportsScreen(
    // WIRING(report→ledger): pass nav to AccountLedgerRoute — e.g.
    // ReportsScreen(onAccountClick = { id -> navController.navigate(AppRoute.AccountLedger(id)) })
    onAccountClick: (accountId: String) -> Unit = {},
) {
    val viewModel = rememberViewModel { ReportsViewModel() }
    val state by viewModel.state.collectAsState()
    val spacing = TanvritDesignSystem.spacing

    Column(modifier = Modifier.fillMaxSize().padding(spacing.lg)) {
        ScreenHeader(
            title = "Reports",
            subtitle = "Financial statements, server-computed",
            actions = {
                OutlinedButton(onClick = { viewModel.export("CSV") }, enabled = !state.isExporting) { Text("CSV") }
                OutlinedButton(onClick = { viewModel.export("EXCEL") }, enabled = !state.isExporting) { Text("Excel") }
                Button(onClick = { viewModel.export("PDF") }, enabled = !state.isExporting) {
                    Text(if (state.isExporting) "Exporting…" else "PDF")
                }
            },
        )

        state.error?.let {
            ErrorBanner(message = it, onRetry = { viewModel.runReport() })
            Spacer(Modifier.height(spacing.sm))
        }
        state.notice?.let {
            StatusChip(label = it + if (state.exportFileUrl.isNotBlank()) " · ${state.exportFileUrl}" else "", tone = ChipTone.Info)
            Spacer(Modifier.height(spacing.sm))
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
                value =
                    when (state.activeTab) {
                        ReportTab.TRIAL_BALANCE, ReportTab.BALANCE_SHEET -> state.asOfDate
                        else -> "${state.fromDate} → ${state.toDate}"
                    },
                onValueChange = { v -> viewModel.setAsOfDate(v) },
                modifier = Modifier.weight(1f),
                label = {
                    Text(
                        if (state.activeTab == ReportTab.TRIAL_BALANCE ||
                            state.activeTab == ReportTab.BALANCE_SHEET
                        ) {
                            "As of date"
                        } else {
                            "Range (from → to)"
                        },
                    )
                },
                singleLine = true,
            )
        }
        if (state.activeTab == ReportTab.PROFIT_AND_LOSS || state.activeTab == ReportTab.CASH_FLOW || state.activeTab == ReportTab.RATIOS) {
            Spacer(Modifier.height(spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
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
                Button(onClick = { viewModel.runReport() }) { Text("Run") }
            }
        }
        Spacer(Modifier.height(spacing.md))

        SecondaryTabRow(selectedTabIndex = state.activeTab.ordinal) {
            ReportTab.entries.forEach { tab ->
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
                ReportTab.TRIAL_BALANCE -> TrialBalanceTab(state, onAccountClick)
                ReportTab.PROFIT_AND_LOSS -> ProfitAndLossTab(state)
                ReportTab.BALANCE_SHEET -> BalanceSheetTab(state)
                ReportTab.CASH_FLOW -> CashFlowTab(state)
                ReportTab.RATIOS -> RatiosTab(state)
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TanvritDesignSystem.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = TanvritDesignSystem.elevation.e1,
    ) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(spacing.sm))
            content()
        }
    }
}

@Composable
private fun TrialBalanceTab(
    state: ReportsUiState,
    onAccountClick: (accountId: String) -> Unit,
) {
    val report = state.trialBalance
    if (report == null) {
        PremiumEmptyState(
            icon = Icons.Outlined.Assessment,
            title = "No trial balance",
            description = "Run the report for a period with posted vouchers.",
        )
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(TanvritDesignSystem.spacing.xs)) {
        items(report.rows) { row: TrialBalanceRow ->
            PremiumListRow(
                title = "${row.accountCode} — ${row.accountName}",
                subtitle = row.accountType,
                trailing = {
                    Row {
                        MoneyText(money = row.debit, bold = true)
                        Text("  /  ", style = MaterialTheme.typography.bodySmall)
                        MoneyText(money = row.credit, bold = true)
                    }
                },
                supporting = "Dr / Cr",
                onClick = { onAccountClick(row.accountId) },
                isStandalone = false,
            )
        }
        item {
            SectionCard(title = "Totals") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Total debit")
                    MoneyText(money = report.totalDebit, bold = true)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Total credit")
                    MoneyText(money = report.totalCredit, bold = true)
                }
                StatusChip(
                    label = if (report.totalDebit == report.totalCredit) "BALANCED" else "OUT OF BALANCE",
                    tone = if (report.totalDebit == report.totalCredit) ChipTone.Success else ChipTone.Error,
                )
            }
        }
    }
}

@Composable
private fun AccountBalanceRows(accounts: List<AccountBalance>) {
    accounts.forEach { account ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = TanvritDesignSystem.spacing.xxs),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(account.accountName, style = MaterialTheme.typography.bodyMedium)
            MoneyText(money = account.balance, bold = true)
        }
    }
}

@Composable
private fun ProfitAndLossTab(state: ReportsUiState) {
    val report = state.profitAndLoss
    if (report == null) {
        PremiumEmptyState(icon = Icons.Outlined.Assessment, title = "No P&L", description = "Run the report for the selected range.")
        return
    }
    val spacing = TanvritDesignSystem.spacing
    LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        item {
            SectionCard(title = "Revenue") {
                AccountBalanceRows(report.revenueAccounts)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total revenue", fontWeight = FontWeight.SemiBold)
                    MoneyText(money = report.totalRevenue, bold = true)
                }
            }
        }
        item {
            SectionCard(title = "Expenses") {
                AccountBalanceRows(report.expenseAccounts)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total expenses", fontWeight = FontWeight.SemiBold)
                    MoneyText(money = report.totalExpenses, bold = true)
                }
            }
        }
        item {
            SectionCard(title = "Result") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Net profit", fontWeight = FontWeight.Bold)
                    MoneyText(money = report.netProfit, bold = true, colorizeSign = true)
                }
            }
        }
    }
}

@Composable
private fun BalanceSheetTab(state: ReportsUiState) {
    val report = state.balanceSheet
    if (report == null) {
        PremiumEmptyState(icon = Icons.Outlined.Assessment, title = "No balance sheet", description = "Run the report as of a date.")
        return
    }
    val spacing = TanvritDesignSystem.spacing
    LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        item {
            SectionCard(title = "Assets") {
                AccountBalanceRows(report.assets)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total assets", fontWeight = FontWeight.SemiBold)
                    MoneyText(money = report.totalAssets, bold = true)
                }
            }
        }
        item {
            SectionCard(title = "Liabilities") {
                AccountBalanceRows(report.liabilities)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total liabilities", fontWeight = FontWeight.SemiBold)
                    MoneyText(money = report.totalLiabilities, bold = true)
                }
            }
        }
        item {
            SectionCard(title = "Equity") {
                AccountBalanceRows(report.equity)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total equity", fontWeight = FontWeight.SemiBold)
                    MoneyText(money = report.totalEquity, bold = true)
                }
                StatusChip(
                    label =
                        if (report.totalAssets == report.totalLiabilities + report.totalEquity) "BALANCED" else "CHECK",
                    tone = if (report.totalAssets == report.totalLiabilities + report.totalEquity) ChipTone.Success else ChipTone.Warning,
                )
            }
        }
    }
}

@Composable
private fun CashFlowActivityRows(activities: List<CashFlowActivity>) {
    activities.forEach { activity ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = TanvritDesignSystem.spacing.xxs),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(activity.description, style = MaterialTheme.typography.bodyMedium)
            MoneyText(money = activity.amount, bold = true, colorizeSign = true)
        }
    }
}

@Composable
private fun CashFlowTab(state: ReportsUiState) {
    val report = state.cashFlow
    if (report == null) {
        PremiumEmptyState(icon = Icons.Outlined.Assessment, title = "No cash flow", description = "Run the report for the selected range.")
        return
    }
    val spacing = TanvritDesignSystem.spacing
    LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        item {
            SectionCard(title = "Operating") {
                CashFlowActivityRows(report.operatingActivities)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Operating cash flow", fontWeight = FontWeight.SemiBold)
                    MoneyText(money = report.operatingCashFlow, bold = true, colorizeSign = true)
                }
            }
        }
        item {
            SectionCard(title = "Investing") {
                CashFlowActivityRows(report.investingActivities)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Investing cash flow", fontWeight = FontWeight.SemiBold)
                    MoneyText(money = report.investingCashFlow, bold = true, colorizeSign = true)
                }
            }
        }
        item {
            SectionCard(title = "Financing") {
                CashFlowActivityRows(report.financingActivities)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Financing cash flow", fontWeight = FontWeight.SemiBold)
                    MoneyText(money = report.financingCashFlow, bold = true, colorizeSign = true)
                }
            }
        }
        item {
            SectionCard(title = "Net movement") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Opening cash")
                    MoneyText(money = report.openingCash, bold = true)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Net cash flow", fontWeight = FontWeight.SemiBold)
                    MoneyText(money = report.netCashFlow, bold = true, colorizeSign = true)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Closing cash", fontWeight = FontWeight.Bold)
                    MoneyText(money = report.closingCash, bold = true)
                }
            }
        }
    }
}

@Composable
private fun RatiosTab(state: ReportsUiState) {
    val report = state.ratios
    if (report == null) {
        PremiumEmptyState(
            icon = Icons.Outlined.Assessment,
            title = "No ratios",
            description = "Run ratio analysis for the selected period.",
        )
        return
    }
    val spacing = TanvritDesignSystem.spacing
    LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        items(report.ratios.entries.toList()) { (name, value) ->
            PremiumListRow(
                title = name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() },
                trailing = {
                    Row {
                        Text(
                            text = twoDecimals(value),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        report.benchmarks[name]?.let { benchmark ->
                            Text(
                                text = "  (bench " + twoDecimals(benchmark) + ")",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                supporting = report.trends[name]?.joinToString(" → ") { entry -> twoDecimals(entry) },
                isStandalone = false,
            )
        }
    }
}
