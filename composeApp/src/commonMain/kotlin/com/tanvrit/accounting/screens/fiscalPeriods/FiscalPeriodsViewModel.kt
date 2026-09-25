package com.tanvrit.accounting.screens.fiscalPeriods

import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.network.FiscalPeriodNetwork
import com.tanvrit.accounting.repository.FiscalPeriodRepository
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.FiscalPeriod
import com.tanvrit.core.feature.accounting.network.CarryForwardRequest
import com.tanvrit.core.feature.accounting.network.CloseFiscalPeriodRequest
import com.tanvrit.core.feature.accounting.network.CreateFiscalPeriodRequest
import com.tanvrit.core.feature.accounting.network.LockFiscalPeriodRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PeriodEditorState(
    val name: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val parentPeriodId: String = "",
    val visible: Boolean = false,
)

data class FiscalPeriodsUiState(
    val businessId: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val periods: List<FiscalPeriod> = emptyList(),
    val editor: PeriodEditorState = PeriodEditorState(),
    /** Non-null while a destructive confirm (lock/close) is armed. */
    val pendingAction: PendingPeriodAction? = null,
    /** Carry-forward picker state: source period id → target period id. */
    val carrySourceId: String = "",
    val carryTargetId: String = "",
)

data class PendingPeriodAction(
    val periodId: String,
    val kind: Kind,
) {
    enum class Kind { LOCK, CLOSE }
}

/**
 * Fiscal periods — create, lock and close periods, and carry opening balances
 * forward. Mutations go to the server first; the returned period upserts the
 * offline-first [FiscalPeriodRepository] cache.
 */
class FiscalPeriodsViewModel : AppViewModel() {
    private val workspace: AccountingWorkspace = TanvritKoin.get()
    private val fiscalPeriodNetwork = FiscalPeriodNetwork.shared()
    private val fiscalPeriodRepository: FiscalPeriodRepository = TanvritKoin.get()

    private val _state = MutableStateFlow(FiscalPeriodsUiState(businessId = workspace.businessId.value))
    val state = _state.asStateFlow()

    init {
        scope.launch {
            workspace.businessId.collect { businessId ->
                _state.value = FiscalPeriodsUiState(businessId = businessId)
                if (businessId.isNotBlank()) refresh()
            }
        }
    }

    fun refresh() {
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            runCatching { fiscalPeriodRepository.findByBusinessId(businessId) }
                .onSuccess { periods ->
                    _state.value =
                        _state.value.copy(isLoading = false, periods = periods.sortedByDescending { it.startDate })
                }.onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = it.message ?: "Failed to load periods")
                }
        }
    }

    fun openEditor() {
        _state.value = _state.value.copy(editor = PeriodEditorState(visible = true))
    }

    fun closeEditor() {
        _state.value = _state.value.copy(editor = PeriodEditorState())
    }

    fun updateEditor(transform: (PeriodEditorState) -> PeriodEditorState) {
        _state.value = _state.value.copy(editor = transform(_state.value.editor))
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, notice = null)
    }

    fun createPeriod() {
        val editor = _state.value.editor
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        if (editor.name.isBlank() || editor.startDate.isBlank() || editor.endDate.isBlank()) {
            _state.value = _state.value.copy(error = "Name, start date and end date are required")
            return
        }
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            fiscalPeriodNetwork
                .createFiscalPeriodAsync(
                    CreateFiscalPeriodRequest(
                        businessId = businessId,
                        name = editor.name.trim(),
                        startDate = editor.startDate,
                        endDate = editor.endDate,
                        parentPeriodId = editor.parentPeriodId,
                    ),
                ).onSuccess { response ->
                    response.payload?.let { runCatching { fiscalPeriodRepository.insert(it) } }
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            notice = "Period \"${editor.name.trim()}\" created",
                            editor = PeriodEditorState(),
                        )
                    refresh()
                }.onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = it.message ?: "Create failed")
                }
        }
    }

    /** Locks are not destructive-destructive but block posting — confirm first. */
    fun armAction(
        periodId: String,
        kind: PendingPeriodAction.Kind,
    ) {
        _state.value = _state.value.copy(pendingAction = PendingPeriodAction(periodId, kind))
    }

    fun disarmAction() {
        _state.value = _state.value.copy(pendingAction = null)
    }

    fun confirmPendingAction() {
        val pending = _state.value.pendingAction ?: return
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        _state.value = _state.value.copy(pendingAction = null)
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result =
                when (pending.kind) {
                    PendingPeriodAction.Kind.LOCK ->
                        fiscalPeriodNetwork.lockFiscalPeriodAsync(
                            LockFiscalPeriodRequest(
                                id = pending.periodId,
                                businessId = businessId,
                                lockedBy = workspace.currentUserId(),
                            ),
                        )
                    PendingPeriodAction.Kind.CLOSE ->
                        fiscalPeriodNetwork.closeFiscalPeriodAsync(
                            CloseFiscalPeriodRequest(
                                id = pending.periodId,
                                businessId = businessId,
                                closedBy = workspace.currentUserId(),
                                carryForward = true,
                            ),
                        )
                }
            result
                .onSuccess { response ->
                    response.payload?.let { runCatching { fiscalPeriodRepository.update(it) } }
                    val verb = if (pending.kind == PendingPeriodAction.Kind.LOCK) "locked" else "closed"
                    _state.value = _state.value.copy(isLoading = false, notice = "Period $verb")
                    refresh()
                }.onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = it.message ?: "Action failed")
                }
        }
    }

    fun setCarrySource(id: String) {
        _state.value = _state.value.copy(carrySourceId = id)
    }

    fun setCarryTarget(id: String) {
        _state.value = _state.value.copy(carryTargetId = id)
    }

    fun carryForward() {
        val s = _state.value
        if (s.businessId.isBlank() || s.carrySourceId.isBlank() || s.carryTargetId.isBlank()) {
            _state.value = s.copy(error = "Pick the source (closed) and target (open) periods")
            return
        }
        scope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            fiscalPeriodNetwork
                .carryForwardFiscalPeriodAsync(
                    CarryForwardRequest(
                        businessId = s.businessId,
                        fromPeriodId = s.carrySourceId,
                        toPeriodId = s.carryTargetId,
                        carriedForwardBy = workspace.currentUserId(),
                    ),
                ).onSuccess { response ->
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            notice = "Opening balances carried forward (${response.payload?.name ?: "target"} period)",
                        )
                    refresh()
                }.onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = it.message ?: "Carry forward failed")
                }
        }
    }
}
