package com.tanvrit.accounting.screens.multiCurrency

import com.tanvrit.accounting.data.AccountingSettingsStore
import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.network.MultiCurrencyNetwork
import com.tanvrit.accounting.repository.FiscalPeriodRepository
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.FiscalPeriod
import com.tanvrit.core.feature.accounting.model.FiscalPeriodStatus
import com.tanvrit.core.feature.accounting.model.FxRate
import com.tanvrit.core.feature.accounting.model.RevaluationResult
import com.tanvrit.core.feature.accounting.network.GetFxRatesRequest
import com.tanvrit.core.feature.accounting.network.RunRevaluationRequest
import com.tanvrit.core.feature.accounting.network.UpdateFxRateRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class MultiCurrencyUiState(
    val businessId: String = "",
    /** Base currency from Settings ▸ System ([AccountingSettingsStore]). */
    val baseCurrency: String = "INR",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** One-shot notice surfaced under the header; replaced by the next action. */
    val notice: String? = null,
    val rates: List<FxRate> = emptyList(),
    /** Editable rate text per row, keyed by [FxRate.id]. Seeded from the model on every successful load. */
    val rateEdits: Map<String, String> = emptyMap(),
    /** FxRate ids with a save in flight — their row controls stay disabled. */
    val savingRateIds: Set<String> = emptySet(),
    /** Per-row validation/save error, keyed by FxRate.id or [MultiCurrencyViewModel.NEW_ROW_KEY]. */
    val rowErrors: Map<String, String> = emptyMap(),
    val addCurrency: String = "",
    val addRateText: String = "",
    val addSaving: Boolean = false,
    /** OPEN periods eligible for revaluation. */
    val openPeriods: List<FiscalPeriod> = emptyList(),
    val revalPeriodId: String = "",
    val revalConfirmArmed: Boolean = false,
    val revalRunning: Boolean = false,
    /** The last server return, shown verbatim — null until a run succeeds. Lines are never synthesized locally. */
    val revaluation: RevaluationResult? = null,
) {
    val isEmpty: Boolean get() = !isLoading && error == null && rates.isEmpty()
}

/**
 * Multi-currency (roadmap #3): FX rates vs the base currency, and period-end
 * revaluation — both server-backed via the SDK's [MultiCurrencyNetwork].
 *
 * Rates come from [MultiCurrencyNetwork.getFxRatesAsync] — the request carries
 * no businessId (quotes are tenant-wide, RBI-sourced by default), but the
 * screen still follows the workspace pattern: loads on refresh + on
 * [AccountingWorkspace.businessId] change, and again when the base currency
 * changes in Settings (the table is pair-scoped). Saves go through
 * [MultiCurrencyNetwork.updateFxRateAsync] — the only mutation the SDK
 * surface offers; a row with a blank id is the add path (the server upserts
 * on the currency pair).
 *
 * Revaluation runs through [MultiCurrencyNetwork.runRevaluationAsync] against
 * an OPEN fiscal period and is deliberately draft-only (`postAutomatically =
 * false`): the unrealized gain/loss lines come back for review, nothing posts
 * to the ledger from here.
 */
class MultiCurrencyViewModel : AppViewModel() {
    private val workspace: AccountingWorkspace = TanvritKoin.get()
    private val settingsStore: AccountingSettingsStore = TanvritKoin.get()
    private val fiscalPeriodRepository: FiscalPeriodRepository = TanvritKoin.get()
    private val multiCurrencyNetwork = MultiCurrencyNetwork.shared()

    private val _state = MutableStateFlow(MultiCurrencyUiState(businessId = workspace.businessId.value))
    val state = _state.asStateFlow()

