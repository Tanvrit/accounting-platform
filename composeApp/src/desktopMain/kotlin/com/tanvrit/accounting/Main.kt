package com.tanvrit.accounting

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.tanvrit.accounting.app.AccountingIdentity
import com.tanvrit.accounting.app.initTanvritAccounting
import com.tanvrit.core.app.AppStartupConfig
import com.tanvrit.core.app.PlatformType
import com.tanvrit.core.constant.Environment
import com.tanvrit.core.storage.DatabaseConfig

fun main() {
    val config =
        AppStartupConfig(
            appName = "Tanvrit Accounting",
            tagline = "Accounting that keeps up",
            platformType = PlatformType.Desktop,
            environment = Environment.prod,
            databaseConfig = DatabaseConfig(name = "accounting.db", version = 1),
            serverUrl = AccountingIdentity.DEFAULT_SERVER,
        )
    initTanvritAccounting(config)
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Tanvrit Accounting",
        ) {
            App()
        }
    }
}
