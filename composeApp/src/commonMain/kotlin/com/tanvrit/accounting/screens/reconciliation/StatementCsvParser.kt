package com.tanvrit.accounting.screens.reconciliation

import com.tanvrit.core.feature.accounting.model.BankStatement
import com.tanvrit.core.feature.accounting.model.BankTransaction
import com.tanvrit.core.feature.money.Money
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Minimal CSV bank-statement parser.
 *
 * Expected columns (header optional, case-insensitive, in any order):
 * `date, narration, amount, balance, reference, type`. `amount` positive =
 * credit, negative = debit; or `type` = CREDIT/DEBIT with positive amounts.
 * Dates must be ISO (`YYYY-MM-DD`). Bad rows are collected into
 * [StatementParseResult.errors] with 1-based row numbers.
 */
object StatementCsvParser {
    data class StatementParseResult(
        val transactions: List<BankTransaction>,
        val errors: List<String>,
    )

    fun parse(
        bankAccountId: String,
        raw: String,
    ): StatementParseResult {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return StatementParseResult(emptyList(), listOf("Statement is empty"))

        val header = lines.first().split(",").map { it.trim().lowercase() }
        val hasHeader = header.any { it in KNOWN_HEADERS }
        val columns =
            if (hasHeader) {
                header
            } else {
                listOf("date", "narration", "amount", "balance", "reference", "type")
            }
        val body = if (hasHeader) lines.drop(1) else lines

        val errors = mutableListOf<String>()
        val transactions = mutableListOf<BankTransaction>()

        body.forEachIndexed { index, line ->
            val cells = line.split(",").map { it.trim() }
            val rowNumber = index + (if (hasHeader) 2 else 1)

            fun cell(name: String): String = columns.indexOf(name).takeIf { it >= 0 && it < cells.size }?.let { cells[it] } ?: ""

            val date = cell("date")
            if (!ISO_DATE.matches(date)) {
                errors += "Row $rowNumber: date '$date' is not YYYY-MM-DD"
                return@forEachIndexed
            }
            val amountValue = cell("amount").toDoubleOrNull()
            if (amountValue == null) {
                errors += "Row $rowNumber: amount '${cell("amount")}' is not a number"
                return@forEachIndexed
            }
            val typeWord = cell("type").uppercase()
            val signed = if (typeWord == "DEBIT" && amountValue > 0) -amountValue else amountValue
            transactions +=
                BankTransaction(
                    date = date,
                    amount = Money.fromDouble(kotlin.math.abs(signed)),
                    type = if (signed < 0) "DEBIT" else "CREDIT",
                    narration = cell("narration"),
                    reference = cell("reference"),
                    balance = Money.fromDouble(cell("balance").toDoubleOrNull() ?: 0.0),
                )
        }
        return StatementParseResult(transactions, errors)
    }

    fun toStatement(
        bankAccountId: String,
        raw: String,
    ): Pair<BankStatement?, List<String>> {
        val result = parse(bankAccountId, raw)
        if (result.transactions.isEmpty()) return null to result.errors
        val sorted = result.transactions.sortedBy { it.date }
        val today =
            Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
                .toString()
        return BankStatement(
            bankAccountId = bankAccountId,
            statementDate = today,
            periodStart = sorted.first().date,
            periodEnd = sorted.last().date,
            openingBalance = Money.ZERO,
            closingBalance = sorted.last().balance,
            transactions = sorted,
            format = "CSV",
        ) to result.errors
    }

    private val ISO_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val KNOWN_HEADERS = setOf("date", "narration", "amount", "balance", "reference", "type")
}
