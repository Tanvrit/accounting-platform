package com.tanvrit.accounting.screens.fixedAssets

import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.data.FixedAsset
import com.tanvrit.accounting.data.FixedAssetStore
import com.tanvrit.accounting.repository.AccountRepository
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.AccountType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Create/edit sheet form — amounts stay strings as typed. */
data class FixedAssetForm(
    val id: String = "",
    val name: String = "",
    val assetAccountId: String = "",
    val depExpenseAccountId: String = "",
    val accumDepAccountId: String = "",
    val cost: String = "",
    val salvage: String = "",
    val method: DepreciationMethod = DepreciationMethod.WDV,
    val ratePercent: String = "",
    val startDate: String = "",
)

data class FixedAssetsUiState(
    val businessId: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val assets: List<FixedAsset> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val nbvById: Map<String, Long> = emptyMap(),
    val showEditor: Boolean = false,
    val form: FixedAssetForm = FixedAssetForm(),
    val pendingDelete: FixedAsset? = null,
    val pendingCharge: FixedAsset? = null,
    val lastOutcome: Map<String, String> = emptyMap(),
)

/** Fixed-assets register (roadmap #6) — local register, ledger-real charges. */
class FixedAssetsViewModel : AppViewModel() {
    private val workspace: AccountingWorkspace = TanvritKoin.get()
    private val store: FixedAssetStore = TanvritKoin.get()
    private val engine: FixedAssetEngine = TanvritKoin.get()
    private val accountRepository: AccountRepository = TanvritKoin.get()

    private val _state = MutableStateFlow(FixedAssetsUiState(businessId = workspace.businessId.value))
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
            store.assets.collect { assets ->
                _state.value =
                    _state.value.copy(
                        assets = assets,
                        nbvById = assets.associate { it.id to engine.netBookValueMinorUnits(it) },
                    )
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

    fun openCreate() {
        _state.value =
            _state.value.copy(
                showEditor = true,
                form = FixedAssetForm(startDate = todayIso()),
            )
    }

    fun openEdit(asset: FixedAsset) {
        _state.value =
            _state.value.copy(
                showEditor = true,
                form =
                    FixedAssetForm(
                        id = asset.id,
                        name = asset.name,
                        assetAccountId = asset.assetAccountId,
                        depExpenseAccountId = asset.depExpenseAccountId,
                        accumDepAccountId = asset.accumDepAccountId,
                        cost = asset.cost,
                        salvage = asset.salvage,
                        method = DepreciationMethod.fromCodeOrDefault(asset.method),
                        ratePercent = asset.ratePercent,
                        startDate = asset.startDate,
                    ),
            )
    }

    fun closeEditor() {
        _state.value = _state.value.copy(showEditor = false, form = FixedAssetForm())
    }

    fun updateForm(transform: (FixedAssetForm) -> FixedAssetForm) {
        _state.value = _state.value.copy(form = transform(_state.value.form))
    }

    fun saveForm() {
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        val form = _state.value.form

        fun fail(msg: String) {
            _state.value = _state.value.copy(error = msg)
        }

        if (form.name.isBlank()) return fail("Name is required")
        if (form.assetAccountId.isBlank()) return fail("Pick the asset account")
        if (form.depExpenseAccountId.isBlank()) return fail("Pick the depreciation expense account")
        if (form.accumDepAccountId.isBlank()) return fail("Pick the accumulated depreciation account")
        if ((form.cost.toDoubleOrNull() ?: 0.0) <= 0.0) return fail("Cost must be positive")
        if ((form.ratePercent.toDoubleOrNull() ?: 0.0) <= 0.0) return fail("Rate % must be positive")

        val accountsById = _state.value.accounts.associateBy { it.id }
        val saved =
            store.save(
                FixedAsset(
                    id = form.id,
                    businessId = businessId,
                    name = form.name.trim(),
                    assetAccountId = form.assetAccountId,
                    assetAccountCode = accountsById[form.assetAccountId]?.accountCode.orEmpty(),
                    assetAccountName = accountsById[form.assetAccountId]?.name.orEmpty(),
                    depExpenseAccountId = form.depExpenseAccountId,
                    depExpenseAccountCode = accountsById[form.depExpenseAccountId]?.accountCode.orEmpty(),
                    depExpenseAccountName = accountsById[form.depExpenseAccountId]?.name.orEmpty(),
                    accumDepAccountId = form.accumDepAccountId,
                    accumDepAccountCode = accountsById[form.accumDepAccountId]?.accountCode.orEmpty(),
                    accumDepAccountName = accountsById[form.accumDepAccountId]?.name.orEmpty(),
                    cost = form.cost.trim(),
                    salvage = form.salvage.trim(),
                    method = form.method.code,
                    ratePercent = form.ratePercent.trim(),
                    startDate = form.startDate.trim(),
                ),
            )
        _state.value = _state.value.copy(showEditor = false, form = FixedAssetForm(), notice = "Saved “${saved.name}”")
    }

    fun askDelete(asset: FixedAsset) {
        _state.value = _state.value.copy(pendingDelete = asset)
    }

    fun dismissDelete() {
        _state.value = _state.value.copy(pendingDelete = null)
    }

    fun confirmDelete() {
        val target = _state.value.pendingDelete ?: return
        store.delete(target.id)
        _state.value = _state.value.copy(pendingDelete = null, notice = "Deleted “${target.name}”")
    }

    fun askCharge(asset: FixedAsset) {
        _state.value = _state.value.copy(pendingCharge = asset)
    }

    fun dismissCharge() {
        _state.value = _state.value.copy(pendingCharge = null)
    }

    fun confirmCharge() {
        val asset = _state.value.pendingCharge ?: return
        val businessId = _state.value.businessId
        _state.value = _state.value.copy(pendingCharge = null)
        scope.launch {
            val outcome = engine.chargeNextPeriod(businessId, asset.id)
            _state.value =
                _state.value.copy(
                    notice = outcome.error,
                    lastOutcome =
                        _state.value.lastOutcome + (asset.id to (outcome.error ?: "Charged period ${(outcome.chargedRowIndex ?: 0) + 1}")),
                )
        }
    }

    fun clearNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun assetTypes(): List<AccountType> = AccountType.entries

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
