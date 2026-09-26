package com.tanvrit.accounting.screens.importWizard

import com.tanvrit.accounting.data.AccountingWorkspace
import com.tanvrit.accounting.network.AccountNetwork
import com.tanvrit.accounting.repository.AccountRepository
import com.tanvrit.core.app.AppViewModel
import com.tanvrit.core.di.TanvritKoin
import com.tanvrit.core.feature.accounting.network.CreateAccountRequest
import com.tanvrit.core.feature.accounting.network.ImportAccountsRequest
import com.tanvrit.core.feature.money.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ImportStage { UPLOAD, PREVIEW, IMPORTING, DONE }

data class ImportAccountsUiState(
    val businessId: String = "",
    val stage: ImportStage = ImportStage.UPLOAD,
    val rawText: String = "",
    val parsed: AccountImportParse? = null,
    /** true → only valid rows are sent; false → importing is blocked until every row parses. */
    val skipInvalid: Boolean = true,
    val confirmVisible: Boolean = false,
    val chunksTotal: Int = 0,
    val chunksDone: Int = 0,
    val sendCount: Int = 0,
    val importedCount: Int = 0,
    val failedCount: Int = 0,
    val skippedInvalidCount: Int = 0,
    /** Server `message` values + client-side parent-resolution failures, verbatim. */
    val messages: List<String> = emptyList(),
    val openingNote: String? = null,
    val error: String? = null,
)

/**
 * Chart-of-Accounts + opening-balance import wizard (roadmap #5).
 *
 * Paste → parse ([AccountImportParser]) → preview → chunked
 * [AccountNetwork.importAccountsAsync] calls (≤ [CHUNK_SIZE] accounts per
 * request). Opening balances travel INSIDE each `CreateAccountRequest`
 * (`openingBalance`, major-unit string, signed +ve = Dr) — the DTO has a
 * balance field, so no compensating journal voucher is created; the server
 * carries the opening balance straight into the account's current balance.
 *
 * Parent links: the endpoint wants a parent account ID, so a row's parent
 * CODE is resolved against the existing cached chart. A code that is not
 * already in the chart fails client-side ("create the parent first") — rows
 * in the same paste cannot link to each other because account ids are
 * server-assigned.
 */
class ImportAccountsViewModel : AppViewModel() {
    private val workspace: AccountingWorkspace = TanvritKoin.get()
    private val accountNetwork = AccountNetwork.shared()
    private val accountRepository: AccountRepository = TanvritKoin.get()

    private val _state = MutableStateFlow(ImportAccountsUiState(businessId = workspace.businessId.value))
    val state = _state.asStateFlow()

    init {
        scope.launch {
            workspace.businessId.collect { businessId ->
                _state.value = _state.value.copy(businessId = businessId)
            }
        }
    }

    fun onTextChange(text: String) {
        val parsed = if (text.isBlank()) null else AccountImportParser.parseCsv(text)
        _state.value = _state.value.copy(rawText = text, parsed = parsed)
    }

    fun toPreview() {
        val parsed = _state.value.parsed ?: return
        if (parsed.rows.isEmpty()) return
        _state.value = _state.value.copy(stage = ImportStage.PREVIEW, error = null)
    }

    fun backToUpload() {
        _state.value = _state.value.copy(stage = ImportStage.UPLOAD)
    }

    fun toggleSkipInvalid() {
        _state.value = _state.value.copy(skipInvalid = !_state.value.skipInvalid)
    }

    fun showConfirm() {
        _state.value = _state.value.copy(confirmVisible = true)
    }

