package com.tanvrit.accounting.screens.recurring

import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.data.RecurringLeg
import com.tanvrit.accounting.data.RecurringSchedule
import com.tanvrit.accounting.data.RecurringVoucherStore
import com.tanvrit.accounting.network.VoucherNetwork
import com.tanvrit.accounting.repository.FiscalPeriodRepository
import com.tanvrit.accounting.repository.VoucherRepository
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.FiscalPeriodStatus
import com.tanvrit.core.feature.accounting.model.VoucherLineItem
import com.tanvrit.core.feature.accounting.model.VoucherType
import com.tanvrit.core.feature.accounting.network.CreateVoucherRequest
import com.tanvrit.core.feature.money.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Per-schedule outcome of one engine pass. */
data class RecurringRunOutcome(
    val scheduleId: String,
    val draftedVoucherIds: List<String>,
    val error: String?,
)

/**
 * The recurring-vouchers engine (roadmap #2). Pure client-local honesty rules:
 *
 *  - Creates **DRAFT** vouchers only — a human verifies/posts through the
 *    existing approvals flow. Never auto-posts.
 *  - Skeletons (legs without accounts, or unbalanced amounts) are never drafted;
 *    the schedule records [RecurringSchedule.lastError] and keeps its nextDue
 *    so the run retries next launch.
 *  - An occurrence whose date has no OPEN fiscal period is NOT drafted and the
 *    schedule stops there (books stay closed periods intact).
 *  - Multiple missed periods draft one voucher per occurrence (max
 *    [RecurringScheduleMath.MAX_OCCURRENCES_PER_RUN] per run) so the ledger
 *    reflects what actually happened, not a lumped catch-up entry.
 */
