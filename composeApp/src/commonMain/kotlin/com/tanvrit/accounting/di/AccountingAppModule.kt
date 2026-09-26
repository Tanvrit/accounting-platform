package com.tanvrit.accounting.di

import com.tanvrit.accounting.data.AccountingSettingsStore
import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.data.NumberingSeriesStore
import com.tanvrit.accounting.data.VoucherTemplateStore
import com.tanvrit.accounting.data.VoucherWorkflowStore
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * App-level Koin registrations for the accounting platform app. Screen
 * ViewModels are NOT registered here — they are constructed per screen via
 * `rememberViewModel { ... }` (SDK convention), and resolve their networks /
 * repositories through the SDK Koin graph (`coreModule`, `storageModule`,
 * `authModule`, `businessModule`, `uiModule`, `AccountingModule`), loaded by
 * `initTanvritAccounting` before this module.
 *
 * The `*Store` singles are app-local state (UserDefaults-backed, per-business)
 * for roadmap #10 (templates + numbering series) and #11 (approval overlay).
 */
val accountingAppModule: Module =
    module {
        single { AccountingWorkspace() }
        single { AccountingSettingsStore() }
        single { NumberingSeriesStore() }
        single { VoucherTemplateStore() }
        single { VoucherWorkflowStore() }
    }
