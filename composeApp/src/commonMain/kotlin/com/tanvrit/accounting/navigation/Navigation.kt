package com.tanvrit.accounting.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.tanvrit.accounting.screens.auditTrail.AuditTrailScreen
import com.tanvrit.accounting.screens.budget.BudgetScreen
import com.tanvrit.accounting.screens.chartOfAccounts.ChartOfAccountsScreen
import com.tanvrit.accounting.screens.dashboard.DashboardScreen
import com.tanvrit.accounting.screens.fiscalPeriods.FiscalPeriodsScreen
import com.tanvrit.accounting.screens.gstCenter.GstCenterScreen
import com.tanvrit.accounting.screens.reconciliation.ReconciliationScreen
import com.tanvrit.accounting.screens.reports.ReportsScreen
import com.tanvrit.accounting.screens.settings.SettingsScreen
import com.tanvrit.accounting.screens.tdsCenter.TdsCenterScreen
import com.tanvrit.accounting.screens.voucherEntry.VoucherEntryScreen
import com.tanvrit.ui.navigation.tanvritComposable

private data class TopLevelDestination(
    val route: AppRoute,
    val label: String,
    val icon: ImageVector,
)

private val topLevelDestinations =
    listOf(
        TopLevelDestination(AppRoute.Dashboard, "Dashboard", Icons.Outlined.Dashboard),
        TopLevelDestination(AppRoute.ChartOfAccounts, "Accounts", Icons.Outlined.AccountTree),
        TopLevelDestination(AppRoute.VoucherEntry(), "Voucher", Icons.Outlined.Receipt),
        TopLevelDestination(AppRoute.GstCenter, "GST", Icons.Outlined.CurrencyRupee),
        TopLevelDestination(AppRoute.TdsCenter, "TDS", Icons.Outlined.Percent),
        TopLevelDestination(AppRoute.Reports, "Reports", Icons.Outlined.Assessment),
        TopLevelDestination(AppRoute.FiscalPeriods, "Periods", Icons.Outlined.DateRange),
        TopLevelDestination(AppRoute.Budget, "Budget", Icons.Outlined.AccountBalance),
        TopLevelDestination(AppRoute.Reconciliation, "Bank Rec", Icons.Outlined.Sync),
        TopLevelDestination(AppRoute.AuditTrail, "Audit", Icons.AutoMirrored.Outlined.FactCheck),
        TopLevelDestination(AppRoute.Settings, "Settings", Icons.Outlined.Settings),
    )

/**
 * App navigation shell: NavigationBar + NavHost. Every destination is declared
 * with `tanvritComposable<Route>` so screen transitions carry the Tanvrit
 * motion vocabulary instead of default transitions.
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                topLevelDestinations.forEach { destination ->
                    val selected =
                        currentDestination?.hierarchy?.any { node ->
                            when (val route = destination.route) {
                                is AppRoute.VoucherEntry ->
                                    node.hasRoute<AppRoute.VoucherEntry>() || node.hasRoute<AppRoute.VoucherList>()
                                else -> node.hasRoute(route::class)
                            }
                        } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo<AppRoute.Dashboard> { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.Dashboard,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            tanvritComposable<AppRoute.Dashboard> { DashboardScreen() }
            tanvritComposable<AppRoute.ChartOfAccounts> { ChartOfAccountsScreen() }
            tanvritComposable<AppRoute.VoucherEntry> { entry ->
                VoucherEntryScreen(initialVoucherType = entry.toRoute<AppRoute.VoucherEntry>().voucherType)
            }
            tanvritComposable<AppRoute.VoucherList> { VoucherEntryScreen() }
            tanvritComposable<AppRoute.GstCenter> { GstCenterScreen() }
            tanvritComposable<AppRoute.TdsCenter> { TdsCenterScreen() }
            tanvritComposable<AppRoute.Reports> { ReportsScreen() }
            tanvritComposable<AppRoute.FiscalPeriods> { FiscalPeriodsScreen() }
            tanvritComposable<AppRoute.Budget> { BudgetScreen() }
            tanvritComposable<AppRoute.Reconciliation> { ReconciliationScreen() }
            tanvritComposable<AppRoute.AuditTrail> { AuditTrailScreen() }
            tanvritComposable<AppRoute.Settings> { SettingsScreen() }
        }
    }
}
