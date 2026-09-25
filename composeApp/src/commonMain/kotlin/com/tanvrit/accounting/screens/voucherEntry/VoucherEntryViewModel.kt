package com.tanvrit.accounting.screens.voucherEntry

import com.tanvrit.accounting.data.AccountingSettingsStore
import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.network.VoucherNetwork
import com.tanvrit.accounting.repository.AccountRepository
import com.tanvrit.accounting.repository.FiscalPeriodRepository
import com.tanvrit.accounting.repository.VoucherRepository
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.FiscalPeriodStatus
import com.tanvrit.core.feature.accounting.model.Voucher
import com.tanvrit.core.feature.accounting.model.VoucherLineItem
import com.tanvrit.core.feature.accounting.model.VoucherType
import com.tanvrit.core.feature.accounting.network.CreateVoucherRequest
import com.tanvrit.core.feature.accounting.network.PostVoucherRequest
import com.tanvrit.core.feature.money.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** A voucher line as the editor holds it — amounts stay strings until post. */
data class VoucherLineDraft(
    val localId: String,
    val accountId: String = "",
    val accountCode: String = "",
    val accountName: String = "",
    /** Major units as typed ("12500.00"). Exactly one of debit/credit holds a value. */
    val debitText: String = "",
    val creditText: String = "",
    val narration: String = "",
)

data class VoucherEntryUiState(
    val businessId: String = "",
    val isLoading: Boolean = false,
    val isPosting: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val accounts: List<Account> = emptyList(),
    val voucherType: VoucherType = VoucherType.SALE,
    val date: String = "",
    val narration: String = "",
    val referenceId: String = "",
    val lines: List<VoucherLineDraft> = emptyList(),
    /** OFFLINE → saved locally; POSTED/CREATED → server-accepted. */
    val lastOutcome: String = "",
) {
    val totalDebit: Money get() = lines.fold(Money.ZERO) { acc, l -> acc + moneyOf(l.debitText) }
    val totalCredit: Money get() = lines.fold(Money.ZERO) { acc, l -> acc + moneyOf(l.creditText) }
    val isBalanced: Boolean get() = lines.isNotEmpty() && totalDebit == totalCredit && totalDebit > Money.ZERO

    private fun moneyOf(raw: String): Money = Money.fromDouble(raw.trim().replace(",", "").toDoubleOrNull() ?: 0.0)
}

/**
 * Voucher entry — Sales / Purchase / Receipt / Payment / Journal / Contra.
 *
 * Drafts validate double-entry continuously ([VoucherEntryUiState.isBalanced]).
 * Posting goes `createVoucher` (server mints the number per the business'
 * numbering series) → `postVoucher`. When the network is unreachable the
 * draft is written to the offline-first [VoucherRepository] so the sync
 * engine can push it on reconnect.
 */
