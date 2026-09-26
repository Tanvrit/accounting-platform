package com.tanvrit.accounting.screens.importWizard

import com.tanvrit.core.feature.accounting.model.AccountType
import com.tanvrit.core.feature.money.Money

/** Debit/credit side of an opening balance, as typed in the import sheet. */
enum class BalanceSide { DR, CR }

/**
 * One parsed account row from the import sheet.
 *
 * [opening] is the SIGNED opening balance with the double-entry convention
 * used throughout the app: positive = Dr balance, negative = Cr balance. A
 * negative amount with an explicit side flips that side ("-500 DR" == "500
 * CR"); without a side column the sign is carried as-is.
 */
data class AccountImportRow(
    /** 1-based row number in the original paste (header row counts). */
    val rowNumber: Int,
    val raw: String,
    val name: String = "",
    val code: String = "",
    val type: AccountType = AccountType.ASSET,
    /** Parent account CODE as typed; resolved to an account id at import time. */
    val parentCode: String = "",
    val opening: Money = Money.ZERO,
    /** Explicit side after the negative-flip; null when the sheet had no side column. */
    val side: BalanceSide? = null,
    val narration: String = "",
    val errors: List<String> = emptyList(),
) {
    val isValid: Boolean get() = errors.isEmpty()
}

data class AccountImportParse(
    val rows: List<AccountImportRow>,
    val delimiter: Char,
    val hasHeader: Boolean,
) {
    val delimiterLabel: String get() = if (delimiter == '\t') "TSV" else "CSV"
    val validRows: List<AccountImportRow> get() = rows.filter { it.isValid }
    val invalidCount: Int get() = rows.count { !it.isValid }
}

/**
 * Chart-of-accounts CSV/TSV parser (roadmap #5).
 *
 * Detects the delimiter (tabs vs commas on the first line) and whether a
 * header row is present. Columns are matched by NAME, case-insensitive, via
 * the alias sets below — headerless pastes fall back to the positional order
 * `name, code, type, parent, opening, side, narration`. `type` accepts the
 * [AccountType] enum names, common synonyms (INCOME → REVENUE), and Tally
 * primary group names ("Sundry Debtors" → ASSET, the receivable-ish bucket —
 * [AccountType] has no subtypes, so groups collapse onto the five primaries
 * plus the tax buckets). Amounts follow `parseMoneyInput` cleaning rules
 * (trim, strip Indian/ lakh-grouping commas, `toDoubleOrNull`) but report
 * failure instead of silently returning ZERO. Rows never throw: every
 * problem lands in [AccountImportRow.errors] with its 1-based row number.
 */
object AccountImportParser {
    fun parseCsv(text: String): AccountImportParse {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return AccountImportParse(emptyList(), ',', false)

        val delimiter = detectDelimiter(lines.first())
        val firstCells = splitRow(lines.first(), delimiter).map { it.lowercase() }
        val hasHeader = firstCells.any { it in ALL_ALIASES }
        val fields =
            if (hasHeader) {
                firstCells.map { header -> FIELD_ALIASES.entries.firstOrNull { header in it.value }?.key }
            } else {
                POSITIONAL_ORDER.take(firstCells.size)
            }
        val body = if (hasHeader) lines.drop(1) else lines

        val rows = mutableListOf<AccountImportRow>()
        val seenCodes = mutableMapOf<String, Int>()
        body.forEachIndexed { index, line ->
            val rowNumber = index + (if (hasHeader) 2 else 1)
            rows += parseRow(rowNumber, line, splitRow(line, delimiter), fields, seenCodes)
        }
        return AccountImportParse(rows, delimiter, hasHeader)
    }

