package com.tanvrit.accounting.screens.dunning

import com.tanvrit.accounting.data.AccountingSettingsStore
import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.repository.AccountRepository
import com.tanvrit.accounting.repository.VoucherRepository
import com.tanvrit.accounting.screens.common.parseMoneyInput
import com.tanvrit.business.feature.business.repository.BusinessRepository
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.Voucher
import com.tanvrit.core.feature.money.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class DunningUiState(
    val businessId: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** One-shot notice surfaced under the header (e.g. "Letter copied"); cleared by the user. */
    val notice: String? = null,
    val businessName: String = "",
    /** GSTIN typed in Settings → registration (preferred letterhead value). */
    val settingsGstin: String = "",
    /** GSTIN on the Business record — fallback when Settings is blank. */
    val businessRecordGstin: String = "",
    /** As-of date for the aging run, ISO yyyy-mm-dd; defaults to today, user-overridable. */
    val referenceDate: String = "",
    val parties: List<PartyAging> = emptyList(),
    val searchQuery: String = "",
    val minAmountText: String = "",
    val bucketFilter: AgingBucket? = null,
    /** Party whose reminder sheet is open; null = sheet closed. */
    val reminderPartyId: String? = null,
    val reminderTone: ReminderTone = ReminderTone.REMINDER,
) {
    val reminderParty: PartyAging? get() = parties.firstOrNull { it.partyAccountId == reminderPartyId }
    val minAmount: Money get() = parseMoneyInput(minAmountText)
    val visibleParties: List<PartyAging> get() = AgingMath.applyFilters(parties, minAmount, bucketFilter, searchQuery)
    val isEmpty: Boolean get() = !isLoading && error == null && parties.isEmpty()

    /** Letterhead GSTIN: Settings registration wins, the business record is the fallback. */
    val letterheadGstin: String get() = settingsGstin.ifBlank { businessRecordGstin }
}

/**
 * Receivables aging + payment reminders (roadmap feature #9) — fully
 * client-side.
 *
 * Reads are offline-first, mirroring the account-ledger screen: vouchers and
 * accounts come from the SDK repositories' local caches
 * ([VoucherRepository.findByBusinessId] / [AccountRepository.findByBusinessId]),
 * then [AgingMath.agingOf] classifies POSTED SALE vouchers into buckets per
 * party. There is deliberately no server report here: allocated payments are
 * not available client-side, so every open invoice shows its FULL amount and
 * the screen says so ("payments not netted, sync pending") instead of faking
 * netted balances.
 *
 * Reminder letters are generated locally by [ReminderLetter]; send affordances
 * are copy-to-clipboard plus a `mailto:` intent (WhatsApp send is roadmap #18,
 * server/provider-gated — no fake calls).
 */
class DunningViewModel : AppViewModel() {
    private val workspace: AccountingWorkspace = TanvritKoin.get()
    private val accountRepository: AccountRepository = TanvritKoin.get()
    private val voucherRepository: VoucherRepository = TanvritKoin.get()
    private val settingsStore: AccountingSettingsStore = TanvritKoin.get()
    private val businessRepository: BusinessRepository = TanvritKoin.get()

    private val accountsById = mutableMapOf<String, Account>()

    /** Most recent cached voucher read — re-aged on a reference-date change without a repo round trip. */
    private var vouchersCache: List<Voucher> = emptyList()

    private val _state = MutableStateFlow(DunningUiState(businessId = workspace.businessId.value))
    val state = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(referenceDate = todayIso())
        scope.launch {
            workspace.businessId.collect { businessId ->
                _state.value = _state.value.copy(businessId = businessId)
                if (businessId.isNotBlank()) {
                    loadLetterhead()
                    refresh()
                }
            }
        }
        scope.launch {
            settingsStore.settings.collect { settings ->
                _state.value = _state.value.copy(settingsGstin = settings.gstin)
            }
        }
        scope.launch {
            businessRepository.myBusiness.collect { business ->
                _state.value =
                    _state.value.copy(
                        businessName = business.name,
                        businessRecordGstin = business.gstNumber,
                    )
            }
        }
    }

    fun refresh() {
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val accounts =
                runCatching { accountRepository.findByBusinessId(businessId, null, null, 0, PAGE_SIZE) }
            val vouchers =
                runCatching { voucherRepository.findByBusinessId(businessId, null, null, null, 0, PAGE_SIZE) }
            val cachedAccounts =
                accounts.getOrElse {
                    _state.value =
                        _state.value.copy(isLoading = false, error = it.message ?: "Failed to load accounts")
                    return@launch
                }
            val cachedVouchers =
                vouchers.getOrElse {
                    _state.value =
                        _state.value.copy(isLoading = false, error = it.message ?: "Failed to load vouchers")
                    return@launch
                }
            accountsById.clear()
            accountsById.putAll(cachedAccounts.associateBy { it.id })
            vouchersCache = cachedVouchers
            rebuild()
        }
    }

    /** Re-runs the pure aging math over the currently cached reads. */
    private fun rebuild() {
        _state.value =
            _state.value.copy(
                isLoading = false,
                error = null,
                parties = AgingMath.agingOf(vouchersCache, accountsById.toMap(), _state.value.referenceDate),
            )
    }

    fun setReferenceDate(value: String) {
        _state.value = _state.value.copy(referenceDate = value)
        rebuild()
    }

    fun setSearchQuery(value: String) {
        _state.value = _state.value.copy(searchQuery = value)
    }

    fun setMinAmount(value: String) {
        _state.value = _state.value.copy(minAmountText = value)
    }

    fun setBucketFilter(bucket: AgingBucket?) {
        _state.value = _state.value.copy(bucketFilter = if (_state.value.bucketFilter == bucket) null else bucket)
    }

    fun setNotice(value: String?) {
        _state.value = _state.value.copy(notice = value)
    }

    fun openReminder(partyAccountId: String) {
        val party = _state.value.parties.firstOrNull { it.partyAccountId == partyAccountId } ?: return
        _state.value =
            _state.value.copy(
                reminderPartyId = partyAccountId,
                reminderTone = suggestedTone(party),
            )
    }

    fun closeReminder() {
        _state.value = _state.value.copy(reminderPartyId = null)
    }

    fun selectTone(tone: ReminderTone) {
        _state.value = _state.value.copy(reminderTone = tone)
    }

    /** Escalation step suggested by the party's oldest overdue invoice. */
    private fun suggestedTone(party: PartyAging): ReminderTone =
        when {
            party.maxDaysOverdue > 90 -> ReminderTone.FINAL
            party.maxDaysOverdue > 30 -> ReminderTone.REMINDER
            else -> ReminderTone.CONSERVATIVE
        }

    /** Letterhead identity — offline-first from the business repository cache. */
    private fun loadLetterhead() {
        val business = runCatching { businessRepository.myBusiness.value }.getOrNull() ?: return
        _state.value =
            _state.value.copy(
                businessName = business.name,
                businessRecordGstin = business.gstNumber,
            )
    }

    private fun todayIso(): String =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()

    companion object {
        /** Single-shot window read, matching the page-size convention of the other screens. */
        private const val PAGE_SIZE = 500
    }
}
