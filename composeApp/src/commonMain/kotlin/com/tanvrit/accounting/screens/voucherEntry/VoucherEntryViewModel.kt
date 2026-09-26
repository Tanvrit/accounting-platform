package com.tanvrit.accounting.screens.voucherEntry

import com.tanvrit.accounting.data.AccountingSettingsStore
import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.data.NumberingSeriesStore
import com.tanvrit.accounting.data.VoucherTemplate
import com.tanvrit.accounting.data.VoucherTemplateLeg
import com.tanvrit.accounting.data.VoucherTemplateStore
import com.tanvrit.accounting.data.VoucherWorkflowStore
import com.tanvrit.accounting.network.VoucherNetwork
import com.tanvrit.accounting.repository.AccountRepository
import com.tanvrit.accounting.repository.FiscalPeriodRepository
import com.tanvrit.accounting.repository.VoucherRepository
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.extension.preserveBase
import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.FiscalPeriodStatus
import com.tanvrit.core.feature.accounting.model.Voucher
import com.tanvrit.core.feature.accounting.model.VoucherLineItem
import com.tanvrit.core.feature.accounting.model.VoucherStatus
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

/** A voucher row in the approval queue: effective workflow stage + local approver note. */
data class ApprovalEntry(
    val voucher: Voucher,
    val stage: VoucherWorkflowStage,
    val approverNote: String = "",
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
    /** Client-side preview from the business' numbering series (#10) — never a wire field. */
    val voucherNumber: String = "",
    val lines: List<VoucherLineDraft> = emptyList(),
    /** OFFLINE → saved locally; POSTED/CREATED → server-accepted. */
    val lastOutcome: String = "",
    val showTemplatePicker: Boolean = false,
    val showSaveTemplate: Boolean = false,
    val showApprovals: Boolean = false,
    val templates: List<VoucherTemplate> = emptyList(),
    /** Draft/verified vouchers populating the approval queue sheet (#11). */
    val approvals: List<ApprovalEntry> = emptyList(),
    /** In-progress approver-note text, keyed by voucher id. */
    val noteDrafts: Map<String, String> = emptyMap(),
    val approvalBusy: Boolean = false,
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
 *
 * Roadmap #10 surfaces: the `Voucher #` preview comes from the client-local
 * [NumberingSeriesStore] (consumed once per saved draft; never sent on the
 * wire — `CreateVoucherRequest` has no number field), and saved templates
 * ([VoucherTemplateStore]) prefill the legs via [applyTemplate].
 * Roadmap #11 surfaces: the approval queue ([refreshApprovals]) lists
 * cached draft/verified vouchers with [VoucherWorkflow]-gated Verify/Post
 * actions; verification + approver note live in [VoucherWorkflowStore] until
 * the server grows a VERIFIED status.
 */
class VoucherEntryViewModel(
    initialVoucherType: String = "SALE",
) : AppViewModel() {
    private val workspace: AccountingWorkspace = TanvritKoin.get()
    private val settingsStore: AccountingSettingsStore = TanvritKoin.get()
    private val numberingStore: NumberingSeriesStore = TanvritKoin.get()
    private val templateStore: VoucherTemplateStore = TanvritKoin.get()
    private val workflowStore: VoucherWorkflowStore = TanvritKoin.get()
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
                templateStore.selectBusiness(businessId)
                numberingStore.selectBusiness(businessId)
                workflowStore.selectBusiness(businessId)
                _state.value =
                    _state.value.copy(businessId = businessId, error = null)
                if (businessId.isNotBlank()) {
                    loadAccounts(businessId)
                    refreshNumberPreview()
                    if (_state.value.showApprovals) refreshApprovals()
                }
            }
        }
        scope.launch {
            templateStore.templates.collect { templates ->
                _state.value = _state.value.copy(templates = templates)
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
        _state.value =
            _state.value.copy(
                voucherType = type,
                voucherNumber = numberingStore.peekNumber(type),
                lines = smartDefaults(type),
            )
    }

    fun setVoucherNumber(value: String) {
        _state.value = _state.value.copy(voucherNumber = value)
    }

    /** Show the active business' next number for the current type (business switch / type switch / after save). */
    private fun refreshNumberPreview() {
        if (_state.value.businessId.isBlank()) return
        _state.value = _state.value.copy(voucherNumber = numberingStore.peekNumber(_state.value.voucherType))
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

    // ── Templates (roadmap #10) ───────────────────────────────────────────

    fun openTemplatePicker() {
        _state.value = _state.value.copy(showTemplatePicker = true)
    }

    fun closeTemplatePicker() {
        _state.value = _state.value.copy(showTemplatePicker = false)
    }

    fun openSaveTemplate() {
        if (_state.value.lines.none { it.accountId.isNotBlank() }) {
            _state.value = _state.value.copy(error = "Pick an account on at least one line before saving a template")
            return
        }
        _state.value = _state.value.copy(showSaveTemplate = true)
    }

    fun closeSaveTemplate() {
        _state.value = _state.value.copy(showSaveTemplate = false)
    }

    /** Saves the current legs (account + narration + amounts as typed) as a reusable template. */
    fun saveCurrentAsTemplate(name: String) {
        val snapshot = _state.value
        val legs =
            snapshot.lines
                .filter { it.accountId.isNotBlank() }
                .map {
                    VoucherTemplateLeg(
                        accountId = it.accountId,
                        accountCode = it.accountCode,
                        accountName = it.accountName,
                        debit = it.debitText.trim(),
                        credit = it.creditText.trim(),
                        narration = it.narration.trim(),
                    )
                }
        if (name.isBlank() || legs.isEmpty() || snapshot.businessId.isBlank()) return
        templateStore.save(name, snapshot.voucherType.code, legs)
        _state.value =
            _state.value.copy(
                showSaveTemplate = false,
                notice = "Template “${name.trim()}” saved",
            )
    }

    /** Replaces the composer with the template's legs and switches to its voucher type. */
    fun applyTemplate(template: VoucherTemplate) {
        val type = VoucherType.entries.firstOrNull { it.code == template.voucherType } ?: _state.value.voucherType
        val lines =
            template.legs.map { leg ->
                newLine().copy(
                    accountId = leg.accountId,
                    accountCode = leg.accountCode,
                    accountName = leg.accountName,
                    debitText = leg.debit,
                    creditText = leg.credit,
                    narration = leg.narration,
                )
            }
        _state.value =
            _state.value.copy(
                voucherType = type,
                voucherNumber = numberingStore.peekNumber(type),
                lines = if (lines.isEmpty()) smartDefaults(type) else lines,
                showTemplatePicker = false,
                notice = "Template “${template.name}” applied",
            )
    }

    fun deleteTemplate(templateId: String) {
        templateStore.delete(templateId)
    }

    // ── Approval workflow (roadmap #11) ───────────────────────────────────

    fun openApprovals() {
        _state.value = _state.value.copy(showApprovals = true)
        refreshApprovals()
    }

    fun closeApprovals() {
        _state.value = _state.value.copy(showApprovals = false)
    }

    /**
     * Reloads the approval queue from the offline-first [VoucherRepository]
     * cache, lifted by the local verification overlay. CANCELLED/REVERSED and
     * POSTED vouchers drop out (terminal per [VoucherWorkflow]).
     */
    fun refreshApprovals() {
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        scope.launch {
            val vouchers =
                runCatching { voucherRepository.findByBusinessId(businessId, null, null, null, 0, PAGE_SIZE) }
                    .getOrDefault(emptyList())
            val entries =
                vouchers.mapNotNull { voucher ->
                    val stage = VoucherWorkflow.stageOf(voucher.status, workflowStore.isVerified(voucher.id))
                    if (!VoucherWorkflow.isActionable(stage)) {
                        null
                    } else {
                        ApprovalEntry(
                            voucher = voucher,
                            stage = stage,
                            approverNote = workflowStore.noteFor(voucher.id),
                        )
                    }
                }
            _state.value = _state.value.copy(approvals = entries)
        }
    }

    fun setNoteDraft(
        voucherId: String,
        note: String,
    ) {
        _state.value = _state.value.copy(noteDrafts = _state.value.noteDrafts + (voucherId to note))
    }

    /** DRAFT → VERIFIED — client-local ([VoucherWorkflow] documents why there is no RPC). */
    fun verifyVoucher(voucherId: String) {
        val entry = _state.value.approvals.firstOrNull { it.voucher.id == voucherId } ?: return
        if (!VoucherWorkflow.allowed(entry.stage, VoucherWorkflowStage.VERIFIED)) {
            _state.value = _state.value.copy(error = "Only drafts can be verified")
            return
        }
        val noteDraft = _state.value.noteDrafts[voucherId].orEmpty()
        val note = noteDraft.trim()
        workflowStore.markVerified(voucherId, workspace.currentUserId(), note)
        _state.value =
            _state.value.copy(
                noteDrafts = _state.value.noteDrafts - voucherId,
                notice = "Voucher ${entry.voucher.voucherNumber.ifBlank { voucherId }} verified",
            )
        refreshApprovals()
    }

    /** VERIFIED → POSTED through the real `postVoucherAsync`; the local cache is upserted on success. */
    fun postVerifiedVoucher(voucherId: String) {
        val snapshot = _state.value
        val entry = snapshot.approvals.firstOrNull { it.voucher.id == voucherId } ?: return
        if (!VoucherWorkflow.allowed(entry.stage, VoucherWorkflowStage.POSTED)) {
            _state.value = snapshot.copy(error = "Only verified vouchers can be posted")
            return
        }
        val businessId = snapshot.businessId
        if (businessId.isBlank()) return
        scope.launch {
            _state.value = _state.value.copy(approvalBusy = true, error = null)
            voucherNetwork
                .postVoucherAsync(
                    PostVoucherRequest(
                        id = voucherId,
                        businessId = businessId,
                        postedBy = workspace.currentUserId(),
                    ),
                ).onSuccess { response ->
                    upsertPosted(response.payload, voucherId)
                    _state.value =
                        _state.value.copy(
                            approvalBusy = false,
                            notice = "Voucher ${response.payload?.voucherNumber ?: entry.voucher.voucherNumber} posted",
                        )
                    refreshApprovals()
                }.onFailure { postError ->
                    _state.value =
                        _state.value.copy(
                            approvalBusy = false,
                            error = "Posting failed: ${postError.message}",
                        )
                }
        }
    }

    /** Successful mutations upsert the local cache; if the payload is missing, flip the cached copy in place. */
    private suspend fun upsertPosted(
        payload: Voucher?,
        voucherId: String,
    ) {
        runCatching {
            if (payload != null) {
                voucherRepository.update(payload)
            } else {
                voucherRepository.findById(voucherId)?.let { local ->
                    voucherRepository.update(
                        local
                            .copy(status = VoucherStatus.POSTED, postedBy = workspace.currentUserId())
                            .preserveBase(local),
                    )
                }
            }
        }
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
                    // The displayed number was shown on this draft — consume it exactly once.
                    numberingStore.consumeNumber(snapshot.voucherType)
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
                                        voucherNumber = numberingStore.peekNumber(snapshot.voucherType),
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
                    numberingStore.consumeNumber(snapshot.voucherType)
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
                voucherNumber = snapshot.voucherNumber.trim(),
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
                voucherNumber = numberingStore.peekNumber(_state.value.voucherType),
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
