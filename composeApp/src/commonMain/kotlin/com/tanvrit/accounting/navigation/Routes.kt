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

    @Serializable
    data object FiscalPeriods : AppRoute

    @Serializable
    data object Budget : AppRoute

    @Serializable
    data object Reconciliation : AppRoute

    @Serializable
    data object AuditTrail : AppRoute

    @Serializable
    data object Settings : AppRoute
}