class RecurringVoucherEngine(
    private val store: RecurringVoucherStore = TanvritKoin.get(),
    private val workspace: AccountingWorkspace = TanvritKoin.get(),
    private val fiscalPeriodRepository: FiscalPeriodRepository = TanvritKoin.get(),
    private val voucherRepository: VoucherRepository = TanvritKoin.get(),
) {
    private val voucherNetwork = VoucherNetwork.shared()

    /** Drafts every due occurrence across enabled schedules for [businessId]. */
    suspend fun processDueSchedules(businessId: String): List<RecurringRunOutcome> {
        if (businessId.isBlank()) return emptyList()
        store.selectBusiness(businessId)
        val today =
            Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
        return store.schedules.value.map { processOne(businessId, it, today) }
    }

    /** Drafts a single schedule's due occurrences ("Run now"). */
    suspend fun runScheduleNow(
        businessId: String,
        scheduleId: String,
    ): RecurringRunOutcome {
        if (businessId.isBlank()) return RecurringRunOutcome(scheduleId, emptyList(), "No active business")
        store.selectBusiness(businessId)
        val schedule =
            store.schedules.value.firstOrNull { it.id == scheduleId }
                ?: return RecurringRunOutcome(scheduleId, emptyList(), "Schedule not found")
        val today =
            Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
        return processOne(businessId, schedule, today)
    }

    private suspend fun processOne(
        businessId: String,
        schedule: RecurringSchedule,
        today: LocalDate,
    ): RecurringRunOutcome {
        val nextDue = parseDate(schedule.nextDue.ifBlank { schedule.startDate })
        if (nextDue == null) {
            return fail(schedule, "Invalid next-due date '${schedule.nextDue}'")
        }
        val endDate = schedule.endDate?.takeIf { it.isNotBlank() }?.let { parseDate(it) }
        val frequency = RecurrenceFrequency.fromCodeOrDefault(schedule.frequency)
        val occurrences = RecurringScheduleMath.dueOccurrences(frequency, nextDue, today, endDate, schedule.enabled)
        if (occurrences.isEmpty()) {
            return RecurringRunOutcome(schedule.id, emptyList(), null)
        }

        val voucherType =
            VoucherType.entries.firstOrNull { it.code == schedule.voucherType }
                ?: return fail(schedule, "Unknown voucher type '${schedule.voucherType}'")

        val lineError = legValidationError(schedule)
        if (lineError != null) return fail(schedule, lineError)

        val draftedIds = mutableListOf<String>()
        var cursor = nextDue
        for (occurrence in occurrences) {
            val periodId = resolveOpenPeriodId(businessId, occurrence.toString())
            if (periodId.isBlank()) {
                return fail(
                    schedule.copy(nextDue = occurrence.toString()),
                    "No open fiscal period for $occurrence; open the period, then re-run",
                )
            }
            val request =
                CreateVoucherRequest(
                    businessId = businessId,
                    voucherType = voucherType,
                    date = occurrence.toString(),
                    narration = "Recurring: ${schedule.name}".trim(),
                    referenceId = "recurring:${schedule.id}",
                    lineItems = schedule.legs.toLineItems(),
                    fiscalPeriodId = periodId,
                )
            val result = voucherNetwork.createVoucherAsync(request)
            val created = result.getOrNull()?.payload
            if (created == null) {
                return fail(
                    schedule.copy(nextDue = occurrence.toString()),
                    result.exceptionOrNull()?.message ?: "No voucher returned by server",
                )
            }
            runCatching { voucherRepository.insert(created) }
            draftedIds += created.id
            cursor = RecurringScheduleMath.nextDueFrom(frequency, occurrence)
            val current = store.schedules.value.first { it.id == schedule.id }
            store.update(
                current.copy(
                    nextDue = cursor.toString(),
                    lastError = null,
                    runHistory = current.runHistory + "${created.id}@$occurrence",
                ),
            )
        }
        return RecurringRunOutcome(schedule.id, draftedIds, null)
    }

    private fun List<RecurringLeg>.toLineItems(): List<VoucherLineItem> =
        map { leg ->
            VoucherLineItem(
                accountId = leg.accountId,
                accountCode = leg.accountCode,
                accountName = leg.accountName,
                debit =
                    Money.fromDouble(
                        leg.debit
                            .trim()
                            .replace(",", "")
                            .toDoubleOrNull() ?: 0.0,
                    ),
                credit =
                    Money.fromDouble(
                        leg.credit
                            .trim()
                            .replace(",", "")
                            .toDoubleOrNull() ?: 0.0,
                    ),
                narration = leg.narration.trim(),
            )
        }

    private fun legValidationError(schedule: RecurringSchedule): String? {
        val legs = schedule.legs
        if (legs.size < 2) return "Needs at least two legs to balance"
        legs.forEachIndexed { index, leg ->
            if (leg.accountId.isBlank()) return "Leg ${index + 1}: no account selected"
            val debit =
                leg.debit
                    .trim()
                    .replace(",", "")
                    .toDoubleOrNull() ?: 0.0
            val credit =
                leg.credit
                    .trim()
                    .replace(",", "")
                    .toDoubleOrNull() ?: 0.0
            if (debit > 0.0 && credit > 0.0) return "Leg ${index + 1}: both debit and credit set"
            if (debit == 0.0 && credit == 0.0) return "Leg ${index + 1}: amount missing"
        }
        val totalDebit =
            legs.sumOf {
                it.debit
                    .trim()
                    .replace(",", "")
                    .toDoubleOrNull() ?: 0.0
            }
        val totalCredit =
            legs.sumOf {
                it.credit
                    .trim()
                    .replace(",", "")
                    .toDoubleOrNull() ?: 0.0
            }
        if (totalDebit != totalCredit) return "Unbalanced legs (Dr $totalDebit ≠ Cr $totalCredit)"
        if (totalDebit == 0.0) return "Zero total"
        return null
    }

    private suspend fun resolveOpenPeriodId(
        businessId: String,
        date: String,
    ): String =
        runCatching { fiscalPeriodRepository.findByBusinessIdAndStatus(businessId, FiscalPeriodStatus.OPEN) }
            .getOrDefault(emptyList())
            .firstOrNull { it.startDate <= date && it.endDate >= date }
            ?.id
            ?: ""

    private fun fail(
        schedule: RecurringSchedule,
        message: String,
    ): RecurringRunOutcome {
        store.update(schedule.copy(lastError = message))
        return RecurringRunOutcome(schedule.id, emptyList(), message)
    }

    private fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw.trim()) }.getOrNull()
}
