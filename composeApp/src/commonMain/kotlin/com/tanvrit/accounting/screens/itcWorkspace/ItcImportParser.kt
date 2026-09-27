package com.tanvrit.accounting.screens.itcWorkspace

import com.tanvrit.core.network.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One parsed GSTR-2B B2B row (all amounts minor-unit Longs; paise). */
data class ItcRow(
    val rowNumber: Int,
    val supplierGstin: String = "",
    val invoiceNumber: String = "",
    val invoiceDate: String = "",
    val taxableValueMinorUnits: Long = 0L,
    val igstMinorUnits: Long = 0L,
    val cgstMinorUnits: Long = 0L,
    val sgstMinorUnits: Long = 0L,
) {
    /** Total input-tax-credit for the row (what GSTR-2B allows claiming). */
    val itcMinorUnits: Long get() = igstMinorUnits + cgstMinorUnits + sgstMinorUnits
}

data class ItcParseResult(
    val rows: List<ItcRow>,
    val errors: List<String>,
    val detectedFormat: String, // "JSON" or "CSV" or "TSV"
)

/**
 * GSTR-2B import parser (roadmap #8). Accepts EITHER the JSON the GST offline
 * tools emit (`data.docdata.b2b[].inv[]` shape — tolerated leniently) OR a
 * CSV/TSV with a header row using common aliases. Money parsing is exact
 * minor-unit math (never Double — see `Money` currency-blindness hazard).
 */
