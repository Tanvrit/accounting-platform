package com.tanvrit.accounting.screens.gstCenter

import com.tanvrit.accounting.data.AccountingSettingsStore
import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.network.TaxNetwork
import com.tanvrit.accounting.repository.FiscalPeriodRepository
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.FiscalPeriod
import com.tanvrit.core.feature.accounting.model.Gstr1Return
import com.tanvrit.core.feature.accounting.model.Gstr3bReturn
import com.tanvrit.core.feature.accounting.network.GenerateEinvoiceRequest
import com.tanvrit.core.feature.accounting.network.GenerateEwayBillRequest
import com.tanvrit.core.feature.accounting.network.GenerateGstr1Request
import com.tanvrit.core.feature.accounting.network.GenerateGstr3bRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class GstTab(
    val label: String,
) {
    RETURNS("Returns"),
    EINVOICE("E-Invoice"),
    EWAY_BILL("E-Way Bill"),
    HEALTH("Health"),
}

data class GstCenterUiState(
    val businessId: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val activeTab: GstTab = GstTab.RETURNS,
    val periods: List<FiscalPeriod> = emptyList(),
    val selectedPeriodId: String = "",
    val gstin: String = "",
    val gstr1: Gstr1Return? = null,
    val gstr3b: Gstr3bReturn? = null,
    val gstr1ValidationErrors: List<String> = emptyList(),
    val einvoiceVoucherId: String = "",
    val einvoiceIrn: String = "",
    val einvoiceAck: String = "",
    val ewayVoucherId: String = "",
    val ewayBillNumber: String = "",
    val ewayValidUpto: String = "",
)

/**
 * GST Center — GSTR-1 / GSTR-3B return generation, e-invoice (IRN) and e-way
 * bill workflows against the server-side GST engine (`TaxNetwork` in the SDK
 * accounting module). The business GSTIN defaults from Settings.
 */
class GstCenterViewModel : AppViewModel() {
    private val workspace: AccountingWorkspace = TanvritKoin.get()
    private val settingsStore: AccountingSettingsStore = TanvritKoin.get()
    private val taxNetwork = TaxNetwork.shared()
    private val fiscalPeriodRepository: FiscalPeriodRepository = TanvritKoin.get()

    private val _state = MutableStateFlow(GstCenterUiState(businessId = workspace.businessId.value))
    val state = _state.asStateFlow()

    init {
        scope.launch {
            workspace.businessId.collect { businessId ->
                _state.value = _state.value.copy(businessId = businessId)
                if (businessId.isNotBlank()) {
                    loadPeriods(businessId)
                    _state.value = _state.value.copy(gstin = settingsStore.settings.value.gstin)
                }
            }
        }
    }

    private suspend fun loadPeriods(businessId: String) {
        runCatching { fiscalPeriodRepository.findByBusinessId(businessId) }
            .onSuccess { periods ->
                _state.value =
                    _state.value.copy(
                        periods = periods.sortedByDescending { it.startDate },
                        selectedPeriodId =
                            _state.value.selectedPeriodId.ifBlank {
                                periods.sortedByDescending { it.startDate }.firstOrNull()?.id ?: ""
                            },
                    )
            }
    }

    fun selectTab(tab: GstTab) {
        _state.value = _state.value.copy(activeTab = tab)
    }

    fun selectPeriod(periodId: String) {
        _state.value = _state.value.copy(selectedPeriodId = periodId)
    }

    fun setGstin(value: String) {
        _state.value = _state.value.copy(gstin = value.trim().uppercase())
        settingsStore.update { it.copy(gstin = value.trim().uppercase()) }
    }

    fun setEinvoiceVoucherId(value: String) {
        _state.value = _state.value.copy(einvoiceVoucherId = value.trim())
    }

    fun setEwayVoucherId(value: String) {
        _state.value = _state.value.copy(ewayVoucherId = value.trim())
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, notice = null)
    }

    fun generateGstr1(validateOnly: Boolean) {
        val s = _state.value
        if (s.businessId.isBlank() || s.selectedPeriodId.isBlank()) return
        scope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            taxNetwork
                .generateGstr1Async(
                    GenerateGstr1Request(
                        businessId = s.businessId,
                        fiscalPeriodId = s.selectedPeriodId,
                        gstin = s.gstin,
                        validateOnly = validateOnly,
                    ),
                ).onSuccess { response ->
                    val payload = response.payload
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            gstr1 = payload,
                            gstr1ValidationErrors = response.validationErrors,
                            notice =
                                when {
                                    response.validationErrors.isNotEmpty() ->
                                        "GSTR-1 generated with ${response.validationErrors.size} validation issue(s)"
                                    payload != null ->
                                        "GSTR-1 ${payload.status.lowercase()} — " +
                                            "${payload.sections.b2b.size} B2B, ${payload.sections.b2cs.size} B2C-small, " +
                                            "${payload.sections.cdnr.size} credit/debit notes"
                                    else -> "GSTR-1 request accepted"
                                },
                        )
                }.onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = it.message ?: "GSTR-1 failed")
                }
        }
    }

    fun generateGstr3b() {
        val s = _state.value
        if (s.businessId.isBlank() || s.selectedPeriodId.isBlank()) return
        scope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            taxNetwork
                .generateGstr3bAsync(
                    GenerateGstr3bRequest(
                        businessId = s.businessId,
                        fiscalPeriodId = s.selectedPeriodId,
                        gstin = s.gstin,
                    ),
                ).onSuccess { response ->
                    val payload = response.payload
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            gstr3b = payload,
                            notice =
                                payload?.let { "GSTR-3B ${it.status.lowercase()} — ITC net ${it.itcEligibility.netItc}" }
                                    ?: "GSTR-3B request accepted",
                        )
                }.onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = it.message ?: "GSTR-3B failed")
                }
        }
    }

    fun generateEinvoice() {
        val s = _state.value
        if (s.businessId.isBlank() || s.einvoiceVoucherId.isBlank()) return
        scope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            taxNetwork
                .generateEinvoiceAsync(
                    GenerateEinvoiceRequest(businessId = s.businessId, voucherId = s.einvoiceVoucherId),
                ).onSuccess { response ->
                    val payload = response.payload
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            einvoiceIrn = payload?.irn.orEmpty(),
                            einvoiceAck = payload?.acknowledgmentNumber.orEmpty(),
                            notice = payload?.errorMessage ?: "E-invoice IRN generated",
                            error = payload?.errorCode?.let { code -> "E-invoice error $code" },
                        )
                }.onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = it.message ?: "E-invoice failed")
                }
        }
    }

    fun generateEwayBill() {
        val s = _state.value
        if (s.businessId.isBlank() || s.ewayVoucherId.isBlank()) return
        scope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            taxNetwork
                .generateEwayBillAsync(
                    GenerateEwayBillRequest(businessId = s.businessId, voucherId = s.ewayVoucherId),
                ).onSuccess { response ->
                    val payload = response.payload
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            ewayBillNumber = payload?.ewayBillNumber.orEmpty(),
                            ewayValidUpto = payload?.validUpto.orEmpty(),
                            notice = payload?.errorMessage ?: "E-way bill generated",
                        )
                }.onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = it.message ?: "E-way bill failed")
                }
        }
    }
}
