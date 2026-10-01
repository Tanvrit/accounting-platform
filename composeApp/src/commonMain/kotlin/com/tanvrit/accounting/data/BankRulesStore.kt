package com.tanvrit.accounting.data

import com.tanvrit.accounting.screens.reconciliation.BankRule
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.extension.uniqueId
import com.tanvrit.core.network.AppJson
import com.tanvrit.storage.store.UserDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-business bank-rule store (roadmap #16 lite). Same persistence shape as
 * [VoucherTemplateStore]: [UserDefaults] JSON under one scoped key per
 * business (`bankRules.<businessId>`), with a [StateFlow] the reconciliation
 * screen collects. Client-local only — rules never go on the wire.
 * [selectBusiness] is called by the consuming ViewModel when the workspace
 * business changes.
 */
class BankRulesStore {
    private val defaults: UserDefaults = TanvritKoin.get()

    private var activeBusinessId: String = ""

    private val _rules = MutableStateFlow<List<BankRule>>(emptyList())
    val rules: StateFlow<List<BankRule>> = _rules.asStateFlow()

    fun selectBusiness(businessId: String) {
        if (businessId == activeBusinessId) return
        activeBusinessId = businessId
        _rules.value = load(businessId)
    }

    /** Saves a new rule (id assigned when blank) and returns it. */
    fun save(rule: BankRule): BankRule {
        val stored = if (rule.id.isBlank()) rule.copy(id = uniqueId()) else rule
        val next = (_rules.value + stored).sortedBy { it.priority }
        persist(next)
        _rules.value = next
        return stored
    }

    fun delete(ruleId: String) {
        val next = _rules.value.filterNot { it.id == ruleId }
        persist(next)
        _rules.value = next
    }

    private fun key() = "$KEY_BASE.$activeBusinessId"

    private fun load(businessId: String): List<BankRule> {
        if (businessId.isBlank()) return emptyList()
        val raw = defaults.retrieveScoped("$KEY_BASE.$businessId")
        if (raw.isBlank()) return emptyList()
        return runCatching {
            AppJson.json.decodeFromString<List<BankRule>>(raw)
        }.getOrDefault(emptyList())
            .sortedBy { it.priority }
    }

    private fun persist(value: List<BankRule>) {
        if (activeBusinessId.isBlank()) return
        defaults.storeScoped(key(), AppJson.json.encodeToString(value))
    }

    private companion object {
        /** Prefix of the namespaced key — `"$KEY_BASE.<businessId>"`. */
        const val KEY_BASE = "bankRules"
    }
}
