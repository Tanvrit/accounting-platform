package com.tanvrit.accounting.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanvrit.accounting.screens.common.ChipTone
import com.tanvrit.accounting.screens.common.ErrorBanner
import com.tanvrit.accounting.screens.common.LoadingPane
import com.tanvrit.accounting.screens.common.MoneyText
import com.tanvrit.accounting.screens.common.ScreenHeader
import com.tanvrit.accounting.screens.common.StatusChip
import com.tanvrit.accounting.screens.common.formatMoney
import com.tanvrit.core.feature.accounting.model.Voucher
import com.tanvrit.core.feature.accounting.model.VoucherStatus
import com.tanvrit.core.feature.money.Money
import com.tanvrit.ui.component.lifecycle.rememberViewModel
import com.tanvrit.ui.component.premium.PremiumEmptyState
import com.tanvrit.ui.component.premium.PremiumHeroCard
import com.tanvrit.ui.component.premium.PremiumListRow
import com.tanvrit.ui.component.premium.PremiumStatCard
import com.tanvrit.ui.component.premium.rememberPremiumChartTheme
import com.tanvrit.ui.theme.TanvritDesignSystem
import kotlinx.coroutines.delay

/** Auto-refresh cadence for the KPI dashboard. */
private const val AUTO_REFRESH_MS = 30_000L

/** Dashboard — live KPIs, trend and activity for the active business. */
@Composable
fun DashboardScreen() {
    val viewModel = rememberViewModel { DashboardViewModel() }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.businessId) {
        while (state.businessId.isNotBlank()) {
            delay(AUTO_REFRESH_MS)
            viewModel.refresh()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(TanvritDesignSystem.spacing.lg),
    ) {
        ScreenHeader(
            title = "Dashboard",
            subtitle = if (state.asOfDate.isBlank()) "Business overview" else "As of ${state.asOfDate}",
            actions = {
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
            },
        )

        state.error?.let {
            ErrorBanner(message = it, onRetry = { viewModel.refresh() })
            Spacer(Modifier.height(TanvritDesignSystem.spacing.lg))
        }

        when {
            state.businessId.isBlank() -> NoWorkspacePane()
            state.isLoading && state.recentVouchers.isEmpty() && state.totalRevenue == state.totalExpenses ->
                LoadingPane(Modifier.weight(1f))
            else -> DashboardBody(state = state)
        }
    }
}

@Composable
private fun NoWorkspacePane() {
    PremiumEmptyState(
        icon = Icons.Outlined.AccountBalanceWallet,
        title = "No business selected",
        description = "Select or create a business workspace to see its dashboard, chart of accounts and vouchers.",
    )
}

@Composable
private fun DashboardBody(state: DashboardUiState) {
    val spacing = TanvritDesignSystem.spacing
    val chartTheme = rememberPremiumChartTheme()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item {
            PremiumHeroCard {
                Text(
                    text = "Cash position",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = formatMoney(state.cashPosition),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "Net profit this FY: ${formatMoney(state.netProfit)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 240.dp),
                modifier = Modifier.fillMaxWidth().height(340.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
                userScrollEnabled = false,
            ) {
                item {
                    PremiumStatCard(
                        label = "Revenue (FY)",
                        value = formatMoney(state.totalRevenue),
                        icon = Icons.AutoMirrored.Outlined.TrendingUp,
                    )
                }
                item {
                    PremiumStatCard(
                        label = "Expenses (FY)",
                        value = formatMoney(state.totalExpenses),
                        icon = Icons.Outlined.Payments,
                    )
                }
                item {
                    PremiumStatCard(
                        label = "GST liability",
                        value = formatMoney(state.gstLiability),
                        icon = Icons.Outlined.CurrencyRupee,
                        sparkline =
                            listOf(
                                state.gstInput.toDouble().toFloat(),
                                state.gstOutput.toDouble().toFloat(),
                            ),
                    )
                }
                item {
                    PremiumStatCard(
                        label = "Vouchers",
                        value = state.recentVouchers.size.toString(),
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    )
                }
            }
        }

        if (state.topExpenses.isNotEmpty()) {
            item {
                Text(
                    text = "Top expenses",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                // chartTheme.series drives any chart colors (Design 2.0); the
                // list rows below use the first series accent.
                val accent = chartTheme.series.firstOrNull()
                if (accent != null) {
                    Text(
                        text = "Largest FY expense accounts, descending",
                        style = MaterialTheme.typography.bodySmall,
                        color = accent,
                    )
                }
            }
            items(state.topExpenses) { (name, balance) ->
                PremiumListRow(
                    title = name,
                    trailing = { MoneyText(money = balance, bold = true) },
                )
            }
        }

        item {
            Text(
                text = "Recent activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (state.recentVouchers.isEmpty()) {
            item {
                PremiumEmptyState(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = "No vouchers yet",
                    description = "Post your first voucher from the Voucher screen.",
                )
            }
        } else {
            items(state.recentVouchers) { voucher -> VoucherRow(voucher) }
        }
    }
}

@Composable
private fun VoucherRow(voucher: Voucher) {
    PremiumListRow(
        title = voucher.voucherNumber.ifBlank { voucher.voucherType.code },
        subtitle = voucher.date,
        supporting = voucher.narration.ifBlank { voucher.voucherType.code },
        leading = {},
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val balance = voucher.lineItems.fold(Money.ZERO) { acc, line -> acc + line.debit }
                MoneyText(money = balance, bold = true)
                StatusChip(
                    label = voucher.status.code,
                    tone =
                        when (voucher.status) {
                            VoucherStatus.POSTED -> ChipTone.Success
                            VoucherStatus.DRAFT -> ChipTone.Warning
                            VoucherStatus.REVERSED -> ChipTone.Neutral
                            VoucherStatus.CANCELLED -> ChipTone.Error
                        },
                    modifier = Modifier.padding(start = TanvritDesignSystem.spacing.sm),
                )
            }
        },
    )
}
