package com.tanvrit.accounting.screens.recurring

import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.data.RecurringLeg
import com.tanvrit.accounting.data.RecurringSchedule
import com.tanvrit.accounting.data.RecurringVoucherStore
import com.tanvrit.accounting.repository.AccountRepository
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.VoucherType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** The create/edit sheet's mutable form state — amounts stay strings as typed. */
data class RecurringFormState(
    val id: String = "",
    val name: String = "",
    val voucherType: VoucherType = VoucherType.JOURNAL,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val startDate: String = "",
    val endDate: String = "",
    val enabled: Boolean = true,
    val legs: List<RecurringLeg> = listOf(RecurringLeg(), RecurringLeg()),
)

data class RecurringVouchersUiState(
    val businessId: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val schedules: List<RecurringSchedule> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val showEditor: Boolean = false,
    val form: RecurringFormState = RecurringFormState(),
    val pendingDelete: RecurringSchedule? = null,
    val lastRun: Map<String, Int> = emptyMap(),
)

/** Recurring vouchers (roadmap #2): client-local schedules → draft vouchers. */
class RecurringVouchersViewModel : AppViewModel() {
    private val workspace: AccountingWorkspace = TanvritKoin.get()
    private val store: RecurringVoucherStore = TanvritKoin.get()
    private val engine: RecurringVoucherEngine = TanvritKoin.get()
    private val accountRepository: AccountRepository = TanvritKoin.get()

    private val _state = MutableStateFlow(RecurringVouchersUiState(businessId = workspace.businessId.value))
    val state = _state.asStateFlow()

    init {
        scope.launch {
            workspace.businessId.collect { businessId ->
                store.selectBusiness(businessId)
                _state.value = _state.value.copy(businessId = businessId)
                if (businessId.isNotBlank()) refresh()
            }
        }
        scope.launch {
            store.schedules.collect { schedules ->
                _state.value = _state.value.copy(schedules = schedules)
            }
        }
    }

    fun refresh() {
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val accounts =
                runCatching { accountRepository.findByBusinessId(businessId, null, null, 0, PAGE_SIZE) }
                    .getOrDefault(emptyList())
            _state.value = _state.value.copy(isLoading = false, accounts = accounts.sortedBy { it.name.lowercase() })
        }
    }

    // ---- editor ----

    fun openCreate() {
        val today = todayIso()
        _state.value =
            _state.value.copy(
                showEditor = true,
                form = RecurringFormState(startDate = today),
            )
    }

    fun openEdit(schedule: RecurringSchedule) {
        _state.value =
            _state.value.copy(
                showEditor = true,
                form =
                    RecurringFormState(
                        id = schedule.id,
                        name = schedule.name,
                        voucherType = VoucherType.entries.firstOrNull { it.code == schedule.voucherType } ?: VoucherType.JOURNAL,
                        frequency = RecurrenceFrequency.fromCodeOrDefault(schedule.frequency),
                        startDate = schedule.startDate,
                        endDate = schedule.endDate.orEmpty(),
                        enabled = schedule.enabled,
                        legs = schedule.legs.ifEmpty { listOf(RecurringLeg(), RecurringLeg()) },
                    ),
            )
    }

    fun closeEditor() {
        _state.value = _state.value.copy(showEditor = false, form = RecurringFormState())
    }

    fun updateForm(transform: (RecurringFormState) -> RecurringFormState) {
        _state.value = _state.value.copy(form = transform(_state.value.form))
    }

    fun saveForm() {
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        val form = _state.value.form
        if (form.name.isBlank()) {
            _state.value = _state.value.copy(error = "Name is required")
            return
        }
        if (form.startDate.isBlank()) {
            _state.value = _state.value.copy(error = "Start date is required (yyyy-MM-dd)")
            return
        }
        val saved =
            store.save(
                RecurringSchedule(
                    id = form.id,
                    businessId = businessId,
                    name = form.name.trim(),
                    voucherType = form.voucherType.code,
                    frequency = form.frequency.code,
                    legs = form.legs,
                    startDate = form.startDate.trim(),
                    endDate = form.endDate.trim().ifBlank { null },
                    nextDue = form.startDate.trim(),
                    enabled = form.enabled,
                ),
            )
        _state.value =
            _state.value.copy(showEditor = false, form = RecurringFormState(), notice = "Saved “${saved.name}”")
    }

    fun askDelete(schedule: RecurringSchedule) {
        _state.value = _state.value.copy(pendingDelete = schedule)
    }

    fun dismissDelete() {
        _state.value = _state.value.copy(pendingDelete = null)
    }

    fun confirmDelete() {
        val target = _state.value.pendingDelete ?: return
        store.delete(target.id)
        _state.value = _state.value.copy(pendingDelete = null, notice = "Deleted “${target.name}”")
    }

    fun toggleEnabled(schedule: RecurringSchedule) {
        store.update(schedule.copy(enabled = !schedule.enabled))
    }

    // ---- runs ----

    fun processDue() {
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        scope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val outcomes = engine.processDueSchedules(businessId)
            val drafted = outcomes.sumOf { it.draftedVoucherIds.size }
            val errors = outcomes.filter { it.error != null }
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    notice = if (drafted > 0) "Drafted $drafted voucher(s) — review in Approvals" else null,
                    error = errors.firstOrNull()?.let { "${it.scheduleId}: ${it.error}" },
                    lastRun = outcomes.associate { it.scheduleId to it.draftedVoucherIds.size },
                )
        }
    }

    fun runNow(schedule: RecurringSchedule) {
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        scope.launch {
            val outcome = engine.runScheduleNow(businessId, schedule.id)
            _state.value =
                _state.value.copy(
                    notice =
                        outcome.error ?: if (outcome.draftedVoucherIds.isEmpty()) {
                            "Nothing due for “${schedule.name}” today"
                        } else {
                            "Drafted ${outcome.draftedVoucherIds.size} voucher(s) from “${schedule.name}”"
                        },
                    lastRun = _state.value.lastRun + (schedule.id to outcome.draftedVoucherIds.size),
                )
        }
    }

    fun clearNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun todayIso(): String =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()

    private companion object {
        const val PAGE_SIZE = 500
    }
}
