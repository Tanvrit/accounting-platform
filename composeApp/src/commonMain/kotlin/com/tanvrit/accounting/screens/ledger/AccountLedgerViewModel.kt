package com.tanvrit.accounting.screens.ledger

import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.network.ReportNetwork
import com.tanvrit.accounting.repository.AccountRepository
import com.tanvrit.accounting.repository.FiscalPeriodRepository
import com.tanvrit.accounting.repository.VoucherRepository
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.FiscalPeriod
import com.tanvrit.core.feature.accounting.model.Voucher
import com.tanvrit.core.feature.accounting.network.TrialBalanceRequest
import com.tanvrit.core.feature.money.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class AccountLedgerUiState(
    val businessId: String = "",
    val accountId: String = "",
    val account: Account? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val periods: List<FiscalPeriod> = emptyList(),
    val selectedPeriodId: String = "",
    val fromDate: String = "",
    val toDate: String = "",
    /** Carry into the window: account opening + posted deltas before [fromDate]. */
    val openingBalance: Money = Money.ZERO,
    /** [openingBalance] + posted deltas inside the window. */
    val closingBalance: Money = Money.ZERO,
    /** Server truth from `trialBalanceAsync` for the window end — null when the row is absent. */
    val trialBalanceClosing: Money? = null,
    val lines: List<LedgerLine> = emptyList(),
    private val vouchers: List<Voucher> = emptyList(),
    /** Voucher id whose read-only detail sheet is open; null = sheet closed. */
    val selectedVoucherId: String? = null,
) {
    val selectedVoucher: Voucher? get() = vouchers.firstOrNull { it.id == selectedVoucherId }
    val isEmpty: Boolean get() = !isLoading && error == null && lines.isEmpty()
}

/**
 * Account Ledger — drill-down target behind Trial Balance rows and the Chart
 * of Accounts "View ledger" action (roadmap feature #1).
 *
 * Reads are offline-first: vouchers come from the SDK [VoucherRepository]
 * (local cache; creation/sync upserts keep it warm) and are filtered to the
 * account by [LedgerMath]. There is intentionally no per-voucher fetch here —
 * SDK 3.0.7 `VoucherNetwork.retrieveVoucherAsync` decodes a singular
 * `VoucherResponse` while the server answers a list, so bulk listing would
 * fail on decode; the cached list (which carries full line items) is the
 * honest source until the SDK contract gains a list-typed retrieval.
 *
 * Opening/closing shown in the header are computed locally (carry from prior
 * periods); `trialBalanceClosing` is the server cross-check from
 * [ReportNetwork.trialBalanceAsync] when a row for this account exists.
 */
class AccountLedgerViewModel(
    accountId: String,
) : AppViewModel() {
    private val workspace: AccountingWorkspace = TanvritKoin.get()
    private val accountRepository: AccountRepository = TanvritKoin.get()
    private val voucherRepository: VoucherRepository = TanvritKoin.get()
    private val fiscalPeriodRepository: FiscalPeriodRepository = TanvritKoin.get()
    private val reportNetwork = ReportNetwork.shared()

    private val _state =
        MutableStateFlow(
            AccountLedgerUiState(accountId = accountId, businessId = workspace.businessId.value),
        )
    val state = _state.asStateFlow()

    init {
        val today = todayIso()
        _state.value =
            _state.value.copy(
                fromDate = LedgerMath.fiscalYearStart(today),
                toDate = today,
            )
        scope.launch {
            workspace.businessId.collect { businessId ->
                _state.value = _state.value.copy(businessId = businessId)
                if (businessId.isNotBlank()) {
                    loadAccount()
                    loadPeriods()
                    refresh()
                }
            }
        }
    }

    fun selectPeriod(periodId: String) {
        val period = _state.value.periods.firstOrNull { it.id == periodId }
        _state.value =
            _state.value.copy(
                selectedPeriodId = periodId,
                fromDate = period?.startDate ?: _state.value.fromDate,
                toDate = period?.endDate ?: _state.value.toDate,
            )
        refresh()
    }

    fun setFromDate(value: String) {
        _state.value = _state.value.copy(fromDate = value, selectedPeriodId = "")
    }

    fun setToDate(value: String) {
        _state.value = _state.value.copy(toDate = value, selectedPeriodId = "")
    }

    fun openVoucher(voucherId: String) {
        _state.value = _state.value.copy(selectedVoucherId = voucherId)
    }

    fun closeVoucher() {
        _state.value = _state.value.copy(selectedVoucherId = null)
    }

    fun refresh() {
        val businessId = _state.value.businessId
        val accountId = _state.value.accountId
        if (businessId.isBlank() || accountId.isBlank()) return
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val cached =
                runCatching {
                    voucherRepository.findByBusinessId(businessId, null, null, null, 0, PAGE_SIZE)
                }
            cached
                .onSuccess { vouchers ->
                    rebuildFrom(vouchers)
                    refreshTrialBalance()
                }.onFailure {
                    _state.value =
                        _state.value.copy(isLoading = false, error = it.message ?: "Failed to load vouchers")
                }
        }
    }

    /** Account header — offline-first from the repository cache. */
    private suspend fun loadAccount() {
        val accountId = _state.value.accountId
        if (accountId.isBlank()) return
        val cached = runCatching { accountRepository.findById(accountId) }.getOrNull()
        if (cached != null) {
            _state.value = _state.value.copy(account = cached)
            return
        }
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        runCatching { accountRepository.findByBusinessId(businessId, null, null, 0, PAGE_SIZE) }
            .onSuccess { accounts ->
                _state.value = _state.value.copy(account = accounts.firstOrNull { it.id == accountId })
            }
    }

    private suspend fun loadPeriods() {
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        runCatching { fiscalPeriodRepository.findByBusinessId(businessId) }
            .onSuccess { periods ->
                _state.value = _state.value.copy(periods = periods.sortedByDescending { it.startDate })
            }
    }

    /** Rebuild opening/closing/lines from a voucher set. */
    private fun rebuildFrom(vouchers: List<Voucher>) {
        val s = _state.value
        val entries = LedgerMath.entriesForAccount(vouchers, s.accountId)
        val opening = LedgerMath.openingAt(s.account?.openingBalance ?: Money.ZERO, entries, s.fromDate)
        val lines = LedgerMath.linesInRange(entries, s.fromDate, s.toDate, opening)
        _state.value =
            s.copy(
                isLoading = false,
                error = null,
                vouchers = vouchers,
                openingBalance = opening,
                closingBalance = LedgerMath.closingOf(opening, lines),
                lines = lines,
            )
    }

    /** Server cross-check for the header — closing per trial balance at [state.toDate]. */
    private suspend fun refreshTrialBalance() {
        val s = _state.value
        reportNetwork
            .trialBalanceAsync(
                TrialBalanceRequest(
                    businessId = s.businessId,
                    asOfDate = s.toDate,
                    fiscalPeriodId = s.selectedPeriodId,
                    includeZeroBalance = true,
                ),
            ).fold(
                onSuccess = { response ->
                    val row = response.payload?.rows?.firstOrNull { it.accountId == s.accountId }
                    _state.value =
                        _state.value.copy(trialBalanceClosing = row?.let { it.debit - it.credit })
                },
                onFailure = {
                    // TB is a cross-check only — keep the locally computed balances.
                    _state.value = _state.value.copy(trialBalanceClosing = null)
                },
            )
    }

    private fun todayIso(): String =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()

    companion object {
        /** Single-shot window read, matching the page size convention of the other screens. */
        private const val PAGE_SIZE = 500
    }
}
