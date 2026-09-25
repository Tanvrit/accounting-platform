package com.tanvrit.accounting.screens.budget

import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.network.AccountingBudgetNetwork
import com.tanvrit.accounting.network.ReportNetwork
import com.tanvrit.accounting.repository.AccountRepository
import com.tanvrit.accounting.repository.BudgetRepository
import com.tanvrit.accounting.repository.FiscalPeriodRepository
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.AccountingBudget
import com.tanvrit.core.feature.accounting.model.BudgetStatus
import com.tanvrit.core.feature.accounting.model.FiscalPeriod
import com.tanvrit.core.feature.accounting.network.CreateBudgetRequest
import com.tanvrit.core.feature.accounting.network.TrialBalanceRequest
import com.tanvrit.core.feature.accounting.network.UpdateBudgetRequest
import com.tanvrit.core.feature.money.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BudgetEditorState(
    val id: String = "",
    val accountId: String = "",
    val amountText: String = "",
    val visible: Boolean = false,
)

/** Budget vs actual variance for one account, computed client-side. */
data class BudgetVarianceRow(
    val accountCode: String,
    val accountName: String,
    val budgeted: Money,
    val actual: Money,
) {
    val variance: Money get() = budgeted - actual
    val variancePercent: Double get() = if (budgeted == Money.ZERO) 0.0 else (variance.toDouble() / budgeted.toDouble()) * 100.0
}

data class BudgetUiState(
    val businessId: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val periods: List<FiscalPeriod> = emptyList(),
    val selectedPeriodId: String = "",
    val budgets: List<AccountingBudget> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val varianceRows: List<BudgetVarianceRow> = emptyList(),
    val showVariance: Boolean = true,
    val editor: BudgetEditorState = BudgetEditorState(),
) {
    val totalBudgeted: Money get() = budgets.fold(Money.ZERO) { acc, b -> acc + b.budgetedAmount }
    val totalActual: Money get() = varianceRows.fold(Money.ZERO) { acc, r -> acc + r.actual }
    val totalVariance: Money get() = totalBudgeted - totalActual

    /**
     * Indicative next-period forecast: current actuals per account projected at
     * the same absolute level (naive baseline forecast; driver-based and
     * rolling forecasts run server-side).
     */
    val forecastRows: List<BudgetVarianceRow> get() = varianceRows.map { it.copy(budgeted = it.actual) }
}

/**
 * Budgets — create/edit budget lines for the selected fiscal period, and view
 * budget-vs-actual variance. Actuals come from the server trial balance and
 * are matched to budget lines by account.
 */
class BudgetViewModel : AppViewModel() {
    private val workspace: AccountingWorkspace = TanvritKoin.get()
    private val budgetNetwork = AccountingBudgetNetwork.shared()
    private val budgetRepository: BudgetRepository = TanvritKoin.get()
    private val fiscalPeriodRepository: FiscalPeriodRepository = TanvritKoin.get()
    private val accountRepository: AccountRepository = TanvritKoin.get()
    private val reportNetwork = ReportNetwork.shared()

    private val _state = MutableStateFlow(BudgetUiState(businessId = workspace.businessId.value))
    val state = _state.asStateFlow()

    init {
        scope.launch {
            workspace.businessId.collect { businessId ->
                _state.value = _state.value.copy(businessId = businessId)
                if (businessId.isNotBlank()) {
                    refresh(businessId, _state.value.selectedPeriodId)
                }
            }
        }
    }

    fun refresh() {
        val s = _state.value
        if (s.businessId.isNotBlank()) refresh(s.businessId, s.selectedPeriodId)
    }

    fun selectPeriod(periodId: String) {
        val s = _state.value
        _state.value = s.copy(selectedPeriodId = periodId)
        if (s.businessId.isNotBlank()) refresh(s.businessId, periodId)
    }

