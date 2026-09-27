@file:Suppress("ktlint:standard:filename") // Project layout keeps routes in Routes.kt per the platform plan.

package com.tanvrit.accounting.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for the accounting app. Destinations are
 * declared with `tanvritComposable<Route>` (see Navigation.kt) so every one
 * inherits the Tanvrit motion vocabulary.
 */
@Serializable
sealed interface AppRoute {
    @Serializable
    data object Dashboard : AppRoute

    @Serializable
    data object ChartOfAccounts : AppRoute

    /** [voucherType] is a `VoucherType.code` ("SALE", "PURCHASE", …). */
    @Serializable
    data class VoucherEntry(
        val voucherType: String = "SALE",
    ) : AppRoute

    @Serializable
    data object VoucherList : AppRoute

    @Serializable
    data object GstCenter : AppRoute

    @Serializable
    data object TdsCenter : AppRoute

    @Serializable
    data object Reports : AppRoute

    /** [accountId] is the chart-of-accounts account to drill into (TB row / CoA row → ledger). */
    @Serializable
    data class AccountLedger(
        val accountId: String,
    ) : AppRoute

    @Serializable
    data object FiscalPeriods : AppRoute

    @Serializable
    data object Budget : AppRoute

    @Serializable
    data object Dunning : AppRoute

    @Serializable
    data object Recurring : AppRoute

    @Serializable
    data object MultiCurrency : AppRoute

    @Serializable
    data object ImportAccounts : AppRoute

    @Serializable
    data object FixedAssets : AppRoute

    /** Reached from the GST Center "ITC (2B)" action (kept off the bottom bar — it's a task screen). */
    @Serializable
    data object ItcWorkspace : AppRoute

    @Serializable
    data object Consolidation : AppRoute

    @Serializable
    data object Reconciliation : AppRoute

    @Serializable
    data object AuditTrail : AppRoute

    @Serializable
    data object Settings : AppRoute
}
