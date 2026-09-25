package com.tanvrit.accounting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tanvrit.accounting.app.AccountingIdentity
import com.tanvrit.accounting.app.initTanvritAccounting
import com.tanvrit.core.app.AppStartupConfig
import com.tanvrit.core.app.PlatformType
import com.tanvrit.core.constant.Environment
import com.tanvrit.core.storage.DatabaseConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initTanvritAccounting(
            AppStartupConfig(
                appName = "Tanvrit Accounting",
                tagline = "Accounting that keeps up",
                platformType = PlatformType.Android,
                environment = Environment.prod,
                // Android's DatabaseConfig takes the application context.
                databaseConfig = DatabaseConfig(applicationContext, "accounting.db", 1),
                serverUrl = AccountingIdentity.DEFAULT_SERVER,
            ),
        )
        setContent { App() }
    }
}
