package com.tanvrit.accounting.screens.settings

import com.tanvrit.accounting.data.AccountingSettings
import com.tanvrit.accounting.data.AccountingSettingsStore
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.ui.theme.TanvritTheme
import com.tanvrit.ui.theme.TanvritThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AccountingSettings = AccountingSettings(),
    val themeMode: TanvritThemeMode = TanvritThemeMode.SYSTEM,
    val saved: Boolean = false,
)

/**
 * System settings — numbering series, GST/TDS registrations, defaults and the
 * dark-mode preference (persisted through the SDK `TanvritTheme` mode so every
 * screen picks it up on the next recomposition).
 */
class SettingsViewModel : AppViewModel() {
    private val store: AccountingSettingsStore = TanvritKoin.get()

    private val _state = MutableStateFlow(SettingsUiState(settings = store.settings.value))
    val state = _state.asStateFlow()

    init {
        scope.launch {
            store.settings.collect { settings ->
                _state.value = _state.value.copy(settings = settings)
            }
        }
        _state.value = _state.value.copy(themeMode = TanvritTheme.mode.value)
    }

    fun update(transform: (AccountingSettings) -> AccountingSettings) {
        _state.value = _state.value.copy(saved = false)
        store.update(transform)
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
