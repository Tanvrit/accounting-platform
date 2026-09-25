package com.tanvrit.accounting.screens.dashboard

import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.network.ReportNetwork
import com.tanvrit.accounting.repository.VoucherRepository
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.AccountType
import com.tanvrit.core.feature.accounting.model.TrialBalanceReport
import com.tanvrit.core.feature.accounting.model.Voucher
import com.tanvrit.core.feature.accounting.network.CashFlowRequest
import com.tanvrit.core.feature.accounting.network.ProfitAndLossRequest
import com.tanvrit.core.feature.accounting.network.TrialBalanceRequest
import com.tanvrit.core.feature.money.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class DashboardUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val businessId: String = "",
    val asOfDate: String = "",
    val cashPosition: Money = Money.ZERO,
    val totalRevenue: Money = Money.ZERO,
    val totalExpenses: Money = Money.ZERO,
    val netProfit: Money = Money.ZERO,
    val gstOutput: Money = Money.ZERO,
    val gstInput: Money = Money.ZERO,
    /** Top expense accounts (name ↔ balance), descending. */
    val topExpenses: List<Pair<String, Money>> = emptyList(),
    val recentVouchers: List<Voucher> = emptyList(),
) {
    val gstLiability: Money get() = gstOutput - gstInput
}

/**
 * Dashboard — real-time KPIs for the active business.
 *
 * Reads: `VoucherRepository` (offline-first recent vouchers) and
 * `ReportNetwork` (trial balance / P&L / cash flow from the server). All
 * figures are Money minor-units; nothing here formats — the screen formats
 * via [com.tanvrit.core.feature.money.MoneyFormatter].
 */
class DashboardViewModel : AppViewModel() {
    private val workspace: AccountingWorkspace = TanvritKoin.get()
    private val reportNetwork = ReportNetwork.shared()
    private val voucherRepository: VoucherRepository = TanvritKoin.get()

    private val _state = MutableStateFlow(DashboardUiState(businessId = workspace.businessId.value))
    val state = _state.asStateFlow()

    init {
        scope.launch {
            workspace.businessId.collect { businessId ->
                _state.value = DashboardUiState(businessId = businessId)
                if (businessId.isNotBlank()) refresh()
            }
        }
    }

    fun refresh() {
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        val today =
            Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
                .toString()
        val fyStart = fiscalYearStart(today)

        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            val trialBalance =
                reportNetwork.trialBalanceAsync(
                    TrialBalanceRequest(businessId = businessId, asOfDate = today),
                )
            val profitAndLoss =
                reportNetwork.profitAndLossAsync(
                    ProfitAndLossRequest(businessId = businessId, fromDate = fyStart, toDate = today),
                )
            val cashFlow =
                reportNetwork.cashFlowAsync(
                    CashFlowRequest(businessId = businessId, fromDate = fyStart, toDate = today),
                )
            val recent =
                runCatching {
                    voucherRepository.findByBusinessId(businessId, null, null, null, 0, RECENT_VOUCHER_LIMIT)
                }.getOrDefault(emptyList())

            val failure =
                listOfNotNull(
                    trialBalance.exceptionOrNull(),
                    profitAndLoss.exceptionOrNull(),
                    cashFlow.exceptionOrNull(),
                ).firstOrNull()

            val tb: TrialBalanceReport? = trialBalance.getOrNull()?.payload
            val gstOut =
                tb
                    ?.rows
                    ?.filter { it.accountType == AccountType.GST_OUTPUT.code }
                    ?.fold(Money.ZERO) { acc, row -> acc + row.credit } ?: Money.ZERO
            val gstIn =
                tb
                    ?.rows
                    ?.filter { it.accountType == AccountType.GST_INPUT.code }
                    ?.fold(Money.ZERO) { acc, row -> acc + row.debit } ?: Money.ZERO

            val pnl = profitAndLoss.getOrNull()?.payload
            val cf = cashFlow.getOrNull()?.payload

            _state.value =
                _state.value.copy(
                    isLoading = false,
                    error = failure?.message,
                    asOfDate = today,
                    cashPosition = cf?.closingCash ?: Money.ZERO,
                    totalRevenue = pnl?.totalRevenue ?: Money.ZERO,
                    totalExpenses = pnl?.totalExpenses ?: Money.ZERO,
                    netProfit = pnl?.netProfit ?: Money.ZERO,
                    gstOutput = gstOut,
                    gstInput = gstIn,
                    topExpenses =
                        pnl
                            ?.expenseAccounts
                            ?.sortedByDescending { it.balance }
                            ?.take(5)
                            ?.map { it.accountName to it.balance }
                            ?: emptyList(),
                    recentVouchers = recent.sortedByDescending { it.date }.take(8),
                )
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /** Indian FY: April 1 of (year) when month >= 4, else April 1 of (year - 1). */
    private fun fiscalYearStart(todayIso: String): String {
        val year = todayIso.substringBefore("-").toIntOrNull() ?: return todayIso
        val month = todayIso.substringAfter("-").substringBefore("-").toIntOrNull() ?: 4
        return (if (month >= 4) year else year - 1).toString().padStart(4, '0') + "-04-01"
    }

    private companion object {
        const val RECENT_VOUCHER_LIMIT = 8
    }
}
