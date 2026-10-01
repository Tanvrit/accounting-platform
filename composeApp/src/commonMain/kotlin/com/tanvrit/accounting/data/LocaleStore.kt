package com.tanvrit.accounting.data

import com.tanvrit.accounting.screens.i18n.AppLanguage
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.storage.store.UserDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI-language preference (roadmap #15). [AccountingSettingsStore] carries no
 * language field, so the locale lives in its own tiny store — same
 * [UserDefaults] shape (`storeScoped`, appId-namespaced) as the other
 * app-local stores, one [StateFlow] the nav shell and Settings collect.
 */
class LocaleStore {
    private val defaults: UserDefaults = TanvritKoin.get()

    private val _language = MutableStateFlow(load())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        defaults.storeScoped(KEY_LOCALE, language.code)
        _language.value = language
    }

    private fun load(): AppLanguage = AppLanguage.fromCode(defaults.retrieveScoped(KEY_LOCALE))

    private companion object {
        /** Backing UserDefaults key — scoped, so it never bleeds across tenants. */
        const val KEY_LOCALE = "accounting.locale"
    }
}
