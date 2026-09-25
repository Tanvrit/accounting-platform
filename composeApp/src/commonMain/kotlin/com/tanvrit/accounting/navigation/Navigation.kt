package com.tanvrit.accounting.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tanvrit.accounting.screens.DashboardScreen
import com.tanvrit.accounting.screens.VoucherEntryScreen
import com.tanvrit.accounting.screens.ChartOfAccountsScreen
import com.tanvrit.accounting.screens.GstCenterScreen
import com.tanvrit.accounting.screens.TdsCenterScreen
import com.tanvrit.accounting.screens.ReportsScreen
import com.tanvrit.accounting.screens.FiscalPeriodsScreen
import com.tanvrit.accounting.screens.BudgetScreen
import com.tanvrit.accounting.screens.AuditTrailScreen

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object VoucherEntry : Screen("voucher/entry")
    object ChartOfAccounts : Screen("coa")
    object GstCenter : Screen("gst")
    object TdsCenter : Screen("tds")
    object Reports : Screen("reports")
    object FiscalPeriods : Screen("fiscal-periods")
    object Budget : Screen("budget")
    object AuditTrail : Screen("audit-trail")
}

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
    ) {
        composable(Screen.Dashboard.route) { DashboardScreen(navController) }
        composable(Screen.VoucherEntry.route) { VoucherEntryScreen(navController) }
        composable(Screen.ChartOfAccounts.route) { ChartOfAccountsScreen(navController) }
        composable(Screen.GstCenter.route) { GstCenterScreen(navController) }
        composable(Screen.TdsCenter.route) { TdsCenterScreen(navController) }
        composable(Screen.Reports.route) { ReportsScreen(navController) }
        composable(Screen.FiscalPeriods.route) { FiscalPeriodsScreen(navController) }
        composable(Screen.Budget.route) { BudgetScreen(navController) }
        composable(Screen.AuditTrail.route) { AuditTrailScreen(navController) }
    }
}