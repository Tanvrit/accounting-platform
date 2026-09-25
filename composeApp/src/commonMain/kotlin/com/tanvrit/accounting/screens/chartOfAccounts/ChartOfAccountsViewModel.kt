package com.tanvrit.accounting.screens.chartOfAccounts

import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.network.AccountNetwork
import com.tanvrit.accounting.repository.AccountRepository
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.AccountType
import com.tanvrit.core.feature.accounting.network.CreateAccountRequest
import com.tanvrit.core.feature.accounting.network.UpdateAccountRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccountEditorState(
    /** Blank id → create; otherwise edit of the existing account. */
    val id: String = "",
    val accountCode: String = "",
    val name: String = "",
    val type: AccountType = AccountType.ASSET,
    val parentAccountId: String = "",
    val description: String = "",
    val openingBalance: String = "",
    val visible: Boolean = false,
)

data class ChartOfAccountsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val businessId: String = "",
    val accounts: List<Account> = emptyList(),
    val searchQuery: String = "",
    val typeFilter: AccountType? = null,
    val expanded: Set<String> = emptySet(),
    val editor: AccountEditorState = AccountEditorState(),
)

/**
 * Chart of Accounts — hierarchical account management for the active business.
 *
 * Reads are offline-first from the SDK [AccountRepository] (local SQLite
 * cache); mutations go through [AccountNetwork] and then upsert the cache so
 * the tree reflects a server push without a separate refetch.
 */
class ChartOfAccountsViewModel : AppViewModel() {
    private val workspace: AccountingWorkspace = TanvritKoin.get()
    private val accountNetwork = AccountNetwork.shared()
    private val accountRepository: AccountRepository = TanvritKoin.get()

    private val _state = MutableStateFlow(ChartOfAccountsUiState(businessId = workspace.businessId.value))
    val state = _state.asStateFlow()

    init {
        scope.launch {
            workspace.businessId.collect { businessId ->
                _state.value = ChartOfAccountsUiState(businessId = businessId)
                if (businessId.isNotBlank()) refresh()
            }
        }
    }

    fun refresh() {
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val cached =
                runCatching {
                    accountRepository.findByBusinessId(businessId, null, null, 0, PAGE_SIZE)
                }
            cached
                .onSuccess { accounts ->
                    _state.value = _state.value.copy(isLoading = false, accounts = accounts)
                }.onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = it.message ?: "Failed to load accounts")
                }
        }
    }

    fun onSearchChange(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun onTypeFilterChange(type: AccountType?) {
        _state.value = _state.value.copy(typeFilter = type)
    }

    fun toggleExpanded(accountId: String) {
        val expanded = _state.value.expanded
        _state.value =
            _state.value.copy(
                expanded = if (accountId in expanded) expanded - accountId else expanded + accountId,
            )
    }

    fun openCreate(parentAccountId: String = "") {
        _state.value = _state.value.copy(editor = AccountEditorState(parentAccountId = parentAccountId, visible = true))
    }

    fun openEdit(account: Account) {
        _state.value =
            _state.value.copy(
                editor =
                    AccountEditorState(
                        id = account.id,
                        accountCode = account.accountCode,
                        name = account.name,
                        type = account.type,
                        parentAccountId = account.parentAccountId ?: "",
                        description = account.description,
                        openingBalance = account.openingBalance.toDouble().toString(),
                        visible = true,
                    ),
            )
    }

    fun closeEditor() {
        _state.value = _state.value.copy(editor = _state.value.editor.copy(visible = false))
    }

    fun updateEditor(transform: (AccountEditorState) -> AccountEditorState) {
        _state.value = _state.value.copy(editor = transform(_state.value.editor))
    }

    fun clearNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    fun saveAccount() {
        val editor = _state.value.editor
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        if (editor.accountCode.isBlank() || editor.name.isBlank()) {
            _state.value = _state.value.copy(error = "Account code and name are required")
            return
        }
        if (editor.id.isNotBlank() && editor.parentAccountId == editor.id) {
            _state.value = _state.value.copy(error = "An account cannot be its own parent")
            return
        }
        val duplicate =
            _state.value.accounts.any {
                it.accountCode.equals(editor.accountCode.trim(), ignoreCase = true) && it.id != editor.id
            }
        if (duplicate) {
            _state.value = _state.value.copy(error = "Account code ${editor.accountCode} already exists")
            return
        }

        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result =
                if (editor.id.isBlank()) {
                    accountNetwork.createAccountAsync(
                        CreateAccountRequest(
                            businessId = businessId,
                            accountCode = editor.accountCode.trim(),
                            name = editor.name.trim(),
                            type = editor.type,
                            parentAccountId = editor.parentAccountId,
                            description = editor.description.trim(),
                            openingBalance = editor.openingBalance.ifBlank { "0" },
                        ),
                    )
                } else {
                    accountNetwork.updateAccountAsync(
                        UpdateAccountRequest(
                            id = editor.id,
                            businessId = businessId,
                            accountCode = editor.accountCode.trim(),
                            name = editor.name.trim(),
                            type = editor.type,
                            parentAccountId = editor.parentAccountId,
                            description = editor.description.trim(),
                        ),
                    )
                }
            result
                .onSuccess { response ->
                    response.payload?.let { account ->
                        runCatching {
                            if (editor.id.isBlank()) accountRepository.insert(account) else accountRepository.update(account)
                        }
                    }
                    val verb = if (editor.id.isBlank()) "created" else "updated"
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            notice = "Account \"${editor.name.trim()}\" $verb",
                            editor = AccountEditorState(),
                        )
                    refresh()
                }.onFailure {
                    _state.value =
                        _state.value.copy(isLoading = false, error = it.message ?: "Save failed")
                }
        }
    }

    companion object {
        private const val PAGE_SIZE = 500
    }
}
