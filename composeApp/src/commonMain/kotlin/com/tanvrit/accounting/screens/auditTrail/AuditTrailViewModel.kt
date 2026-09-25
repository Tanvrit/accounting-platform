package com.tanvrit.accounting.screens.auditTrail

import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.network.AuditNetwork
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.AuditLogEntry
import com.tanvrit.core.feature.accounting.network.AccountingAuditLogRequest
import com.tanvrit.core.feature.accounting.network.VerifyAuditChainRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuditTrailUiState(
    val businessId: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val entries: List<AuditLogEntry> = emptyList(),
    val entityTypeFilter: String = "",
    val actionFilter: String = "",
    val dateFrom: String = "",
    val dateTo: String = "",
    val page: Int = 0,
    val totalCount: Long = 0,
    val selected: AuditLogEntry? = null,
    /** Null = not verified this session. */
    val chainValid: Boolean? = null,
    val chainBrokenAt: String? = null,
)

/**
 * Audit Trail — the immutable, hash-chained event log (Rule 11(g) compliant).
 * Filterable reads go through `AuditNetwork.retrieveAuditLog`; "Verify
 * integrity" recomputes the chain server-side via `verifyAuditChain`.
 */
class AuditTrailViewModel : AppViewModel() {
    private val workspace: AccountingWorkspace = TanvritKoin.get()
    private val auditNetwork = AuditNetwork.shared()

    private val _state = MutableStateFlow(AuditTrailUiState(businessId = workspace.businessId.value))
    val state = _state.asStateFlow()

    init {
        scope.launch {
            workspace.businessId.collect { businessId ->
                _state.value = AuditTrailUiState(businessId = businessId)
                if (businessId.isNotBlank()) refresh()
            }
        }
    }

    fun refresh() {
        val s = _state.value
        if (s.businessId.isBlank()) return
        scope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            auditNetwork
                .retrieveAuditLogAsync(
                    AccountingAuditLogRequest(
                        businessId = s.businessId,
                        entityType = s.entityTypeFilter,
                        action = null,
                        userId = "",
                        dateFrom = s.dateFrom,
                        dateTo = s.dateTo,
                        page = s.page,
                    ),
                ).onSuccess { response ->
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            entries =
                                response.payload.filter {
                                    s.actionFilter.isBlank() || it.action.code.equals(s.actionFilter, true)
                                },
                            totalCount = response.totalCount,
                        )
                }.onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = it.message ?: "Failed to load audit log")
                }
        }
    }

    fun setEntityTypeFilter(value: String) {
        _state.value = _state.value.copy(entityTypeFilter = value, page = 0)
        refresh()
    }

    fun setActionFilter(value: String) {
        _state.value = _state.value.copy(actionFilter = value)
    }

    fun setDateFrom(value: String) {
        _state.value = _state.value.copy(dateFrom = value, page = 0)
        refresh()
    }

    fun setDateTo(value: String) {
        _state.value = _state.value.copy(dateTo = value, page = 0)
        refresh()
    }

    fun select(entry: AuditLogEntry?) {
        _state.value = _state.value.copy(selected = entry)
    }

    fun verifyChain() {
        val s = _state.value
        if (s.businessId.isBlank()) return
        scope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            auditNetwork
                .verifyAuditChainAsync(VerifyAuditChainRequest(businessId = s.businessId))
                .onSuccess { response ->
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            chainValid = response.isValid,
                            chainBrokenAt = response.brokenAt,
                        )
                }.onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = it.message ?: "Verification failed")
                }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
