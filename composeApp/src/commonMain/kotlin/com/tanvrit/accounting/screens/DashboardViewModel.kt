package com.tanvrit.accounting.screens

import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.Voucher
import com.tanvrit.core.feature.money.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DashboardViewModel(
    private val accountRepository: AccountRepository,
) {
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadDashboard(businessId: String, fiscalPeriodId: String?) {
        _isLoading.value = true
        _error.value = null
        // Network call would go here
        _isLoading.value = false
    }
}