    private fun parseRow(
        rowNumber: Int,
        raw: String,
        cells: List<String>,
        fields: List<ImportField?>,
        seenCodes: MutableMap<String, Int>,
    ): AccountImportRow {
        fun cell(field: ImportField): String {
            val idx = fields.indexOf(field)
            return if (idx >= 0 && idx < cells.size) cells[idx] else ""
        }

        val errors = mutableListOf<String>()

        fun fail(message: String) {
            errors += "Row $rowNumber: $message"
        }

        val name = cell(ImportField.NAME)
        val code = cell(ImportField.CODE)
        if (name.isBlank()) fail("name is required")
        if (code.isBlank()) fail("account code is required")

        if (code.isNotBlank()) {
            val key = code.lowercase()
            // No putIfAbsent on commonMain MutableMap — explicit containsKey+put.
            val firstRow = if (seenCodes.containsKey(key)) seenCodes[key] else null
            if (firstRow == null) {
                seenCodes[key] = rowNumber
            } else {
                fail("duplicate account code \"$code\" (first used at row $firstRow)")
            }
        }

        val typeRaw = cell(ImportField.TYPE)
        val type = mapAccountType(typeRaw)
        if (type == null) {
            fail(
                "type \"$typeRaw\" not recognised — use ASSET/LIABILITY/EQUITY/REVENUE/EXPENSE " +
                    "or a Tally group (e.g. \"Sundry Debtors\")",
            )
        }

        val parentCode = cell(ImportField.PARENT)
        if (parentCode.isNotBlank() && parentCode.equals(code, ignoreCase = true)) {
            fail("account cannot be its own parent")
        }

        var amount = Money.ZERO
        val openingRaw = cell(ImportField.OPENING)
        if (openingRaw.isNotBlank()) {
            val parsed = parseAmount(openingRaw)
            if (parsed == null) {
                fail("opening balance \"$openingRaw\" is not a number")
            } else {
                amount = parsed
            }
        }

        var side: BalanceSide? = null
        val sideRaw = cell(ImportField.SIDE)
        if (sideRaw.isNotBlank()) {
            val parsed = parseSide(sideRaw)
            if (parsed == null) {
                fail("side \"$sideRaw\" not recognised (use DR or CR)")
            } else {
                side = parsed
            }
        }

        // Side flip on negatives: "-500 DR" is a credit balance of 500.
        if (amount.isNegative && side != null) {
            amount = amount.absoluteValue
            side = if (side == BalanceSide.DR) BalanceSide.CR else BalanceSide.DR
        }
        val opening = if (side == BalanceSide.CR) -amount else amount

        return AccountImportRow(
            rowNumber = rowNumber,
            raw = raw,
            name = name,
            code = code,
            type = type ?: AccountType.ASSET,
            parentCode = parentCode,
            opening = opening,
            side = side,
            narration = cell(ImportField.NARRATION),
            errors = errors,
        )
    }

    /** Tab wins when the first line is at least as tab-y as comma-y (Excel pastes). */
    internal fun detectDelimiter(firstLine: String): Char {
        val tabs = firstLine.count { it == '\t' }
        val commas = firstLine.count { it == ',' }
        return if (tabs > 0 && tabs >= commas) '\t' else ','
    }

