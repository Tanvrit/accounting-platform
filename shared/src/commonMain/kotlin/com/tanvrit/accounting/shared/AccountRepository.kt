package com.tanvrit.accounting.shared

import com.tanvrit.accounting.model.Account
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    suspend fun findAll(businessId: String): List<Account>
    suspend fun findById(id: String): Account?
    suspend fun save(account: Account)
    suspend fun update(account: Account)
    suspend fun delete(id: String)
    fun observeByAccountCode(businessId: String, accountCode: String): Flow<Account?>
}