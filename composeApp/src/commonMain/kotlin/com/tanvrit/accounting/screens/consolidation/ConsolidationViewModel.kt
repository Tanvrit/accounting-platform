package com.tanvrit.accounting.screens.consolidation

import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.network.ConsolidationNetwork
import com.tanvrit.accounting.network.ReportNetwork
import com.tanvrit.accounting.repository.AccountRepository
import com.tanvrit.accounting.repository.FiscalPeriodRepository
import com.tanvrit.accounting.screens.common.parseMoneyInput
import com.tanvrit.accounting.screens.multiCurrency.FxFormat
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.ConsolidatedReport
import com.tanvrit.core.feature.accounting.model.ConsolidationGroup
import com.tanvrit.core.feature.accounting.model.FiscalPeriod
import com.tanvrit.core.feature.accounting.model.FiscalPeriodStatus
import com.tanvrit.core.feature.accounting.network.ConsolidatedReportRequest
import com.tanvrit.core.feature.accounting.network.CreateConsolidationGroupRequest
import com.tanvrit.core.feature.accounting.network.CreateEliminationEntryRequest
import com.tanvrit.core.feature.accounting.network.GenerateConsolidationRequest
import com.tanvrit.core.feature.accounting.network.RetrieveConsolidationRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConsolidationUiState(
    val businessId: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** One-shot notice surfaced under the header; replaced by the next action. */
    val notice: String? = null,
    val groups: List<ConsolidationGroup> = emptyList(),
    val selectedGroupId: String? = null,
    /** OPEN periods eligible for a consolidation run. */
    val openPeriods: List<FiscalPeriod> = emptyList(),
    val periodId: String = "",
    val confirmArmed: Boolean = false,
    val generating: Boolean = false,
    /** Last consolidated report returned by the server; null until a run succeeds for this group+period. */
    val report: ConsolidatedReport? = null,
    /** Account cache for the elimination sheet's debit/credit pickers. */
    val accounts: List<Account> = emptyList(),
    // New-group sheet.
    val addGroupOpen: Boolean = false,
    val groupName: String = "",
    val memberIdsText: String = "",
    val addGroupSaving: Boolean = false,
    /** Inline sheet error (create-group or create-elimination), shown verbatim. */
    val sheetError: String? = null,
    // New-elimination sheet (for the selected group).
    val eliminationOpen: Boolean = false,
    val eliminationType: String = ConsolidationViewModel.ELIMINATION_TYPES.first(),
    val debitAccountId: String = "",
    val creditAccountId: String = "",
    val amountText: String = "",
    val description: String = "",
    val linkedVoucherId: String = "",
    val eliminationSaving: Boolean = false,
) {
    val selectedGroup: ConsolidationGroup? get() = groups.firstOrNull { it.id == selectedGroupId }
    val isEmpty: Boolean get() = !isLoading && error == null && groups.isEmpty()
    val parsedMemberIds: List<String> get() = parseMemberBusinessIds(memberIdsText).filter { it != businessId }
    val accountsById: Map<String, Account> get() = accounts.associateBy { it.id }
}

/**
 * Group consolidation viewer (roadmap #4) — group management, a server-side
 * consolidation run, a rendered [ConsolidatedReport] and elimination entries.
 * All mutations and the report itself go through the SDK's
 * [ConsolidationNetwork] / [ReportNetwork]; nothing is computed locally.
 *
 * Honest scoping (also stated on the panel): consolidation runs for the
 * **current business** against the group definition — [GenerateConsolidationRequest]
 * is group+period scoped, but `generateConsolidationAsync` returns the group
 * list ([com.tanvrit.core.feature.accounting.network.ConsolidationGroupListResponse]),
 * not the report. The [ConsolidatedReport] comes from
 * [ReportNetwork.consolidatedReportAsync], whose [ConsolidatedReportRequest]
 * is business+period scoped — there is no `groupId` on it, and no
 * `retrieveEliminations` endpoint exists; eliminations render from the
 * report payload's `eliminations` list.
 */