    fun hideConfirm() {
        _state.value = _state.value.copy(confirmVisible = false)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun startOver() {
        _state.value = ImportAccountsUiState(businessId = _state.value.businessId)
    }

    fun runImport() {
        val snapshot = _state.value
        val parsed = snapshot.parsed ?: return
        if (snapshot.businessId.isBlank()) return
        if (!snapshot.skipInvalid && parsed.invalidCount > 0) {
            _state.value =
                snapshot.copy(
                    confirmVisible = false,
                    error = "${parsed.invalidCount} rows have errors — fix them or enable \"Skip invalid rows\"",
                )
            return
        }
        val candidates = parsed.validRows
        if (candidates.isEmpty()) {
            _state.value = snapshot.copy(confirmVisible = false, error = "Nothing valid to import")
            return
        }

        scope.launch {
            _state.value =
                _state.value.copy(
                    stage = ImportStage.IMPORTING,
                    confirmVisible = false,
                    error = null,
                    importedCount = 0,
                    failedCount = 0,
                    messages = emptyList(),
                )

            // Resolve parent codes against the existing cached chart — ids are
            // server-assigned, so same-paste parents cannot be referenced.
            val existing =
                runCatching {
                    accountRepository.findByBusinessId(snapshot.businessId, null, null, 0, PAGE_SIZE)
                }.getOrDefault(emptyList())
            val idByCode = existing.associateBy({ it.accountCode.lowercase() }, { it.id })

            val messages = mutableListOf<String>()
            var failed = 0
            val requests = mutableListOf<CreateAccountRequest>()
            candidates.forEach { row ->
                val parent = row.parentCode.trim()
                val parentId = idByCode[parent.lowercase()]
                if (parent.isNotEmpty() && parentId == null) {
                    failed++
                    messages +=
                        "Row ${row.rowNumber}: parent \"$parent\" not found in the existing chart — " +
                        "create the parent account first, then re-import"
                } else {
                    requests += row.toRequest(snapshot.businessId, parentId.orEmpty())
                }
            }

            val chunks = requests.chunked(CHUNK_SIZE)
            var imported = 0
            _state.value = _state.value.copy(chunksTotal = chunks.size, chunksDone = 0)
            chunks.forEach { chunk ->
                accountNetwork
                    .importAccountsAsync(
                        ImportAccountsRequest(
                            businessId = snapshot.businessId,
                            accounts = chunk,
                            updateExisting = false,
                        ),
                    ).onSuccess { response ->
                        if (response.status == "SUCCESS") {
                            imported += chunk.size
                        } else {
                            // The SDK surface deserialises the import result into
                            // AccountResponse (no per-row created/errors) — report the
                            // server's summary message verbatim instead of guessing counts.
                            if (response.message.isNotBlank()) {
                                messages += "Server (${chunk.size} rows): ${response.message}"
                            }
                        }
                    }.onFailure { failure ->
                        failed += chunk.size
                        messages += "Chunk failed: ${failure.message ?: "unknown error"}"
                    }
                _state.value =
                    _state.value.copy(
                        chunksDone = _state.value.chunksDone + 1,
                        importedCount = imported,
                        failedCount = failed,
                        messages = messages.toList(),
                    )
            }

            val openingCount = candidates.count { it.opening != Money.ZERO }
            _state.value =
                _state.value.copy(
                    stage = ImportStage.DONE,
                    sendCount = requests.size,
                    skippedInvalidCount = parsed.invalidCount,
                    openingNote =
                        if (openingCount > 0) {
                            "$openingCount accounts carry opening balances (positive = Dr). " +
                                "The server carries each opening balance straight into the account's " +
                                "current balance — no balancing journal voucher is created."
                        } else {
                            null
                        },
                )
        }
    }

    private fun AccountImportRow.toRequest(
        businessId: String,
        parentAccountId: String,
    ): CreateAccountRequest =
        CreateAccountRequest(
            businessId = businessId,
            accountCode = code,
            name = name,
            type = type,
            parentAccountId = parentAccountId,
            description = narration,
            openingBalance = opening.toString(),
        )

    companion object {
        private const val CHUNK_SIZE = 100
        private const val PAGE_SIZE = 500
    }
}
