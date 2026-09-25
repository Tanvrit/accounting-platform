package com.tanvrit.accounting.screens.reports

import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.network.ReportNetwork
import com.tanvrit.accounting.repository.FiscalPeriodRepository
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.BalanceSheetReport
import com.tanvrit.core.feature.accounting.model.CashFlowReport
import com.tanvrit.core.feature.accounting.model.FiscalPeriod
import com.tanvrit.core.feature.accounting.model.ProfitAndLossReport
import com.tanvrit.core.feature.accounting.model.RatioAnalysisReport
import com.tanvrit.core.feature.accounting.model.TrialBalanceReport
import com.tanvrit.core.feature.accounting.network.BalanceSheetRequest
import com.tanvrit.core.feature.accounting.network.CashFlowRequest
import com.tanvrit.core.feature.accounting.network.ExportReportRequest
import com.tanvrit.core.feature.accounting.network.ProfitAndLossRequest
import com.tanvrit.core.feature.accounting.network.RatioAnalysisRequest
import com.tanvrit.core.feature.accounting.network.TrialBalanceRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

enum class ReportTab(
    val label: String,
    val exportType: String,
) {
    TRIAL_BALANCE("Trial Balance", "TRIAL_BALANCE"),
    PROFIT_AND_LOSS("P&L", "PROFIT_AND_LOSS"),
    BALANCE_SHEET("Balance Sheet", "BALANCE_SHEET"),
    CASH_FLOW("Cash Flow", "CASH_FLOW"),
    RATIOS("Ratios", "RATIO_ANALYSIS"),
}

data class ReportsUiState(
    val businessId: String = "",
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val activeTab: ReportTab = ReportTab.TRIAL_BALANCE,
    val periods: List<FiscalPeriod> = emptyList(),
    val selectedPeriodId: String = "",
    val asOfDate: String = "",
    val fromDate: String = "",
    val toDate: String = "",
    val trialBalance: TrialBalanceReport? = null,
    val profitAndLoss: ProfitAndLossReport? = null,
    val balanceSheet: BalanceSheetReport? = null,
    val cashFlow: CashFlowReport? = null,
    val ratios: RatioAnalysisReport? = null,
    /** Last export result — a short-lived server-side artifact. */
    val exportFileUrl: String = "",
    val exportFileName: String = "",
)

/**
 * Reports hub — Trial Balance, P&L, Balance Sheet, Cash Flow and Ratio
 * Analysis, all computed server-side by the accounting engine; export goes
 * through `exportReport` which returns a downloadable artifact URL.
 */
class ReportsViewModel : AppViewModel() {
    private val workspace: AccountingWorkspace = TanvritKoin.get()
    private val reportNetwork = ReportNetwork.shared()
    private val fiscalPeriodRepository: FiscalPeriodRepository = TanvritKoin.get()

    private val _state = MutableStateFlow(ReportsUiState(businessId = workspace.businessId.value))
    val state = _state.asStateFlow()

    init {
        val today =
            Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
                .toString()
        _state.value = _state.value.copy(asOfDate = today, fromDate = fiscalYearStart(today), toDate = today)
        scope.launch {
            workspace.businessId.collect { businessId ->
                _state.value = _state.value.copy(businessId = businessId)
                if (businessId.isNotBlank()) {
                    loadPeriods(businessId)
                    runReport()
                }
            }
        }
    }

    private suspend fun loadPeriods(businessId: String) {
        runCatching { fiscalPeriodRepository.findByBusinessId(businessId) }
            .onSuccess { periods ->
                val sorted = periods.sortedByDescending { it.startDate }
                _state.value =
                    _state.value.copy(
                        periods = sorted,
                        selectedPeriodId = _state.value.selectedPeriodId.ifBlank { sorted.firstOrNull()?.id ?: "" },
                    )
            }
    }

    fun selectTab(tab: ReportTab) {
        _state.value = _state.value.copy(activeTab = tab)
        runReport()
    }

    fun selectPeriod(periodId: String) {
        _state.value = _state.value.copy(selectedPeriodId = periodId)
        runReport()
    }

    fun setAsOfDate(value: String) {
        _state.value = _state.value.copy(asOfDate = value)
    }

    fun setFromDate(value: String) {
        _state.value = _state.value.copy(fromDate = value)
    }

