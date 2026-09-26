package com.tanvrit.accounting.data

import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.extension.uniqueId
import com.tanvrit.core.network.AppJson
import com.tanvrit.storage.store.UserDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * One recurring-voucher leg. Amounts are major-unit strings **as typed**
 * ("12500.00"); exactly one of [debit]/[credit] holds a value when the leg is
 * complete. A leg with both sides blank makes the schedule a *skeleton* —
 * skeletons are listed but never drafted (they can't balance).
 */
@Serializable
data class RecurringLeg(
    val accountId: String = "",
    val accountCode: String = "",
    val accountName: String = "",
    val debit: String = "",
    val credit: String = "",
    val narration: String = "",
)

/**
 * A client-local recurring schedule (roadmap #2). Client-local on purpose:
 * the SDK 3.0.7 declares recurring RPC actions but wires no Network methods
 * to them, so schedules live in [UserDefaults] under
 * `recurringSchedules.<businessId>` and move to the server when the SDK does.
 *
 * [startDate]/[nextDue]/[endDate] are ISO date strings ("2026-04-01").
 * [runHistory] appends `"<voucherId>@<yyyy-MM-dd>"` per drafted run.
 */
@Serializable
data class RecurringSchedule(
    val id: String = "",
    val businessId: String = "",
    val name: String = "",
    /** `VoucherType.code` ("SALE", …) — a string so schedules survive enum growth. */
    val voucherType: String = "",
    /** `RecurrenceFrequency.code` ("MONTHLY", …). */
    val frequency: String = "MONTHLY",
    val legs: List<RecurringLeg> = emptyList(),
    val startDate: String = "",
    val endDate: String? = null,
    val nextDue: String = "",
    val enabled: Boolean = true,
    val lastError: String? = null,
    val runHistory: List<String> = emptyList(),
)

/** Per-business recurring schedule store — same persistence shape as [VoucherTemplateStore]. */
class RecurringVoucherStore {
    private val defaults: UserDefaults = TanvritKoin.get()

    private var activeBusinessId: String = ""

    private val _schedules = MutableStateFlow<List<RecurringSchedule>>(emptyList())
    val schedules: StateFlow<List<RecurringSchedule>> = _schedules.asStateFlow()

    fun selectBusiness(businessId: String) {
        if (businessId == activeBusinessId) return
        activeBusinessId = businessId
        _schedules.value = load(businessId)
    }

    fun save(schedule: RecurringSchedule): RecurringSchedule {
        val saved =
            schedule.copy(
                id = schedule.id.ifBlank { uniqueId() },
                businessId = activeBusinessId,
            )
        val next = _schedules.value.filterNot { it.id == saved.id } + saved
        persist(next)
        _schedules.value = next
        return saved
    }

    /** Updates an existing schedule in place (engine advances nextDue / appends runs through this). */
    fun update(schedule: RecurringSchedule) {
        val next = _schedules.value.map { if (it.id == schedule.id) schedule else it }
        persist(next)
        _schedules.value = next
    }

    fun delete(scheduleId: String) {
        val next = _schedules.value.filterNot { it.id == scheduleId }
        persist(next)
        _schedules.value = next
    }

    private fun key() = "$KEY_BASE.$activeBusinessId"

    private fun load(businessId: String): List<RecurringSchedule> {
        if (businessId.isBlank()) return emptyList()
        val raw = defaults.retrieveScoped("$KEY_BASE.$businessId")
        if (raw.isBlank()) return emptyList()
        return runCatching {
            AppJson.json.decodeFromString<List<RecurringSchedule>>(raw)
        }.getOrDefault(emptyList())
            .filter { it.businessId == businessId || it.businessId.isBlank() }
            .sortedBy { it.name.lowercase() }
    }

    private fun persist(value: List<RecurringSchedule>) {
        if (activeBusinessId.isBlank()) return
        defaults.storeScoped(key(), AppJson.json.encodeToString(value))
    }

    private companion object {
        /** Prefix of the namespaced key — `"$KEY_BASE.<businessId>"`. */
        const val KEY_BASE = "recurringSchedules"
    }
}
