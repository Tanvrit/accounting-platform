package com.tanvrit.accounting.screens.tdsCenter

import com.tanvrit.accounting.data.AccountingSettingsStore
import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.network.TaxNetwork
import com.tanvrit.accounting.repository.FiscalPeriodRepository
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.FiscalPeriod
import com.tanvrit.core.feature.accounting.model.Form16
import com.tanvrit.core.feature.accounting.model.Form16A
import com.tanvrit.core.feature.accounting.model.TdsReturn26Q
import com.tanvrit.core.feature.accounting.network.GenerateForm16Request
import com.tanvrit.core.feature.accounting.network.GenerateForm16aRequest
import com.tanvrit.core.feature.accounting.network.GenerateTdsReturnRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

enum class TdsTab(
    val label: String,
) {
    RETURNS("Returns"),
    CHALLANS("Challans"),
    CERTIFICATES("Form 16/16A"),
}

data class TdsCenterUiState(
    val businessId: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val activeTab: TdsTab = TdsTab.RETURNS,
    val periods: List<FiscalPeriod> = emptyList(),
    val selectedPeriodId: String = "",
    val tan: String = "",
    val quarter: String = currentQuarter(),
    val financialYear: String = currentFinancialYear(),
    val returnType: String = "26Q",
    val tdsReturn: TdsReturn26Q? = null,
    val fvuPath: String? = null,
    val certificateEmployeePan: String = "",
    val certificateDeducteePan: String = "",
    val certificateSection: String = "194J",
    val form16: Form16? = null,
    val form16a: Form16A? = null,
)

/**
 * TDS Center — 26Q/27Q/24Q returns, challan linkage view, Form 16/16A
 * certificate generation (`TaxNetwork` in the SDK accounting module).
 */
class TdsCenterViewModel : AppViewModel() {
    private val workspace: AccountingWorkspace = TanvritKoin.get()
    private val settingsStore: AccountingSettingsStore = TanvritKoin.get()
    private val taxNetwork = TaxNetwork.shared()
    private val fiscalPeriodRepository: FiscalPeriodRepository = TanvritKoin.get()

    private val _state = MutableStateFlow(TdsCenterUiState(businessId = workspace.businessId.value))
    val state = _state.asStateFlow()

    init {
        scope.launch {
            workspace.businessId.collect { businessId ->
                _state.value = _state.value.copy(businessId = businessId)
                if (businessId.isNotBlank()) {
                    _state.value = _state.value.copy(tan = settingsStore.settings.value.tan)
                    loadPeriods(businessId)
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

    fun selectTab(tab: TdsTab) {
        _state.value = _state.value.copy(activeTab = tab)
    }

    fun selectPeriod(periodId: String) {
        _state.value = _state.value.copy(selectedPeriodId = periodId)
    }

    fun setTan(value: String) {
        _state.value = _state.value.copy(tan = value.trim().uppercase())
        settingsStore.update { it.copy(tan = value.trim().uppercase()) }
    }

    fun setQuarter(value: String) {
        _state.value = _state.value.copy(quarter = value)
    }

    fun setFinancialYear(value: String) {
        _state.value = _state.value.copy(financialYear = value)
    }

    fun setReturnType(value: String) {
        _state.value = _state.value.copy(returnType = value)
    }

    fun setCertificateEmployeePan(value: String) {
        _state.value = _state.value.copy(certificateEmployeePan = value.trim().uppercase())
    }

    fun setCertificateDeducteePan(value: String) {
        _state.value = _state.value.copy(certificateDeducteePan = value.trim().uppercase())
    }

    fun setCertificateSection(value: String) {
        _state.value = _state.value.copy(certificateSection = value.trim())
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, notice = null)
    }

    fun generateReturn() {
        val s = _state.value
        if (s.businessId.isBlank() || s.selectedPeriodId.isBlank()) return
        scope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            taxNetwork
                .generateTdsReturnAsync(
                    GenerateTdsReturnRequest(
                        businessId = s.businessId,
                        fiscalPeriodId = s.selectedPeriodId,
                        tan = s.tan,
                        quarter = s.quarter,
                        financialYear = s.financialYear,
                        returnType = s.returnType,
                    ),
                ).onSuccess { response ->
                    val payload = response.payload
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            tdsReturn = payload,
                            fvuPath = response.fvuPath ?: payload?.fvuPath,
                            notice =
                                payload?.let { tds ->
                                    "${s.returnType} ${tds.status.lowercase()} — ${tds.deductions.size} deduction rows"
                                } ?: "Return request accepted",
                        )
                }.onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = it.message ?: "Return generation failed")
                }
        }
    }

    fun generateForm16() {
        val s = _state.value
        if (s.businessId.isBlank() || s.certificateEmployeePan.isBlank()) return
        scope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            taxNetwork
                .generateForm16Async(
                    GenerateForm16Request(
                        businessId = s.businessId,
                        financialYear = s.financialYear,
                        tan = s.tan,
                        employeePan = s.certificateEmployeePan,
                    ),
                ).onSuccess { response ->
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            form16 = response.payload,
                            notice =
                                if (response.pdfBase64 != null) "Form 16 generated — PDF ready to download" else "Form 16 generated",
                        )
                }.onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = it.message ?: "Form 16 failed")
                }
        }
    }

    fun generateForm16a() {
        val s = _state.value
        if (s.businessId.isBlank() || s.certificateDeducteePan.isBlank()) return
        scope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            taxNetwork
                .generateForm16aAsync(
                    GenerateForm16aRequest(
                        businessId = s.businessId,
                        financialYear = s.financialYear,
                        tan = s.tan,
                        deducteePan = s.certificateDeducteePan,
                        section = s.certificateSection,
                    ),
                ).onSuccess { response ->
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            form16a = response.payload,
                            notice =
                                if (response.pdfBase64 != null) "Form 16A generated — PDF ready to download" else "Form 16A generated",
                        )
                }.onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = it.message ?: "Form 16A failed")
                }
        }
    }
}

private fun currentQuarter(): String {
    val month =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .monthNumber
    return "Q${((month - 4 + 12) % 12) / 3 + 1}"
}

private fun currentFinancialYear(): String {
    val now =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
    val start = if (now.monthNumber >= 4) now.year else now.year - 1
    return "$start-" + ((start + 1) % 100).toString().padStart(2, '0')
}