    fun setToDate(value: String) {
        _state.value = _state.value.copy(toDate = value)
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, notice = null)
    }

    fun runReport() {
        val s = _state.value
        if (s.businessId.isBlank()) return
        scope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            when (s.activeTab) {
                ReportTab.TRIAL_BALANCE ->
                    reportNetwork
                        .trialBalanceAsync(
                            TrialBalanceRequest(
                                businessId = s.businessId,
                                asOfDate = s.asOfDate,
                                fiscalPeriodId = s.selectedPeriodId,
                            ),
                        ).fold(
                            onSuccess = { r -> _state.value = _state.value.copy(isLoading = false, trialBalance = r.payload) },
                            onFailure = { e -> _state.value = _state.value.copy(isLoading = false, error = e.message) },
                        )
                ReportTab.PROFIT_AND_LOSS ->
                    reportNetwork
                        .profitAndLossAsync(
                            ProfitAndLossRequest(
                                businessId = s.businessId,
                                fromDate = s.fromDate,
                                toDate = s.toDate,
                                fiscalPeriodId = s.selectedPeriodId,
                            ),
                        ).fold(
                            onSuccess = { r -> _state.value = _state.value.copy(isLoading = false, profitAndLoss = r.payload) },
                            onFailure = { e -> _state.value = _state.value.copy(isLoading = false, error = e.message) },
                        )
                ReportTab.BALANCE_SHEET ->
                    reportNetwork
                        .balanceSheetAsync(
                            BalanceSheetRequest(
                                businessId = s.businessId,
                                asOfDate = s.asOfDate,
                                fiscalPeriodId = s.selectedPeriodId,
                            ),
                        ).fold(
                            onSuccess = { r -> _state.value = _state.value.copy(isLoading = false, balanceSheet = r.payload) },
                            onFailure = { e -> _state.value = _state.value.copy(isLoading = false, error = e.message) },
                        )
                ReportTab.CASH_FLOW ->
                    reportNetwork
                        .cashFlowAsync(
                            CashFlowRequest(
                                businessId = s.businessId,
                                fromDate = s.fromDate,
                                toDate = s.toDate,
                                fiscalPeriodId = s.selectedPeriodId,
                            ),
                        ).fold(
                            onSuccess = { r -> _state.value = _state.value.copy(isLoading = false, cashFlow = r.payload) },
                            onFailure = { e -> _state.value = _state.value.copy(isLoading = false, error = e.message) },
                        )
                ReportTab.RATIOS ->
                    reportNetwork
                        .ratioAnalysisAsync(
                            RatioAnalysisRequest(
                                businessId = s.businessId,
                                fiscalPeriodId = s.selectedPeriodId,
                                fromDate = s.fromDate,
                                toDate = s.toDate,
                            ),
                        ).fold(
                            onSuccess = { r -> _state.value = _state.value.copy(isLoading = false, ratios = r.payload) },
                            onFailure = { e -> _state.value = _state.value.copy(isLoading = false, error = e.message) },
                        )
            }
        }
    }

    fun export(format: String) {
        val s = _state.value
        if (s.businessId.isBlank()) return
        scope.launch {
            _state.value = s.copy(isExporting = true, error = null)
            reportNetwork
                .exportReportAsync(
                    ExportReportRequest(
                        businessId = s.businessId,
                        reportType = s.activeTab.exportType,
                        format = format,
                        parameters =
                            mapOf(
                                "asOfDate" to s.asOfDate,
                                "fromDate" to s.fromDate,
                                "toDate" to s.toDate,
                                "fiscalPeriodId" to s.selectedPeriodId,
                            ),
                    ),
                ).fold(
                    onSuccess = { r ->
                        _state.value =
                            _state.value.copy(
                                isExporting = false,
                                exportFileUrl = r.fileUrl,
                                exportFileName = r.fileName,
                                notice = if (r.fileUrl.isBlank()) "Export queued (${r.status})" else "Export ready: ${r.fileName}",
                            )
                    },
                    onFailure = { e -> _state.value = _state.value.copy(isExporting = false, error = e.message) },
                )
        }
    }

    private fun fiscalYearStart(todayIso: String): String {
        val year = todayIso.substringBefore("-").toIntOrNull() ?: return todayIso
        val month = todayIso.substringAfter("-").substringBefore("-").toIntOrNull() ?: 4
        return (if (month >= 4) year else year - 1).toString().padStart(4, '0') + "-04-01"
    }
}