class VoucherEntryViewModel(
    initialVoucherType: String = "SALE",
) : AppViewModel() {
    private val workspace: AccountingWorkspace = TanvritKoin.get()
    private val settingsStore: AccountingSettingsStore = TanvritKoin.get()
    private val voucherNetwork = VoucherNetwork.shared()
    private val voucherRepository: VoucherRepository = TanvritKoin.get()
    private val accountRepository: AccountRepository = TanvritKoin.get()
    private val fiscalPeriodRepository: FiscalPeriodRepository = TanvritKoin.get()

    private var nextLocalId = 1L

    private val _state =
        MutableStateFlow(
            VoucherEntryUiState(
                businessId = workspace.businessId.value,
                voucherType = VoucherType.entries.firstOrNull { it.code == initialVoucherType } ?: VoucherType.SALE,
                date = todayIso(),
                lines = listOf(newLine(), newLine()),
            ),
        )
    val state = _state.asStateFlow()

    init {
        scope.launch {
            workspace.businessId.collect { businessId ->
                _state.value =
                    _state.value.copy(businessId = businessId, error = null)
                if (businessId.isNotBlank()) loadAccounts(businessId)
            }
        }
    }

    private fun loadAccounts(businessId: String) {
        scope.launch {
            runCatching { accountRepository.findByBusinessId(businessId, null, true, 0, PAGE_SIZE) }
                .onSuccess { accounts -> _state.value = _state.value.copy(accounts = accounts) }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun setVoucherType(type: VoucherType) {
        _state.value = _state.value.copy(voucherType = type, lines = smartDefaults(type))
    }

    fun updateLine(
        localId: String,
        transform: (VoucherLineDraft) -> VoucherLineDraft,
    ) {
        _state.value =
            _state.value.copy(
                lines = _state.value.lines.map { if (it.localId == localId) transform(it) else it },
            )
    }

    fun setAccountOnLine(
        localId: String,
        account: Account,
    ) {
        updateLine(localId) {
            it.copy(accountId = account.id, accountCode = account.accountCode, accountName = account.name)
        }
    }

    fun addLine() {
        _state.value = _state.value.copy(lines = _state.value.lines + newLine())
    }

    fun removeLine(localId: String) {
        val lines = _state.value.lines
        if (lines.size <= 2) return
        _state.value = _state.value.copy(lines = lines.filterNot { it.localId == localId })
    }

    fun updateHeader(transform: (VoucherEntryUiState) -> VoucherEntryUiState) {
        _state.value = transform(_state.value)
    }

    fun clearNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun submit(andPost: Boolean) {
        val snapshot = _state.value
        val businessId = snapshot.businessId
        if (businessId.isBlank()) return
        val validationError = validate(snapshot)
        if (validationError != null) {
            _state.value = snapshot.copy(error = validationError)
            return
        }

        scope.launch {
            _state.value = _state.value.copy(isPosting = true, error = null)

            val fiscalPeriodId = resolveOpenPeriodId(businessId, snapshot.date)
            val request =
                CreateVoucherRequest(
                    businessId = businessId,
                    voucherType = snapshot.voucherType,
                    date = snapshot.date,
                    narration = snapshot.narration.trim(),
                    referenceId = snapshot.referenceId.trim(),
                    lineItems = snapshot.lines.map { it.toModel() },
                    fiscalPeriodId = fiscalPeriodId,
                )

            voucherNetwork
                .createVoucherAsync(request)
                .onSuccess { response ->
                    val created = response.payload
                    if (andPost && created != null) {
                        voucherNetwork
                            .postVoucherAsync(
                                PostVoucherRequest(
                                    id = created.id,
                                    businessId = businessId,
                                    postedBy = workspace.currentUserId(),
                                ),
                            ).onSuccess {
                                _state.value =
                                    _state.value.copy(
                                        isPosting = false,
                                        notice = "Voucher ${it.payload?.voucherNumber ?: created.voucherNumber} posted",
                                        lastOutcome = "POSTED",
                                    )
                                resetForm()
                            }.onFailure { postError ->
                                persistOffline(created, businessId)
                                _state.value =
                                    _state.value.copy(
                                        isPosting = false,
                                        error = "Created as draft; posting failed: ${postError.message}",
                                        lastOutcome = "DRAFT",
                                    )
                            }
                    } else {
                        _state.value =
                            _state.value.copy(
                                isPosting = false,
                                notice = "Draft ${created?.voucherNumber?.ifBlank { "voucher" } ?: "voucher"} saved",
                                lastOutcome = "DRAFT",
                            )
                        resetForm()
                    }
                }.onFailure {
                    // Offline path: persist locally so the sync engine pushes later.
                    persistOffline(null, businessId)
                    _state.value =
                        _state.value.copy(
                            isPosting = false,
                            notice = "Saved offline — will sync when the connection is back",
                            lastOutcome = "OFFLINE",
                        )
                    resetForm()
                }
        }
    }

    private fun validate(state: VoucherEntryUiState): String? =
        when {
            state.date.isBlank() -> "Voucher date is required"
            state.lines.count { it.accountId.isNotBlank() } < 2 -> "Pick an account on at least two lines"
            state.lines.any { it.accountId.isBlank() && (it.debitText.isNotBlank() || it.creditText.isNotBlank()) } ->
                "Every line with an amount needs an account"
            !state.isBalanced -> "Debits must equal credits and be non-zero"
            else -> null
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

    private suspend fun persistOffline(
        voucher: Voucher?,
        businessId: String,
    ) {
        val snapshot = _state.value
        val draft =
            voucher ?: Voucher(
                businessId = businessId,
                voucherNumber = "",
                voucherType = snapshot.voucherType,
                date = snapshot.date,
                narration = snapshot.narration.trim(),
                referenceId = snapshot.referenceId.trim(),
                lineItems = snapshot.lines.map { it.toModel() },
            )
        runCatching { voucherRepository.insert(draft) }
    }

    private fun resetForm() {
        _state.value =
            _state.value.copy(
                narration = "",
                referenceId = "",
                lines = smartDefaults(_state.value.voucherType),
            )
    }

    private fun newLine() = VoucherLineDraft(localId = "line-${nextLocalId++}")

    /** Smart defaults: pre-fill the conventional two legs per voucher type. */
    private fun smartDefaults(type: VoucherType): List<VoucherLineDraft> {
        val accounts = _state.value.accounts

        fun find(namePart: String): Account? = accounts.firstOrNull { it.name.contains(namePart, ignoreCase = true) }

        fun line(account: Account?): VoucherLineDraft {
            val draft = newLine()
            return if (account == null) {
                draft
            } else {
                draft.copy(accountId = account.id, accountCode = account.accountCode, accountName = account.name)
            }
        }
        return when (type) {
            VoucherType.SALE -> listOf(line(find("cash") ?: find("bank")), line(find("revenue") ?: find("sales")))
            VoucherType.PURCHASE -> listOf(line(find("purchase") ?: find("expense")), line(find("cash") ?: find("bank")))
            VoucherType.RECEIPT -> listOf(line(find("cash") ?: find("bank")), line(find("receivable") ?: find("debtor")))
            VoucherType.PAYMENT -> listOf(line(find("payable") ?: find("creditor")), line(find("cash") ?: find("bank")))
            else -> listOf(newLine(), newLine())
        }
    }

    private fun todayIso(): String =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()

    private companion object {
        const val PAGE_SIZE = 500
    }
}

private fun VoucherLineDraft.toModel(): VoucherLineItem =
    VoucherLineItem(
        accountId = accountId,
        accountCode = accountCode,
        accountName = accountName,
        debit = Money.fromDouble(debitText.trim().replace(",", "").toDoubleOrNull() ?: 0.0),
        credit = Money.fromDouble(creditText.trim().replace(",", "").toDoubleOrNull() ?: 0.0),
        narration = narration.trim(),
    )