    init {
        scope.launch {
            workspace.businessId.collect { businessId ->
                _state.value = _state.value.copy(businessId = businessId)
                if (businessId.isNotBlank()) {
                    refresh()
                    loadOpenPeriods()
                }
            }
        }
        scope.launch {
            settingsStore.settings.collect { settings ->
                val baseChanged = settings.baseCurrency != _state.value.baseCurrency
                _state.value = _state.value.copy(baseCurrency = settings.baseCurrency)
                // FX quotes are pair-scoped: a base change invalidates the table.
                if (baseChanged) refresh()
            }
        }
    }

    /** Reloads rates from the server and re-seeds row text from the server truth. */
    fun refresh() {
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        val baseCurrency = _state.value.baseCurrency
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            multiCurrencyNetwork
                .getFxRatesAsync(GetFxRatesRequest(baseCurrency = baseCurrency))
                .onSuccess { response ->
                    val rates = response.payload.sortedBy { it.counterCurrency }
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            rates = rates,
                            rateEdits = rates.associate { it.id to FxFormat.formatRate(it.rate.toString()) },
                        )
                }.onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = it.message ?: "Failed to load FX rates")
                }
        }
    }

    /** OPEN periods for the revaluation picker — the same repository read the voucher/recurring screens make. */
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
                            revalPeriodId =
                                _state.value.revalPeriodId
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

    // ---- rates ----

    fun onRateTextChange(
        rateId: String,
        value: String,
    ) {
        _state.value =
            _state.value.copy(
                rateEdits = _state.value.rateEdits + (rateId to value),
                rowErrors = _state.value.rowErrors - rateId,
            )
    }

    fun saveRate(rate: FxRate) {
        val text = _state.value.rateEdits[rate.id] ?: FxFormat.formatRate(rate.rate.toString())
        val parsed = FxFormat.parseRate(text)
        if (parsed == null) {
            _state.value =
                _state.value.copy(rowErrors = _state.value.rowErrors + (rate.id to "Enter a positive rate (e.g. 82.35)"))
            return
        }
        scope.launch {
            _state.value =
                _state.value.copy(
                    savingRateIds = _state.value.savingRateIds + rate.id,
                    rowErrors = _state.value.rowErrors - rate.id,
                )
            multiCurrencyNetwork
                .updateFxRateAsync(
                    UpdateFxRateRequest(
                        id = rate.id,
                        baseCurrency = rate.baseCurrency,
                        counterCurrency = rate.counterCurrency,
                        rate = parsed.toDouble(),
                        rateDate = todayIso(),
                        source = "MANUAL",
                        rateType = rate.rateType.ifBlank { "SPOT" },
                    ),
                ).onSuccess { response ->
                    val returned = response.payload
                    val effective = returned ?: rate
                    _state.value =
                        _state.value.copy(
                            savingRateIds = _state.value.savingRateIds - rate.id,
                            notice = "Rate ${rate.counterCurrency} updated",
                            rates =
                                if (returned != null) {
                                    _state.value.rates.map { if (it.id == returned.id) returned else it }
                                } else {
                                    _state.value.rates
                                },
                            rateEdits =
                                _state.value.rateEdits +
                                    (rate.id to FxFormat.formatRate(effective.rate.toString())),
                        )
                }.onFailure {
                    _state.value =
                        _state.value.copy(
                            savingRateIds = _state.value.savingRateIds - rate.id,
                            rowErrors = _state.value.rowErrors + (rate.id to (it.message ?: "Save failed")),
                        )
                }
        }
    }

    fun onAddCurrencyChange(value: String) {
        _state.value = _state.value.copy(addCurrency = value)
    }

    fun onAddRateTextChange(value: String) {
        _state.value =
            _state.value.copy(addRateText = value, rowErrors = _state.value.rowErrors - NEW_ROW_KEY)
    }

    /**
     * The "add" path is the same update endpoint with a blank id — the SDK
     * exposes no create; the server upserts on (baseCurrency, counterCurrency).
     */
    fun addRate() {
        val state = _state.value
        if (state.businessId.isBlank()) return
        if (state.addCurrency.isBlank()) {
            _state.value = state.copy(rowErrors = state.rowErrors + (NEW_ROW_KEY to "Pick a currency"))
            return
        }
        val parsed = FxFormat.parseRate(state.addRateText)
        if (parsed == null) {
            _state.value =
                state.copy(rowErrors = state.rowErrors + (NEW_ROW_KEY to "Enter a positive rate (e.g. 82.35)"))
            return
        }
        scope.launch {
            _state.value = _state.value.copy(addSaving = true, rowErrors = _state.value.rowErrors - NEW_ROW_KEY)
            multiCurrencyNetwork
                .updateFxRateAsync(
                    UpdateFxRateRequest(
                        id = "",
                        baseCurrency = state.baseCurrency,
                        counterCurrency = state.addCurrency,
                        rate = parsed.toDouble(),
                        rateDate = todayIso(),
                        source = "MANUAL",
                    ),
                ).onSuccess {
                    _state.value =
                        _state.value.copy(
                            addSaving = false,
                            addCurrency = "",
                            addRateText = "",
                            notice = "Rate ${state.addCurrency} added",
                        )
                    refresh()
                }.onFailure {
                    _state.value =
                        _state.value.copy(
                            addSaving = false,
                            rowErrors = _state.value.rowErrors + (NEW_ROW_KEY to (it.message ?: "Add failed")),
                        )
                }
        }
    }

    // ---- revaluation ----

    fun onRevalPeriodChange(periodId: String) {
        _state.value = _state.value.copy(revalPeriodId = periodId)
    }

    fun armRevaluation() {
        val state = _state.value
        if (state.businessId.isBlank()) return
        if (state.revalPeriodId.isBlank()) {
            _state.value = state.copy(error = "No OPEN fiscal period — create and open one in Periods first")
            return
        }
        _state.value = state.copy(revalConfirmArmed = true)
    }

    fun disarmRevaluation() {
        _state.value = _state.value.copy(revalConfirmArmed = false)
    }

    fun confirmRevaluation() {
        val state = _state.value
        if (state.businessId.isBlank() || state.revalPeriodId.isBlank()) return
        _state.value = state.copy(revalConfirmArmed = false)
        scope.launch {
            _state.value = _state.value.copy(revalRunning = true, error = null, notice = null)
            multiCurrencyNetwork
                .runRevaluationAsync(
                    RunRevaluationRequest(
                        businessId = state.businessId,
                        fiscalPeriodId = state.revalPeriodId,
                        revaluationDate = todayIso(),
                        baseCurrency = state.baseCurrency,
                        // Draft-only: posting the adjustment voucher stays a deliberate server-side step.
                        postAutomatically = false,
                        postedBy = workspace.currentUserId(),
                    ),
                ).onSuccess { response ->
                    val result = response.payload
                    _state.value =
                        _state.value.copy(
                            revalRunning = false,
                            revaluation = result,
                            notice =
                                result?.let { "Revaluation ${it.status} — ${it.lineItems.size} line(s)" }
                                    ?: response.message.ifBlank { null },
                        )
                }.onFailure {
                    _state.value =
                        _state.value.copy(revalRunning = false, error = it.message ?: "Revaluation failed")
                }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, notice = null)
    }

    private fun todayIso(): String =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()

    companion object {
        /** Row error key for the add-rate editor (no FxRate id yet). */
        const val NEW_ROW_KEY = "__new__"

        /**
         * Curated ISO-4217 counters for the add-rate picker (INR last,
         * deliberately — it is almost always the base here).
         */
        val ADD_CURRENCIES =
            listOf(
                "USD",
                "EUR",
                "GBP",
                "AED",
                "SGD",
                "JPY",
                "AUD",
                "CAD",
                "CHF",
                "SAR",
                "MYR",
                "THB",
                "IDR",
                "HKD",
                "NZD",
                "ZAR",
                "BDT",
                "NPR",
                "LKR",
                "INR",
            )
    }
}
