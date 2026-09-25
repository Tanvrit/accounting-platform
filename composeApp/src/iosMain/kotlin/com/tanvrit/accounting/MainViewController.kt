package com.tanvrit.accounting

import androidx.compose.ui.window.ComposeUIViewController
import com.tanvrit.accounting.app.AccountingIdentity
import com.tanvrit.accounting.app.initTanvritAccounting
import com.tanvrit.core.app.AppStartupConfig
import com.tanvrit.core.app.PlatformType
import com.tanvrit.core.constant.Environment
import com.tanvrit.core.storage.DatabaseConfig
import platform.UIKit.UIViewController

fun mainViewController(): UIViewController {
    initTanvritAccounting(
        AppStartupConfig(
            appName = "Tanvrit Accounting",
            tagline = "Accounting that keeps up",
            platformType = PlatformType.iOS,
            environment = Environment.prod,
            databaseConfig = DatabaseConfig(name = "accounting.db", version = 1),
            serverUrl = AccountingIdentity.DEFAULT_SERVER,
        ),
    )
    return ComposeUIViewController { App() }
}
