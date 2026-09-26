package com.tanvrit.accounting.data

import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.extension.uniqueId
import com.tanvrit.core.network.AppJson
import com.tanvrit.storage.store.UserDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/**
 * One template leg — the account + optional amounts a saved voucher keeps.
 * Amounts are major-unit strings **as typed** in the editor ("12500.00"); an
 * empty string means "leave that side blank when the template is applied".
 */
@Serializable
data class VoucherTemplateLeg(
    val accountId: String = "",
    val accountCode: String = "",
    val accountName: String = "",
    val debit: String = "",
    val credit: String = "",
    val narration: String = "",
)

/**
 * A saved voucher skeleton (feature #10). Client-local: never goes on the
 * wire, so field names carry no `@SerialName` contract — they are the local
 * persistence format under `voucherTemplates.<businessId>` in [UserDefaults].
 */
@Serializable
data class VoucherTemplate(
    val id: String = "",
    val businessId: String = "",
    val name: String = "",
    /** `VoucherType.code` ("SALE", …) — a string so old templates survive enum growth. */
    val voucherType: String = "",
    val legs: List<VoucherTemplateLeg> = emptyList(),
    val createdAt: String = "",
)

/**
 * Per-business voucher template store. Same persistence shape as
 * [AccountingSettingsStore]: [UserDefaults] JSON under one scoped key per
 * business, with a [StateFlow] the screens collect. The active business is
 * set via [selectBusiness] by the consuming ViewModel.
 */
class VoucherTemplateStore {
    private val defaults: UserDefaults = TanvritKoin.get()

    private var activeBusinessId: String = ""

    private val _templates = MutableStateFlow<List<VoucherTemplate>>(emptyList())
    val templates: StateFlow<List<VoucherTemplate>> = _templates.asStateFlow()

    fun selectBusiness(businessId: String) {
        if (businessId == activeBusinessId) return
        activeBusinessId = businessId
        _templates.value = load(businessId)
    }

    /** Saves a new template and returns it. Caller passes already-filtered legs. */
    fun save(
        name: String,
        voucherTypeCode: String,
        legs: List<VoucherTemplateLeg>,
    ): VoucherTemplate {
        val template =
            VoucherTemplate(
                id = uniqueId(),
                businessId = activeBusinessId,
                name = name.trim(),
                voucherType = voucherTypeCode,
                legs = legs,
                createdAt = todayIso(),
            )
        val next = _templates.value + template
        persist(next)
        _templates.value = next
        return template
    }

    fun delete(templateId: String) {
        val next = _templates.value.filterNot { it.id == templateId }
        persist(next)
        _templates.value = next
    }

    private fun key() = "$KEY_BASE.$activeBusinessId"

    private fun load(businessId: String): List<VoucherTemplate> {
        if (businessId.isBlank()) return emptyList()
        val raw = defaults.retrieveScoped("$KEY_BASE.$businessId")
        if (raw.isBlank()) return emptyList()
        return runCatching {
            AppJson.json.decodeFromString<List<VoucherTemplate>>(raw)
        }.getOrDefault(emptyList())
            .filter { it.businessId == businessId || it.businessId.isBlank() }
            .sortedBy { it.name.lowercase() }
    }

    private fun persist(value: List<VoucherTemplate>) {
        if (activeBusinessId.isBlank()) return
        defaults.storeScoped(key(), AppJson.json.encodeToString(value))
    }

    private fun todayIso(): String =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .toString()

    private companion object {
        /** Prefix of the namespaced key — `"$KEY_BASE.<businessId>"`. */
        const val KEY_BASE = "voucherTemplates"
    }
}
