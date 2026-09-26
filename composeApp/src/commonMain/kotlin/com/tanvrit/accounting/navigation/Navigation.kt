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
import androidx.compose.material.icons.outlined.MarkEmailUnread
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.tanvrit.accounting.screens.dunning.DunningScreen
import com.tanvrit.accounting.screens.fiscalPeriods.FiscalPeriodsScreen
import com.tanvrit.accounting.screens.gstCenter.GstCenterScreen
import com.tanvrit.accounting.screens.keyboard.KeyboardCheatSheetSheet
import com.tanvrit.accounting.screens.keyboard.ShortcutRegistry
import com.tanvrit.accounting.screens.keyboard.tanvritShortcutLayer
import com.tanvrit.accounting.screens.ledger.AccountLedgerScreen
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
        TopLevelDestination(AppRoute.Dunning, "Dunning", Icons.Outlined.MarkEmailUnread),
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

    // Keyboard-first layer (roadmap #12 — Tally-style speed entry). Global
    // chords only; voucher-entry form chords live on that screen itself.
    val shortcutRegistry =
        remember {
            ShortcutRegistry().apply {
                register("ALT+D", "nav.dashboard", "Dashboard", "Navigate")
                register("ALT+C", "nav.coa", "Chart of Accounts", "Navigate")
                register("ALT+V", "nav.voucher", "New voucher", "Navigate")
                register("ESCAPE", "nav.back", "Back", "Navigate")
                register("SHIFT+/", "ui.cheatSheet", "Shortcut cheat sheet", "Help")
                register("CTRL+K", "ui.cheatSheet", "Shortcut cheat sheet", "Help")
                // Listed for the cheat sheet; consumed by VoucherEntryScreen's own
                // layer — the nav handler returns false for these so the inner
                // layer handles them.
                register("CTRL+S", "voucher.saveDraft", "Voucher: save draft", "Voucher entry")
                register("CTRL+ENTER", "voucher.post", "Voucher: save + post", "Voucher entry")
                register("ALT+ENTER", "voucher.addLeg", "Voucher: add leg", "Voucher entry")
            }
        }
    var showCheatSheet by remember { mutableStateOf(false) }

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
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .tanvritShortcutLayer { chord ->
                        when (shortcutRegistry.forChord(chord)?.actionId) {
                            "nav.dashboard" -> {
                                navController.navigate(AppRoute.Dashboard) { launchSingleTop = true }
                                true
                            }
                            "nav.coa" -> {
                                navController.navigate(AppRoute.ChartOfAccounts) { launchSingleTop = true }
                                true
                            }
                            "nav.voucher" -> {
                                navController.navigate(AppRoute.VoucherEntry()) { launchSingleTop = true }
                                true
                            }
                            // false at the root lets Esc bubble to the platform.
                            "nav.back" -> navController.popBackStack()
                            "ui.cheatSheet" -> {
                                showCheatSheet = true
                                true
                            }
                            else -> false
                        }
                    },
        ) {
            tanvritComposable<AppRoute.Dashboard> { DashboardScreen() }
            tanvritComposable<AppRoute.ChartOfAccounts> {
                ChartOfAccountsScreen(onViewLedger = { accountId ->
                    navController.navigate(AppRoute.AccountLedger(accountId))
                })
            }
            tanvritComposable<AppRoute.VoucherEntry> { entry ->
                VoucherEntryScreen(initialVoucherType = entry.toRoute<AppRoute.VoucherEntry>().voucherType)
            }
            tanvritComposable<AppRoute.VoucherList> { VoucherEntryScreen() }
            tanvritComposable<AppRoute.GstCenter> { GstCenterScreen() }
            tanvritComposable<AppRoute.TdsCenter> { TdsCenterScreen() }
            tanvritComposable<AppRoute.Reports> {
                ReportsScreen(onAccountClick = { accountId ->
                    navController.navigate(AppRoute.AccountLedger(accountId))
                })
            }
            tanvritComposable<AppRoute.AccountLedger> { entry ->
                AccountLedgerScreen(
                    accountId = entry.toRoute<AppRoute.AccountLedger>().accountId,
                    onBack = { navController.popBackStack() },
                )
            }
            tanvritComposable<AppRoute.FiscalPeriods> { FiscalPeriodsScreen() }
            tanvritComposable<AppRoute.Budget> { BudgetScreen() }
            tanvritComposable<AppRoute.Dunning> { DunningScreen() }
            tanvritComposable<AppRoute.Reconciliation> { ReconciliationScreen() }
            tanvritComposable<AppRoute.AuditTrail> { AuditTrailScreen() }
            tanvritComposable<AppRoute.Settings> { SettingsScreen() }
        }

        if (showCheatSheet) {
            KeyboardCheatSheetSheet(
                registry = shortcutRegistry,
                onDismiss = { showCheatSheet = false },
            )
        }
    }
}
