package com.tanvrit.accounting.app

import com.tanvrit.accounting.di.AccountingModule
import com.tanvrit.accounting.di.accountingAppModule
import com.tanvrit.auth.di.authModule
import com.tanvrit.business.di.businessModule
import com.tanvrit.core.app.AppStartupConfig
import com.tanvrit.core.di.TanvritSDK
import com.tanvrit.core.di.coreModule
import com.tanvrit.core.network.rest.TanvritClientConfig
import com.tanvrit.storage.app.AppInitializer
import com.tanvrit.storage.di.storageModule
import com.tanvrit.ui.di.uiModule
import kotlin.concurrent.Volatile

/**
 * Tanvrit Accounting tenant identity.
 *
 * Must be applied before the first Compose render so every network call
 * carries `X-App-ID` / `X-API-Key` and token storage is namespaced with
 * `com.tanvrit.accounting.*`.
 */
object AccountingIdentity {
    const val APP_ID = "com.tanvrit.accounting"
    const val DEFAULT_SERVER = "https://api.tanvrit.com"

    /** ARGB-packed brand seed (ledger green). */
    const val BRAND_SEED_COLOR: ULong = 0xFF14532DUL
}

@Volatile
private var sdkInitialized = false

/**
 * Boots the Tanvrit SDK module graph for the accounting app.
 *
 * `TanvritSDK.init` is single-call per process by SDK design (it throws to
 * prevent cross-tenant config bleed), so the boot is guarded behind
 * [sdkInitialized]; later calls only re-assert tenant identity.
 *
 * Called from every platform entry point (desktopMain / androidMain /
 * wasmJsMain / iosMain) before [com.tanvrit.accounting.App] is composed.
 */
fun initTanvritAccounting(config: AppStartupConfig) {
    if (sdkInitialized) {
        TanvritClientConfig.appId = AccountingIdentity.APP_ID
        return
    }
    TanvritSDK.init {
        appId = AccountingIdentity.APP_ID
        apiBaseUrl = config.serverUrl ?: AccountingIdentity.DEFAULT_SERVER
        env = config.environment
        brandSeedColor = AccountingIdentity.BRAND_SEED_COLOR
        modules {
            +coreModule
            +storageModule
            +authModule
            +businessModule
            +uiModule
            // SDK :accounting — networks, offline-first repositories, Koin.
            +AccountingModule
            +accountingAppModule
        }
    }
    // Open the local DB so SDK + accounting caches persist across restarts
    // (WasmJS: sql.js → localStorage; degrades logged, never fatal).
    runCatching { AppInitializer.initialize(config) }
    sdkInitialized = true
}
