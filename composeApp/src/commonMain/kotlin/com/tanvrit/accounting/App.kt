package com.tanvrit.accounting

import androidx.compose.runtime.Composable
import com.tanvrit.accounting.navigation.AppNavigation
import com.tanvrit.accounting.theme.AccountingTheme

/**
 * App root. The SDK must already be booted ([com.tanvrit.accounting.app.initTanvritAccounting])
 * by the platform entry point before this is composed.
 */
@Composable
fun App() {
    AccountingTheme {
        AppNavigation()
    }
}
