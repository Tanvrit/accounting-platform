package com.tanvrit.accounting.data

import com.tanvrit.auth.core.handler.AuthHandler
import com.tanvrit.business.feature.business.repository.BusinessRepository
import com.tanvrit.core.di.TanvritKoin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The active workspace for the accounting app — which business' books are on
 * screen, plus the signed-in user id stamped on voucher posts / period locks.
 *
 * Backed by the SDK `businessModule`'s [BusinessRepository] (tenant-isolated,
 * cleared on business switch via `clearOnTenantSwitch`) and `authModule`'s
 * [AuthHandler]. Single Koin registration in `accountingAppModule`.
 */
class AccountingWorkspace {
    private val repository: BusinessRepository = TanvritKoin.get()

    private val flowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Active business id — blank until a business is selected/loaded. */
    val businessId: StateFlow<String> =
        repository.myBusiness
            .map { it.id }
            .stateIn(flowScope, SharingStarted.Eagerly, repository.myBusiness.value.id)

    /** True once the workspace points at a real business. */
    val hasActiveBusiness: Boolean get() = businessId.value.isNotBlank()

    /** Current user id, or "" when signed out / guest (never throws). */
    fun currentUserId(): String = runCatching { AuthHandler.shared().userId }.getOrDefault("")
}