    /**
     * RFC-4180-lite splitter: honours double-quoted cells (`"a, b"`, `""` for a
     * literal quote) so Indian-grouped amounts like `"1,50,000.00"` survive
     * CSV. No multi-line quoted cells — one ledger row per line.
     */
    internal fun splitRow(
        line: String,
        delimiter: Char,
    ): List<String> {
        val cells = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> {
                    cells += current.toString().trim()
                    current = StringBuilder()
                }
                else -> current.append(c)
            }
            i++
        }
        cells += current.toString().trim()
        return cells
    }

    /** Blank → ASSET (matches the Chart-of-Accounts editor default); unknown → null. */
    internal fun mapAccountType(raw: String): AccountType? {
        val norm =
            raw
                .trim()
                .lowercase()
                .replace('_', ' ')
                .replace(Regex("\\s+"), " ")
        if (norm.isEmpty()) return AccountType.ASSET
        TYPE_NAMES[norm]?.let { return it }
        TALLY_GROUPS[norm]?.let { return it }
        return null
    }

    /** Same cleaning rules as `parseMoneyInput`, but failure is reported, not zeroed. */
    private fun parseAmount(raw: String): Money? {
        val value = raw.trim().replace(",", "").toDoubleOrNull() ?: return null
        return Money.fromDouble(value)
    }

    private fun parseSide(raw: String): BalanceSide? =
        when (raw.trim().lowercase()) {
            "dr", "d", "debit" -> BalanceSide.DR
            "cr", "c", "credit" -> BalanceSide.CR
            else -> null
        }

    private enum class ImportField { NAME, CODE, TYPE, PARENT, OPENING, SIDE, NARRATION }

    private val POSITIONAL_ORDER =
        listOf(
            ImportField.NAME,
            ImportField.CODE,
            ImportField.TYPE,
            ImportField.PARENT,
            ImportField.OPENING,
            ImportField.SIDE,
            ImportField.NARRATION,
        )

    private val FIELD_ALIASES: Map<ImportField, Set<String>> =
        mapOf(
            ImportField.NAME to setOf("name", "account name", "account"),
            ImportField.CODE to setOf("code", "account code"),
            ImportField.TYPE to setOf("type", "group", "account type"),
            ImportField.PARENT to setOf("parent", "parent code", "parent account"),
            ImportField.OPENING to setOf("opening", "opening balance", "balance"),
            ImportField.SIDE to setOf("side", "dr/cr", "drcr"),
            ImportField.NARRATION to setOf("narration", "description"),
        )

    private val ALL_ALIASES: Set<String> = FIELD_ALIASES.values.flatten().toSet()

    /** Direct enum names + common synonyms. INCOME maps to REVENUE ([AccountType] has no INCOME). */
    private val TYPE_NAMES: Map<String, AccountType> =
        mapOf(
            "asset" to AccountType.ASSET,
            "assets" to AccountType.ASSET,
            "liability" to AccountType.LIABILITY,
            "liabilities" to AccountType.LIABILITY,
            "equity" to AccountType.EQUITY,
            "capital" to AccountType.EQUITY,
            "revenue" to AccountType.REVENUE,
            "income" to AccountType.REVENUE,
            "incomes" to AccountType.REVENUE,
            "expense" to AccountType.EXPENSE,
            "expenses" to AccountType.EXPENSE,
            "gst input" to AccountType.GST_INPUT,
            "gstinput" to AccountType.GST_INPUT,
            "gst output" to AccountType.GST_OUTPUT,
            "gstoutput" to AccountType.GST_OUTPUT,
            "tds receivable" to AccountType.TDS_RECEIVABLE,
            "tcs payable" to AccountType.TCS_PAYABLE,
        )

    /** Tally primary groups → nearest primary AccountType (AccountType has no subtypes). */
    private val TALLY_GROUPS: Map<String, AccountType> =
        mapOf(
            "sundry debtors" to AccountType.ASSET,
            "bank accounts" to AccountType.ASSET,
            "cash-in-hand" to AccountType.ASSET,
            "cash in hand" to AccountType.ASSET,
            "deposits (asset)" to AccountType.ASSET,
            "loans & advances (asset)" to AccountType.ASSET,
            "loans and advances (asset)" to AccountType.ASSET,
            "stock-in-hand" to AccountType.ASSET,
            "stock in hand" to AccountType.ASSET,
            "fixed assets" to AccountType.ASSET,
            "investments" to AccountType.ASSET,
            "current assets" to AccountType.ASSET,
            "misc. expenses (asset)" to AccountType.ASSET,
            "sundry creditors" to AccountType.LIABILITY,
            "current liabilities" to AccountType.LIABILITY,
            "duties & taxes" to AccountType.LIABILITY,
            "duties and taxes" to AccountType.LIABILITY,
            "provisions" to AccountType.LIABILITY,
            "loans (liability)" to AccountType.LIABILITY,
            "bank od a/c" to AccountType.LIABILITY,
            "secured loans" to AccountType.LIABILITY,
            "unsecured loans" to AccountType.LIABILITY,
            "suspense a/c" to AccountType.LIABILITY,
            "capital account" to AccountType.EQUITY,
            "reserves & surplus" to AccountType.EQUITY,
            "reserves and surplus" to AccountType.EQUITY,
            "retained earnings" to AccountType.EQUITY,
            "sales accounts" to AccountType.REVENUE,
            "direct incomes" to AccountType.REVENUE,
            "indirect incomes" to AccountType.REVENUE,
            "purchase accounts" to AccountType.EXPENSE,
            "direct expenses" to AccountType.EXPENSE,
            "indirect expenses" to AccountType.EXPENSE,
            "manufacturing expenses" to AccountType.EXPENSE,
        )
}