    private fun refresh(
        businessId: String,
        periodId: String,
    ) {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            runCatching { fiscalPeriodRepository.findByBusinessId(businessId) }
                .onSuccess { periods ->
                    val sorted = periods.sortedByDescending { it.startDate }
                    _state.value =
                        _state.value.copy(
                            periods = sorted,
                            selectedPeriodId = periodId.ifBlank { sorted.firstOrNull()?.id ?: "" },
                        )
                }

            runCatching { accountRepository.findByBusinessId(businessId, null, true, 0, PAGE_SIZE) }
                .onSuccess { _state.value = _state.value.copy(accounts = it) }

            val currentPeriod = _state.value.selectedPeriodId
            val budgets =
                if (currentPeriod.isBlank()) {
                    runCatching { budgetRepository.findByBusinessId(businessId, null) }.getOrDefault(emptyList())
                } else {
                    runCatching {
                        budgetRepository.findByBusinessIdAndFiscalPeriod(businessId, currentPeriod)
                    }.getOrDefault(emptyList())
                }
            _state.value = _state.value.copy(isLoading = false, budgets = budgets)

            computeVariance(businessId, currentPeriod, budgets)
        }
    }

    /** Actuals per account (cr-normal for expense accounts uses debit side). */
    private suspend fun computeVariance(
        businessId: String,
        periodId: String,
        budgets: List<AccountingBudget>,
    ) {
        val trial =
            reportNetwork
                .trialBalanceAsync(
                    TrialBalanceRequest(businessId = businessId, fiscalPeriodId = periodId),
                ).getOrNull()
                ?.payload ?: return
        val byAccount = trial.rows.associateBy { it.accountId }
        _state.value =
            _state.value.copy(
                varianceRows =
                    budgets
                        .map { budget ->
                            val actual = byAccount[budget.accountId]?.let { it.debit - it.credit } ?: Money.ZERO
                            BudgetVarianceRow(
                                accountCode = budget.accountCode,
                                accountName = budget.accountName,
                                budgeted = budget.budgetedAmount,
                                actual = actual,
                            )
                        }.sortedByDescending { it.budgeted },
            )
    }

    fun toggleVariance(show: Boolean) {
        _state.value = _state.value.copy(showVariance = show)
    }

    fun openEditor(budget: AccountingBudget?) {
        _state.value =
            _state.value.copy(
                editor =
                    BudgetEditorState(
                        id = budget?.id ?: "",
                        accountId = budget?.accountId ?: "",
                        amountText = budget?.budgetedAmount?.toDouble()?.toString() ?: "",
                        visible = true,
                    ),
            )
    }

    fun closeEditor() {
        _state.value = _state.value.copy(editor = BudgetEditorState())
    }

    fun updateEditor(transform: (BudgetEditorState) -> BudgetEditorState) {
        _state.value = _state.value.copy(editor = transform(_state.value.editor))
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, notice = null)
    }

    fun saveBudget() {
        val s = _state.value
        val editor = s.editor
        if (s.businessId.isBlank() || s.selectedPeriodId.isBlank()) return
        val account = s.accounts.firstOrNull { it.id == editor.accountId }
        if (account == null) {
            _state.value = s.copy(error = "Pick an account for this budget line")
            return
        }
        if (editor.amountText.isBlank()) {
            _state.value = s.copy(error = "Budgeted amount is required")
            return
        }
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result =
                if (editor.id.isBlank()) {
                    budgetNetwork.createBudgetAsync(
                        CreateBudgetRequest(
                            businessId = s.businessId,
                            fiscalPeriodId = s.selectedPeriodId,
                            accountId = account.id,
                            accountCode = account.accountCode,
                            accountName = account.name,
                            budgetedAmount = editor.amountText.trim(),
                            status = BudgetStatus.ACTIVE,
                        ),
                    )
                } else {
                    budgetNetwork.updateBudgetAsync(
                        UpdateBudgetRequest(
                            id = editor.id,
                            businessId = s.businessId,
                            budgetedAmount = editor.amountText.trim(),
                        ),
                    )
                }
            result
                .onSuccess { response ->
                    response.payload?.let { runCatching { budgetRepository.insert(it) } }
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            notice = "Budget for ${account.name} saved",
                            editor = BudgetEditorState(),
                        )
                    refresh()
                }.onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = it.message ?: "Save failed")
                }
        }
    }

    private companion object {
        const val PAGE_SIZE = 500
    }
}
