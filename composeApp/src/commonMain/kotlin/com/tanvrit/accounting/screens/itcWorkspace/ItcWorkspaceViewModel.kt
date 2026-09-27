package com.tanvrit.accounting.screens.itcWorkspace

import com.tanvrit.accounting.data.AccountingSettingsStore
import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.repository.AccountRepository
import com.tanvrit.accounting.repository.VoucherRepository
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.model.VoucherType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ItcWorkspaceUiState(
    val businessId: String = "",
    val businessGstin: String = "",
    val isLoading: Boolean = false,
    val pasteText: String = "",
    val detectedFormat: String? = null,
    val parsedRows: Int = 0,
    val parseErrors: List<String> = emptyList(),
    val results: List<ItcMatchRow> = emptyList(),
    val bucketFilter: Set<ItcBucket> = ItcBucket.entries.toSet(),
    val error: String? = null,
    val notice: String? = null,
)

/** ITC workspace (roadmap #8) — manual GSTR-2B import + client-side matching. */
class ItcWorkspaceViewModel : AppViewModel() {
    private val workspace: AccountingWorkspace = TanvritKoin.get()
    private val settingsStore: AccountingSettingsStore = TanvritKoin.get()
    private val voucherRepository: VoucherRepository = TanvritKoin.get()
    private val accountRepository: AccountRepository = TanvritKoin.get()

    private val _state = MutableStateFlow(ItcWorkspaceUiState(businessId = workspace.businessId.value))
    val state = _state.asStateFlow()

    init {
        scope.launch {
            workspace.businessId.collect { businessId ->
                _state.value = ItcWorkspaceUiState(businessId = businessId)
                if (businessId.isNotBlank()) refresh()
            }
        }
        scope.launch {
            // Settings store holds the business GSTIN (typed in Settings) — shown
            // above the paste area so the user can confirm the 2B belongs to them.
            settingsStore.settings.collect { settings ->
                _state.value = _state.value.copy(businessGstin = settings.gstin)
            }
        }
    }

    fun refresh() {
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            // Nothing server-bound to refresh — matching runs on import; this
            // just resets the busy state consistently with sibling screens.
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    fun setPaste(text: String) {
        _state.value = _state.value.copy(pasteText = text)
    }

    fun importAndMatch() {
        val businessId = _state.value.businessId
        if (businessId.isBlank()) return
        val text = _state.value.pasteText
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val parsed = ItcImportParser.parse(text)
            val accounts =
                runCatching { accountRepository.findByBusinessId(businessId, null, null, 0, PAGE_SIZE) }
                    .getOrDefault(emptyList())
                    .associateBy { it.id }
            val vouchers =
                runCatching {
                    voucherRepository
                        .findByBusinessId(businessId, null, null, null, 0, PAGE_SIZE)
                        .filter { it.voucherType == VoucherType.PURCHASE }
                }.getOrDefault(emptyList())
            val results = ItcMatcher.match(parsed.rows, vouchers, accounts)
            _state.value =
                _state.value.copy(
                    isLoading = false,
                    detectedFormat = parsed.detectedFormat,
                    parsedRows = parsed.rows.size,
                    parseErrors = parsed.errors,
                    results = results,
                    notice =
                        if (parsed.rows.isNotEmpty()) {
                            "Parsed ${parsed.rows.size} rows (${parsed.detectedFormat}); " +
                                "matched against ${vouchers.size} posted purchases"
                        } else {
                            null
                        },
                    error = if (parsed.rows.isEmpty()) parsed.errors.firstOrNull() ?: "No rows parsed" else null,
                )
        }
    }

    fun toggleBucket(bucket: ItcBucket) {
        _state.value =
            _state.value.copy(
                bucketFilter = _state.value.bucketFilter.let { if (bucket in it) it - bucket else it + bucket },
            )
    }

    fun mismatchesCsv(): String {
        val header = "bucket,supplier,invoice,twoB_tax,books_tax,diff"
        val lines =
            _state.value.results
                .filter {
                    it.bucket == ItcBucket.AMOUNT_MISMATCH ||
                        it.bucket == ItcBucket.MISSING_IN_BOOKS ||
                        it.bucket == ItcBucket.DUPLICATE_IN_BOOKS
                }.joinToString("\n") {
                    "${it.bucket.code},\"${it.supplierLabel.replace(
                        "\"",
                        "\"\"",
                    )}\",${it.row?.invoiceNumber ?: it.voucher?.referenceId.orEmpty()},${minorToMajor(
                        it.twoBTaxMinorUnits,
                    )},${minorToMajor(it.booksTaxMinorUnits)},${minorToMajor(it.diffMinorUnits)}"
                }
        return header + "\n" + lines
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun clearNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    private fun minorToMajor(minor: Long): String {
        val sign = if (minor < 0) "-" else ""
        val abs = kotlin.math.abs(minor)
        return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    private companion object {
        const val PAGE_SIZE = 500
    }
}