class ConsolidationViewModel : AppViewModel() {
    private val workspace: AccountingWorkspace = TanvritKoin.get()
    private val accountRepository: AccountRepository = TanvritKoin.get()
    private val fiscalPeriodRepository: FiscalPeriodRepository = TanvritKoin.get()
    private val consolidationNetwork = ConsolidationNetwork.shared()
    private val reportNetwork = ReportNetwork.shared()

    private val _state = MutableStateFlow(ConsolidationUiState(businessId = workspace.businessId.value))
    val state = _state.asStateFlow()

    init {
        scope.launch {
            workspace.businessId.collect { businessId ->
                _state.value = _state.value.copy(businessId = businessId, selectedGroupId = null, report = null)
                if (businessId.isNotBlank()) {
                    refresh()
                    loadOpenPeriods()
                    loadAccounts()
                }
            }
        }
    }

    // ---- groups ----

    /** Reloads the group's list from the server (no offline cache exists for consolidation). */
    fun refresh() {
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            consolidationNetwork
                .retrieveConsolidationAsync(RetrieveConsolidationRequest(businessId = businessId))
                .onSuccess { response ->
                    val groups = response.payload.sortedBy { it.name.lowercase() }
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            groups = groups,
                            selectedGroupId =
                                _state.value.selectedGroupId?.takeIf { id -> groups.any { it.id == id } },
                        )
                }.onFailure {
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            error = it.message ?: "Failed to load consolidation groups",
                        )
                }
        }
    }

    fun selectGroup(groupId: String) {
        _state.value =
            _state.value.copy(
                selectedGroupId = if (_state.value.selectedGroupId == groupId) null else groupId,
                report = null,
                confirmArmed = false,
                notice = null,
            )
    }

    fun openAddGroup() {
        _state.value =
            _state.value.copy(
                addGroupOpen = true,
                groupName = "",
                memberIdsText = "",
                sheetError = null,
                notice = null,
            )
    }

    fun closeAddGroup() {
        if (_state.value.addGroupSaving) return
        _state.value = _state.value.copy(addGroupOpen = false)
    }

    fun onGroupNameChange(value: String) {
        _state.value = _state.value.copy(groupName = value, sheetError = null)
    }

    fun onMemberIdsTextChange(value: String) {
        _state.value =
            _state.value.copy(
                memberIdsText = value,
                sheetError = null,
            )
    }

    /**
     * Creates the group via [ConsolidationNetwork.createConsolidationGroupAsync]
     * — the current business is the parent (there is no business-directory
     * lookup in this app context, so member ids come from the sheet's
     * comma-separated textarea, de-duplicated and with the parent excluded).
     */
    fun createGroup() {
        val state = _state.value
        if (state.businessId.isBlank()) return
        val members = state.parsedMemberIds
        val error = validateGroupForm(state.groupName, members)
        if (error != null) {
            _state.value = state.copy(sheetError = error)
            return
        }
        scope.launch {
            _state.value = _state.value.copy(addGroupSaving = true, sheetError = null)
            consolidationNetwork
                .createConsolidationGroupAsync(
                    CreateConsolidationGroupRequest(
                        businessId = state.businessId,
                        name = state.groupName.trim(),
                        parentBusinessId = state.businessId,
                        subsidiaryBusinessIds = members,
                    ),
                ).onSuccess { response ->
                    _state.value =
                        _state.value.copy(
                            addGroupSaving = false,
                            addGroupOpen = false,
                            notice = "Group \"${state.groupName.trim()}\" created",
                        )
                    refresh()
                    response.payload?.let { created ->
                        _state.value = _state.value.copy(selectedGroupId = created.id)
                    }
                }.onFailure {
                    _state.value =
                        _state.value.copy(addGroupSaving = false, sheetError = it.message ?: "Create failed")
                }
        }
    }

    // ---- consolidate ----

    /** OPEN periods for the picker — the same repository read the multicurrency screen makes. */
    private fun loadOpenPeriods() {
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        scope.launch {
            runCatching { fiscalPeriodRepository.findByBusinessIdAndStatus(businessId, FiscalPeriodStatus.OPEN) }
                .onSuccess { periods ->
                    val sorted = periods.sortedByDescending { it.startDate }
                    _state.value =
                        _state.value.copy(
                            openPeriods = sorted,
                            periodId =
                                _state.value.periodId
                                    .takeIf { id -> sorted.any { it.id == id } }
                                    ?: sorted.firstOrNull()?.id.orEmpty(),
                        )
                }.onFailure {
                    if (_state.value.error == null) {
                        _state.value = _state.value.copy(error = it.message ?: "Failed to load open periods")
                    }
                }
        }
    }

    private fun loadAccounts() {
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        scope.launch {
            runCatching { accountRepository.findByBusinessId(businessId, null, true, 0, PAGE_SIZE) }
                .onSuccess { accounts ->
                    _state.value = _state.value.copy(accounts = accounts.sortedBy { it.accountCode })
                }.onFailure {
                    if (_state.value.error == null) {
                        _state.value = _state.value.copy(error = it.message ?: "Failed to load accounts")
                    }
                }
        }
    }

    fun onPeriodChange(periodId: String) {
        _state.value = _state.value.copy(periodId = periodId)
    }

    fun armConsolidation() {
        val state = _state.value
        if (state.businessId.isBlank() || state.selectedGroupId == null) return
        if (state.periodId.isBlank()) {
            _state.value = state.copy(error = "No OPEN fiscal period — create and open one in Periods first")
            return
        }
        _state.value = state.copy(confirmArmed = true)
    }

    fun disarmConsolidation() {
        _state.value = _state.value.copy(confirmArmed = false)
    }

    fun confirmConsolidation() {
        val state = _state.value
        val group = state.selectedGroup ?: return
        if (state.businessId.isBlank() || state.periodId.isBlank()) return
        _state.value = state.copy(confirmArmed = false)
        scope.launch {
            _state.value = _state.value.copy(generating = true, error = null, notice = null)
            consolidationNetwork
                .generateConsolidationAsync(
                    GenerateConsolidationRequest(
                        businessId = state.businessId,
                        groupId = group.id,
                        fiscalPeriodId = state.periodId,
                        reportType = "ALL",
                        includeEliminations = true,
                    ),
                ).onFailure {
                    _state.value =
                        _state.value.copy(generating = false, error = it.message ?: "Consolidation failed")
                }.onSuccess { response ->
                    // The generate response carries the refreshed group list,
                    // not the report — the report is fetched below.
                    if (response.payload.isNotEmpty()) {
                        _state.value = _state.value.copy(groups = response.payload)
                    }
                    _state.value =
                        _state.value.copy(
                            notice = response.message.ifBlank { null },
                        )
                    fetchReport(group, state.periodId)
                }
        }
    }

    /** Fetches the consolidated report for the current group+period and shows it as returned. */
    private suspend fun fetchReport(
        group: ConsolidationGroup,
        periodId: String,
    ) {
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        reportNetwork
            .consolidatedReportAsync(
                ConsolidatedReportRequest(
                    businessId = businessId,
                    fiscalPeriodId = periodId,
                    includeSubsidiaries = true,
                    eliminationMethod = group.method.ifBlank { "FULL" },
                    reportType = "ALL",
                ),
            ).onSuccess { response ->
                _state.value =
                    _state.value.copy(
                        generating = false,
                        report = response.payload,
                        notice =
                            _state.value.notice
                                ?: response.message.ifBlank { null },
                    )
            }.onFailure {
                _state.value =
                    _state.value.copy(
                        generating = false,
                        error = it.message ?: "Failed to load the consolidated report",
                    )
            }
    }

    // ---- eliminations ----

    fun openElimination() {
        if (_state.value.selectedGroupId == null || _state.value.businessId.isBlank()) return
        _state.value =
            _state.value.copy(
                eliminationOpen = true,
                eliminationType = ELIMINATION_TYPES.first(),
                debitAccountId = "",
                creditAccountId = "",
                amountText = "",
                description = "",
                linkedVoucherId = "",
                sheetError = null,
                notice = null,
            )
    }

    fun closeElimination() {
        if (_state.value.eliminationSaving) return
        _state.value = _state.value.copy(eliminationOpen = false)
    }

    fun onEliminationTypeChange(type: String) {
        _state.value = _state.value.copy(eliminationType = type, sheetError = null)
    }

    fun onDebitAccountChange(accountId: String) {
        _state.value = _state.value.copy(debitAccountId = accountId, sheetError = null)
    }

    fun onCreditAccountChange(accountId: String) {
        _state.value = _state.value.copy(creditAccountId = accountId, sheetError = null)
    }

    fun onAmountTextChange(value: String) {
        _state.value = _state.value.copy(amountText = value, sheetError = null)
    }

    fun onDescriptionChange(value: String) {
        _state.value = _state.value.copy(description = value, sheetError = null)
    }

    fun onLinkedVoucherChange(value: String) {
        _state.value = _state.value.copy(linkedVoucherId = value, sheetError = null)
    }

    fun createElimination() {
        val state = _state.value
        val group = state.selectedGroup ?: return
        if (state.businessId.isBlank()) return
        if (state.periodId.isBlank()) {
            _state.value = state.copy(sheetError = "Pick an OPEN fiscal period in the panel first")
            return
        }
        val amount = parseMoneyInput(state.amountText)
        val error = validateEliminationForm(state.debitAccountId, state.creditAccountId, amount)
        if (error != null) {
            _state.value = state.copy(sheetError = error)
            return
        }
        scope.launch {
            _state.value = _state.value.copy(eliminationSaving = true, sheetError = null)
            consolidationNetwork
                .createEliminationEntryAsync(
                    CreateEliminationEntryRequest(
                        businessId = state.businessId,
                        groupId = group.id,
                        fiscalPeriodId = state.periodId,
                        type = state.eliminationType,
                        debitAccountId = state.debitAccountId,
                        creditAccountId = state.creditAccountId,
                        amount = FxFormat.minorToMajor(amount.amountInSmallestUnit),
                        description = eliminationDescription(state.description, state.linkedVoucherId),
                    ),
                ).onSuccess {
                    _state.value =
                        _state.value.copy(
                            eliminationSaving = false,
                            eliminationOpen = false,
                            notice = "Elimination entry recorded",
                        )
                    // Refresh the group list, and re-fetch the on-screen report
                    // so its eliminations reflect the new entry.
                    refresh()
                    val freshGroup = _state.value.groups.firstOrNull { it.id == group.id } ?: group
                    if (_state.value.report != null && _state.value.periodId.isNotBlank()) {
                        fetchReport(freshGroup, _state.value.periodId)
                    }
                }.onFailure {
                    _state.value =
                        _state.value.copy(eliminationSaving = false, sheetError = it.message ?: "Save failed")
                }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, notice = null)
    }

    companion object {
        /** Single-shot window read, matching the page-size convention of the other screens. */
        private const val PAGE_SIZE = 500

        /**
         * Intercompany elimination `type` is a free string on the server —
         * these are the labels offered by the picker, nothing is enum-validated
         * client-side.
         */
        val ELIMINATION_TYPES =
            listOf(
                "INTERCOMPANY_SALE",
                "INTERCOMPANY_EXPENSE",
                "INTERCOMPANY_RECEIVABLE",
                "INTERCOMPANY_PAYABLE",
                "INVESTMENT_IN_SUBSIDIARY",
                "DIVIDEND",
                "OTHER",
            )
    }
}
