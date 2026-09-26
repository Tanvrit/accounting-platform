package com.tanvrit.accounting.di

import com.tanvrit.accounting.data.AccountingSettingsStore
import com.tanvrit.accounting.data.AccountingWorkspace
import org.koin.core.annotation.KoinInternalApi
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Static assertion that the app-level Koin module keeps its two registrations.
 * Factories are never invoked — no Koin context is started, so nothing
 * resolves network, storage, or `UserDefaults` — this only proves the
 * declarations exist and carry the expected types.
 *
 * Deliberately NOT attempted in commonTest (per the audit rule against fake
 * misuse): booting the module to construct `AccountingSettingsStore` /
 * `AccountingWorkspace` for real. Both pull the SDK graph via TanvritKoin, and
 * the only clean persistence seam — `UserDefaults.setSettingsForTesting` + an
 * in-memory `Settings` — lives in `com.russhwolf:multiplatform-settings`,
 * which `com.tanvrit:storage` consumes as an internal (`implementation`)
 * dependency, so the type is not on this app's test compile classpath. A
 * no-arg `UserDefaults()` would instead initialize `java.util.prefs` — real
 * writes outside the workspace. If a live-boot test is wanted later, declare
 * `com.tanvrit:test-support` as a commonTest dependency and use its harness.
 */
class AccountingAppModuleTest {
    // Factory tables are Koin-internal surface (sufficiently stable across
    // Koin 3.x→4.x, and this app pins Koin in the catalog); used read-only.
    @OptIn(KoinInternalApi::class)
    @Test
    fun accountingAppModuleRegistersWorkspaceAndSettingsStore() {
        val primaryTypes = accountingAppModule.mappings.values.map { it.beanDefinition.primaryType }
        assertTrue(AccountingWorkspace::class in primaryTypes, "AccountingWorkspace not registered")
        assertTrue(AccountingSettingsStore::class in primaryTypes, "AccountingSettingsStore not registered")
    }
}
