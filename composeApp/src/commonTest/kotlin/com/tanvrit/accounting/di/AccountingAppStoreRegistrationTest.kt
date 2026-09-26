package com.tanvrit.accounting.di

import com.tanvrit.accounting.data.NumberingSeriesStore
import com.tanvrit.accounting.data.VoucherTemplateStore
import com.tanvrit.accounting.data.VoucherWorkflowStore
import org.koin.core.annotation.KoinInternalApi
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Static assertion that the app-level Koin module registers the three stores
 * added for roadmap #10/#11 — every `TanvritKoin.get<…>()` in the ViewModels
 * must have a matching `single<>`. Factories are never invoked (no Koin
 * context is started), mirroring [AccountingAppModuleTest].
 */
class AccountingAppStoreRegistrationTest {
    @OptIn(KoinInternalApi::class)
    @Test
    fun accountingAppModuleRegistersTheFeatureTenAndElevenStores() {
        val primaryTypes = accountingAppModule.mappings.values.map { it.beanDefinition.primaryType }
        assertTrue(NumberingSeriesStore::class in primaryTypes, "NumberingSeriesStore not registered")
        assertTrue(VoucherTemplateStore::class in primaryTypes, "VoucherTemplateStore not registered")
        assertTrue(VoucherWorkflowStore::class in primaryTypes, "VoucherWorkflowStore not registered")
    }
}
