package com.tanvrit.accounting.screens.reconciliation

import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.network.ReconciliationNetwork
import com.tanvrit.accounting.repository.AccountRepository
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.AccountType
import com.tanvrit.core.feature.accounting.model.BankReconciliation
import com.tanvrit.core.feature.accounting.model.MatchResult
import com.tanvrit.core.feature.accounting.network.AutoMatchRequest
import com.tanvrit.core.feature.accounting.network.CompleteReconciliationRequest
import com.tanvrit.core.feature.accounting.network.ImportBankStatementRequest
import com.tanvrit.core.feature.accounting.network.ManualMatchRequest
import com.tanvrit.core.feature.accounting.network.RetrieveReconciliationRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ReconciliationTab(
    val label: String,
) {
    IMPORT("Import"),
    MATCH("Match"),
    REVIEW("Review"),
    HISTORY("History"),
}

data class ReconciliationUiState(
    val businessId: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val activeTab: ReconciliationTab = ReconciliationTab.IMPORT,
    val bankAccounts: List<Account> = emptyList(),
    val bankAccountId: String = "",
    val csvInput: String = "",
    val parseErrors: List<String> = emptyList(),
    val sessionId: String = "",
    val importedCount: Int = 0,
    val matchResult: MatchResult? = null,
    /** Tap-to-select pair for a manual match. */
    val selectedStatementItemId: String = "",
    val selectedBookItemId: String = "",
    val completed: BankReconciliation? = null,
    val history: List<BankReconciliation> = emptyList(),
)

/**
 * Bank Reconciliation — import statement → auto-match → manual match →
 * review → complete. Sessions live server-side; [sessionId] is the handle
 * returned by the import call. History reads the past reconciliations list.
 */
class ReconciliationViewModel : AppViewModel() {
    private val workspace: AccountingWorkspace = TanvritKoin.get()
    private val reconciliationNetwork = ReconciliationNetwork.shared()
    private val accountRepository: AccountRepository = TanvritKoin.get()

    private val _state = MutableStateFlow(ReconciliationUiState(businessId = workspace.businessId.value))
    val state = _state.asStateFlow()

    init {
        scope.launch {
            workspace.businessId.collect { businessId ->
                _state.value = _state.value.copy(businessId = businessId)
                if (businessId.isNotBlank()) {
                    loadBankAccounts(businessId)
                    loadHistory(businessId)
                }
            }
        }
    }

    private suspend fun loadBankAccounts(businessId: String) {
        runCatching { accountRepository.findByType(businessId, AccountType.ASSET) }
            .onSuccess { assets ->
                val banks = assets.filter { it.name.contains("bank", ignoreCase = true) }.ifEmpty { assets }
                _state.value =
                    _state.value.copy(
                        bankAccounts = banks,
                        bankAccountId = _state.value.bankAccountId.ifBlank { banks.firstOrNull()?.id ?: "" },
                    )
            }
    }

    private suspend fun loadHistory(businessId: String) {
        runCatching {
            reconciliationNetwork.retrieveReconciliationAsync(
                RetrieveReconciliationRequest(businessId = businessId, bankAccountId = _state.value.bankAccountId),
            )
        }.onSuccess { result ->
            result.onSuccess { response -> _state.value = _state.value.copy(history = response.payload) }
        }
    }

    fun selectTab(tab: ReconciliationTab) {
        _state.value = _state.value.copy(activeTab = tab)
        val businessId = _state.value.businessId
        if (tab == ReconciliationTab.HISTORY && businessId.isNotBlank()) {
            scope.launch { loadHistory(businessId) }
        }
    }

    fun selectBankAccount(accountId: String) {
        _state.value = _state.value.copy(bankAccountId = accountId)
    }

    fun setCsvInput(value: String) {
        _state.value = _state.value.copy(csvInput = value)
    }

    fun selectStatementItem(itemId: String) {
        _state.value =
            _state.value.copy(selectedStatementItemId = if (_state.value.selectedStatementItemId == itemId) "" else itemId)
    }

    fun selectBookItem(itemId: String) {
        _state.value =
            _state.value.copy(selectedBookItemId = if (_state.value.selectedBookItemId == itemId) "" else itemId)
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, notice = null)
    }

    fun importStatement() {
        val s = _state.value
        if (s.businessId.isBlank() || s.bankAccountId.isBlank()) {
            _state.value = s.copy(error = "Pick a bank account first")
            return
        }
        val (statement, errors) = StatementCsvParser.toStatement(s.bankAccountId, s.csvInput)
        if (statement == null) {
            _state.value = s.copy(parseErrors = errors)
            return
        }
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, parseErrors = errors)
            reconciliationNetwork
                .importBankStatementAsync(
                    ImportBankStatementRequest(
                        businessId = s.businessId,
                        bankAccountId = s.bankAccountId,
                        statement = statement,
                        format = "CSV",
                    ),
                ).onSuccess { response ->
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            sessionId = response.sessionId,
                            importedCount = response.importedCount,
                            notice =
                                "Imported ${response.importedCount} transactions" +
                                    if (errors.isNotEmpty()) " (${errors.size} row(s) skipped)" else "",
                            activeTab = ReconciliationTab.MATCH,
                        )
                }.onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = it.message ?: "Import failed")
                }
        }
    }

    fun autoMatch() =
        withSession { s, sessionId ->
            reconciliationNetwork
                .autoMatchAsync(
                    AutoMatchRequest(businessId = s.businessId, sessionId = sessionId),
                ).onSuccess { response ->
                    _state.value =
                        _state.value.copy(
                            matchResult = response.payload,
                            notice =
                                "Auto-match: ${response.payload?.totalMatches ?: 0} matched, " +
                                    "${response.payload?.totalUnmatched ?: 0} unmatched",
                        )
                }.onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Auto-match failed")
                }
        }

    fun manualMatch() {
        val s = _state.value
        if (s.selectedStatementItemId.isBlank() || s.selectedBookItemId.isBlank()) {
            _state.value = s.copy(error = "Select one statement item and one book item to match")
            return
        }
        withSession { state, sessionId ->
            reconciliationNetwork
                .manualMatchAsync(
                    ManualMatchRequest(
                        businessId = state.businessId,
                        sessionId = sessionId,
                        statementItemId = state.selectedStatementItemId,
                        bookItemId = state.selectedBookItemId,
                        matchedBy = workspace.currentUserId(),
                    ),
                ).onSuccess { response ->
                    _state.value =
                        _state.value.copy(
                            matchResult = response.payload,
                            selectedStatementItemId = "",
                            selectedBookItemId = "",
                            notice = "Matched",
                        )
                }.onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Match failed")
                }
        }
    }

    fun completeReconciliation() =
        withSession { s, sessionId ->
            reconciliationNetwork
                .completeReconciliationAsync(
                    CompleteReconciliationRequest(
                        businessId = s.businessId,
                        sessionId = sessionId,
                        completedBy = workspace.currentUserId(),
                    ),
                ).onSuccess { response ->
                    _state.value =
                        _state.value.copy(
                            completed = response.payload,
                            notice = "Reconciliation completed",
                            activeTab = ReconciliationTab.REVIEW,
                        )
                }.onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Complete failed")
                }
        }

    /** Runs [block] against the active session, or surfaces "import first". */
    private fun withSession(block: suspend (ReconciliationUiState, String) -> Unit) {
        val s = _state.value
        if (s.sessionId.isBlank()) {
            _state.value = s.copy(error = "Import a statement first")
            return
        }
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            block(s, s.sessionId)
            _state.value = _state.value.copy(isLoading = false)
        }
    }
}
