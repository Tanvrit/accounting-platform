package com.tanvrit.accounting.screens.settings

import com.tanvrit.accounting.data.AccountingSettings
import com.tanvrit.accounting.data.AccountingSettingsStore
import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.data.NumberingSeries
import com.tanvrit.accounting.data.NumberingSeriesStore
import com.tanvrit.accounting.data.VoucherNumbering
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.VoucherType
import com.tanvrit.ui.theme.TanvritTheme
import com.tanvrit.ui.theme.TanvritThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AccountingSettings = AccountingSettings(),
    val themeMode: TanvritThemeMode = TanvritThemeMode.SYSTEM,
    /** Voucher type code ("SALE", …) → numbering series, for the active business. */
    val numbering: Map<String, NumberingSeries> = emptyMap(),
    val saved: Boolean = false,
) {
    /** The editable series shown for [type] — persisted value or settings-seeded default. */
    fun numberingFor(type: VoucherType): NumberingSeries = numbering[type.code] ?: VoucherNumbering.defaultFor(type, settings)
}

/**
 * System settings — numbering series, GST/TDS registrations, defaults and the
 * dark-mode preference (persisted through the SDK `TanvritTheme` mode so every
 * screen picks it up on the next recomposition). The "Voucher numbering" card
 * edits [NumberingSeriesStore] (per business); the legacy prefix fields remain
 * the defaults a new series is seeded from.
 */
class SettingsViewModel : AppViewModel() {
    private val store: AccountingSettingsStore = TanvritKoin.get()
    private val workspace: AccountingWorkspace = TanvritKoin.get()
    private val numberingStore: NumberingSeriesStore = TanvritKoin.get()

    private val _state = MutableStateFlow(SettingsUiState(settings = store.settings.value))
    val state = _state.asStateFlow()

    init {
        scope.launch {
            store.settings.collect { settings ->
                _state.value = _state.value.copy(settings = settings)
            }
        }
        scope.launch {
            workspace.businessId.collect { businessId ->
                numberingStore.selectBusiness(businessId)
            }
        }
        scope.launch {
            numberingStore.series.collect { series ->
                _state.value = _state.value.copy(numbering = series)
            }
        }
        _state.value = _state.value.copy(themeMode = TanvritTheme.mode.value)
    }

    fun update(transform: (AccountingSettings) -> AccountingSettings) {
        _state.value = _state.value.copy(saved = false)
        store.update(transform)
    }

    fun updateNumbering(
        type: VoucherType,
        transform: (NumberingSeries) -> NumberingSeries,
    ) {
        _state.value = _state.value.copy(saved = false)
        numberingStore.updateSeries(type, transform(_state.value.numberingFor(type)))
    }

    fun setThemeMode(mode: TanvritThemeMode) {
        TanvritTheme.setMode(mode)
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun markSaved() {
        _state.value = _state.value.copy(saved = true)
    }

    fun clearSaved() {
        _state.value = _state.value.copy(saved = false)
    }
}