object ItcImportParser {
    fun parse(text: String): ItcParseResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ItcParseResult(emptyList(), listOf("Nothing to parse"), "CSV")
        return if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            parseJson(trimmed)
        } else {
            parseDelimited(trimmed)
        }
    }

    // ---- JSON (GSTR-2B offline utility export) ----

    private fun parseJson(text: String): ItcParseResult {
        val element =
            runCatching { AppJson.json.parseToJsonElement(text) }.getOrNull()
                ?: return ItcParseResult(emptyList(), listOf("Invalid JSON"), "JSON")
        val b2b =
            findB2bArray(element) ?: return ItcParseResult(
                emptyList(),
                listOf("JSON found but no GSTR-2B b2b invoice array (expected data.docdata.b2b or b2b)"),
                "JSON",
            )
        val errors = mutableListOf<String>()
        val rows = mutableListOf<ItcRow>()
        var rowNumber = 0
        for (supplier in b2b) {
            val supplierObj = supplier as? JsonObject ?: continue
            val gstin = supplierObj.text("ctin") ?: supplierObj.text("gstin").orEmpty()
            val invoices = supplierObj.array("inv") ?: supplierObj.array("invoices") ?: continue
            for (inv in invoices) {
                val o = inv as? JsonObject ?: continue
                rowNumber++
                val invNum = o.text("inum") ?: o.text("invoiceNumber").orEmpty()
                val row =
                    ItcRow(
                        rowNumber = rowNumber,
                        supplierGstin = gstin.trim().uppercase(),
                        invoiceNumber = invNum.trim(),
                        invoiceDate = (o.text("dt") ?: o.text("idt") ?: o.text("invoiceDate")).orEmpty().trim(),
                        taxableValueMinorUnits = parseMinor((o.text("txval") ?: o.text("txable")).orEmpty()),
                        igstMinorUnits = parseMinor((o.text("igst") ?: o.numAsText("igstamt")).orEmpty()),
                        cgstMinorUnits = parseMinor((o.text("cgst") ?: o.text("cgstamt")).orEmpty()),
                        sgstMinorUnits = parseMinor((o.text("sgst") ?: o.text("sgstamt")).orEmpty()),
                    )
                if (row.invoiceNumber.isBlank()) errors += "Row $rowNumber: invoice number missing"
                if (row.taxableValueMinorUnits == 0L && row.itcMinorUnits == 0L) errors += "Row $rowNumber: no amounts"
                rows += row
            }
        }
        return ItcParseResult(rows, errors, "JSON")
    }

    private fun findB2bArray(element: JsonElement): JsonArray? {
        val obj = element as? JsonObject ?: return null
        obj.array("b2b")?.let { return it }
        obj
            .obj("data")
            ?.obj("docdata")
            ?.array("b2b")
            ?.let { return it }
        obj.obj("docdata")?.array("b2b")?.let { return it }
        // CDNR (credit/debit notes) folded in the same report are matched by the
        // same keys — include, they carry HRDN-correct ITC.
        return null
    }

    private fun JsonObject.obj(key: String): JsonObject? = (this[key] as? JsonObject)

    private fun JsonObject.array(key: String): JsonArray? = (this[key] as? JsonArray)

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonElement)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it != "null" }

    @Suppress("UNUSED_PARAMETER")
    private fun JsonObject.numAsText(key: String): String? = text(key)

    // ---- CSV / TSV ----

    private val HEADER_ALIASES: Map<String, String> =
        mapOf(
            "supplier gstin" to "gstin",
            "gstin" to "gstin",
            "suppliergstin" to "gstin",
            "invoice number" to "invnum",
            "invoice no" to "invnum",
            "bill no" to "invnum",
            "invc no" to "invnum",
            "invoice date" to "invdate",
            "date" to "invdate",
            "taxable value" to "taxable",
            "taxable" to "taxable",
            "igst" to "igst",
            "integrated tax" to "igst",
            "cgst" to "cgst",
            "central tax" to "cgst",
            "sgst" to "sgst",
            "state tax" to "sgst",
            "utgst" to "sgst",
            "invoice value" to "value",
            "value" to "value",
        )

    private fun parseDelimited(text: String): ItcParseResult {
        val rawLines =
            text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .split('\n')
                .filter { it.isNotBlank() }
        if (rawLines.isEmpty()) return ItcParseResult(emptyList(), listOf("No lines"), "CSV")
        val delimiter = if (rawLines.first().count { it == '\t' } >= rawLines.first().count { it == ',' }) '\t' else ','
        val format = if (delimiter == '\t') "TSV" else "CSV"
        val lines = rawLines.map { splitRespectingQuotes(it, delimiter) }

        val errors = mutableListOf<String>()
        var headerIndex: Map<String, Int> = emptyMap()
        var dataStart = 0
        val firstCellKey =
            HEADER_ALIASES[
                lines
                    .first()
                    .firstOrNull()
                    ?.trim()
                    ?.lowercase(),
            ]
        if (firstCellKey != null) {
            headerIndex =
                lines
                    .first()
                    .mapIndexedNotNull { index, cell ->
                        HEADER_ALIASES[cell.trim().lowercase()]?.let { alias -> alias to index }
                    }.toMap()
            dataStart = 1
        } else {
            // Headerless positional fallback: gstin, invnum, invdate, taxable, igst, cgst, sgst, value
            headerIndex =
                mapOf(
                    "gstin" to 0,
                    "invnum" to 1,
                    "invdate" to 2,
                    "taxable" to 3,
                    "igst" to 4,
                    "cgst" to 5,
                    "sgst" to 6,
                    "value" to 7,
                )
        }
        if (!headerIndex.containsKey("invnum")) {
            return ItcParseResult(
                emptyList(),
                listOf("No invoice-number column found (header aliases: invoice number / invoice no / bill no)"),
                format,
            )
        }

        val rows = mutableListOf<ItcRow>()
        val seenKeys = mutableMapOf<String, Int>()
        for (lineIndex in dataStart until lines.size) {
            val cells = lines[lineIndex]
            val rowNumber = lineIndex + 1

            fun cell(key: String): String = headerIndex[key]?.let { cells.getOrNull(it) }?.trim().orEmpty()

            val gstin = cell("gstin").uppercase()
            val invNum = cell("invnum")
            if (invNum.isBlank()) {
                errors += "Row $rowNumber: invoice number missing"
                continue
            }
            val row =
                ItcRow(
                    rowNumber = rowNumber,
                    supplierGstin = gstin,
                    invoiceNumber = invNum,
                    invoiceDate = cell("invdate"),
                    taxableValueMinorUnits = parseMinor(cell("taxable")),
                    igstMinorUnits = parseMinor(cell("igst")),
                    cgstMinorUnits = parseMinor(cell("cgst")),
                    sgstMinorUnits = parseMinor(cell("sgst")),
                )
            val key = "${row.supplierGstin}|${ItcMatcher.normalizeInvoiceNumber(row.invoiceNumber)}"
            seenKeys[key]?.let { first -> errors += "Row $rowNumber: duplicate of row $first (${row.invoiceNumber})" }
            seenKeys.putIfAbsentCommon(key, rowNumber)
            rows += row
        }
        return ItcParseResult(rows, errors, format)
    }

    /** RFC-4180-lite: split on delimiter outside of double quotes, unquote, unescape "". */
    private fun splitRespectingQuotes(
        line: String,
        delimiter: Char,
    ): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
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
                    out += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        out += current.toString()
        return out
    }

    /**
     * Major-unit decimal → minor-unit Long, EXACT string math (no Double — the
     * Money-class currency-blindness hazard). Accepts Indian grouping commas
     * and (₹1.00) parenthesized negatives. Returns 0 on blank/unparseable.
     */
    fun parseMinor(raw: String): Long {
        var s =
            raw
                .trim()
                .replace(",", "")
                .replace("₹", "")
                .trim()
        if (s.isEmpty()) return 0L
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true
            s = s.substring(1, s.length - 1).trim()
        }
        if (s.startsWith("-")) {
            negative = true
            s = s.drop(1)
        }
        val parts = s.split('.')
        val whole = parts[0].toLongOrNull() ?: return 0L
        val fracDigits = parts.getOrNull(1)?.filter { it.isDigit() } ?: ""
        val frac = (fracDigits + "00").substring(0, 2).toLongOrNull() ?: 0L
        val value = whole * 100 + frac
        return if (negative) -value else value
    }
}

private fun MutableMap<String, Int>.putIfAbsentCommon(
    key: String,
    value: Int,
) {
    if (!containsKey(key)) put(key, value)
}
