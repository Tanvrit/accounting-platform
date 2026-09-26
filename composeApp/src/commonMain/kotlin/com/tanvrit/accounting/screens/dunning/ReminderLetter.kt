package com.tanvrit.accounting.screens.dunning

import com.tanvrit.accounting.screens.common.formatMoney

/** Dunning tone — the escalation step of the letter. */
enum class ReminderTone(
    val code: String,
    val label: String,
) {
    /** First nudge — assumes an oversight, no pressure. */
    CONSERVATIVE("CONSERVATIVE", "First nudge"),

    /** Middle step — states the overdue fact plainly. */
    REMINDER("REMINDER", "Payment reminder"),

    /** Last step before escalation — names a deadline and consequences. */
    FINAL("FINAL", "Final notice"),
}

/** Everything the letter needs — deliberately typed, so nothing else can leak into it. */
data class LetterInput(
    val businessName: String,
    /** Business GSTIN for the letterhead; blank = line omitted. */
    val businessGstin: String = "",
    /** "As of" date — the same reference date the aging ran at, ISO yyyy-mm-dd. */
    val asOfDate: String,
    val party: PartyAging,
    val currencyCode: String = "INR",
)

/** One tone's constant text — placeholders resolved by [ReminderLetter.generate]. */
private data class LetterTemplate(
    val subject: String,
    val opening: String,
    val demand: String,
    val closing: String,
)

/**
 * Pure reminder-letter generator — no I/O, no PII beyond the explicitly typed
 * [LetterInput] fields (party name/code/GSTIN come from the account; internal
 * ids and free-form dimension keys never enter the text). Covered directly by
 * `commonTest/.../screens/dunning/ReminderLetterTest.kt`.
 *
 * Templates are EN, fixed per tone, and deliberately plain-text: the send path
 * is copy-to-clipboard / `mailto:`, so the output must survive being pasted
 * into any mail or chat client.
 */
object ReminderLetter {
    /** Constant template catalog keyed by tone — the "sealed" set of 3 voices. */
    private val TEMPLATES: Map<ReminderTone, LetterTemplate> =
        mapOf(
            ReminderTone.CONSERVATIVE to
                LetterTemplate(
                    subject = "Gentle reminder: {TOTAL} outstanding — {BUSINESS_NAME}",
                    opening =
                        "We hope this note finds you well. While reconciling our accounts we noticed " +
                            "the following invoice(s) from {BUSINESS_NAME} appear unpaid. This may simply " +
                            "be an oversight or a payment still in transit.",
                    demand =
                        "If payment has already been made, please disregard this note and accept our " +
                            "thanks. Otherwise we would be grateful if you could arrange payment of " +
                            "{TOTAL} at your earliest convenience.",
                    closing = "Thank you for your continued business.",
                ),
            ReminderTone.REMINDER to
                LetterTemplate(
                    subject = "Payment reminder: {TOTAL} overdue — {BUSINESS_NAME}",
                    opening =
                        "Our records show the following invoice(s) issued by {BUSINESS_NAME} remain " +
                            "unpaid and are now past their due dates. The oldest outstanding amount is " +
                            "{MAX_DAYS} days overdue.",
                    demand =
                        "Kindly arrange payment of the total outstanding amount of {TOTAL} within 7 days " +
                            "of this notice. If any invoice is disputed, please write back so we can " +
                            "resolve it promptly.",
                    closing = "We value our association and look forward to your prompt response.",
                ),
            ReminderTone.FINAL to
                LetterTemplate(
                    subject = "Final notice before escalation: {TOTAL} overdue — {BUSINESS_NAME}",
                    opening =
                        "Despite previous reminders, the following invoice(s) issued by {BUSINESS_NAME} " +
                            "remain unpaid. The oldest outstanding amount is {MAX_DAYS} days past its " +
                            "due date. This notice serves as a final request prior to escalation.",
                    demand =
                        "Please remit the total outstanding amount of {TOTAL} within 3 working days of " +
                            "receipt of this notice. Failing this, we may be constrained to refer the " +
                            "matter for recovery proceedings and to suspend further credit, without " +
                            "further notice.",
                    closing = "We sincerely hope escalation will not be necessary.",
                ),
        )

    /** Renders the full letter for [input] in [tone]. */
    fun generate(
        input: LetterInput,
        tone: ReminderTone,
    ): String {
        val template = TEMPLATES.getValue(tone)
        val tokens = tokensOf(input)
        val body =
            buildString {
                // Letterhead.
                appendLine(input.businessName.ifBlank { "—" })
                if (input.businessGstin.isNotBlank()) appendLine("GSTIN: ${input.businessGstin}")
                appendLine("Date: ${input.asOfDate.ifBlank { "—" }}")
                appendLine()
                // Addressee — only typed party fields, never ids or raw maps.
                appendLine("To,")
                appendLine(input.party.name.ifBlank { "—" })
                if (input.party.accountCode.isNotBlank()) appendLine("Account code: ${input.party.accountCode}")
                if (input.party.gstin.isNotBlank()) appendLine("GSTIN: ${input.party.gstin}")
                appendLine()
                appendLine("Subject: ${resolve(template.subject, tokens)}")
                appendLine()
                appendLine("Dear Sir or Madam,")
                appendLine()
                appendLine(resolve(template.opening, tokens))
                appendLine()
                appendLine("Outstanding invoices as of ${input.asOfDate.ifBlank { "—" }}:")
                appendLine()
                append(invoiceTable(input))
                appendLine()
                appendLine(
                    "Total outstanding: ${formatMoney(input.party.total, input.currencyCode)} " +
                        "across ${input.party.invoiceCount} invoice(s).",
                )
                appendLine()
                appendLine(resolve(template.demand, tokens))
                appendLine()
                appendLine(resolve(template.closing, tokens))
                appendLine()
                appendLine("Yours faithfully,")
                append("For ${input.businessName.ifBlank { "—" }}")
            }
        return body
    }

    /** mailto: deep link — recipient prefilled only when the party account carries an e-mail. */
    fun mailtoFor(
        input: LetterInput,
        tone: ReminderTone,
    ): String {
        val template = TEMPLATES.getValue(tone)
        val subject = resolve(template.subject, tokensOf(input))
        val recipient = input.party.email.trim()
        val to = if (recipient.isEmpty()) "" else encodeQueryParam(recipient, extraAllowed = "@.+-_")
        return "mailto:$to?subject=${encodeQueryParam(subject)}&body=${encodeQueryParam(generate(input, tone))}"
    }

    /** Subject line for the sheet header / previews, resolved for [input]. */
    fun subjectFor(
        input: LetterInput,
        tone: ReminderTone,
    ): String = resolve(TEMPLATES.getValue(tone).subject, tokensOf(input))

    private fun tokensOf(input: LetterInput): Map<String, String> =
        mapOf(
            "{BUSINESS_NAME}" to input.businessName.ifBlank { "—" },
            "{BUSINESS_GSTIN}" to input.businessGstin,
            "{PARTY_NAME}" to input.party.name.ifBlank { "—" },
            "{TOTAL}" to formatMoney(input.party.total, input.currencyCode),
            "{AS_OF}" to input.asOfDate,
            "{MAX_DAYS}" to input.party.maxDaysOverdue.toString(),
        )

    /** Deterministic placeholder resolution — unknown tokens are left verbatim, not dropped. */
    internal fun resolve(
        template: String,
        tokens: Map<String, String>,
    ): String = tokens.entries.fold(template) { acc, (token, value) -> acc.replace(token, value) }

    /** Numbered plain-text invoice table — monospace-safe fixed columns. */
    private fun invoiceTable(input: LetterInput): String =
        buildString {
            input.party.invoices.forEachIndexed { index, invoice ->
                val number = (invoice.voucherNumber.ifBlank { invoice.voucherId }).padEnd(14).take(14)
                val row =
                    (index + 1).toString().padStart(2) + ". " +
                        number + " " +
                        invoice.date.take(10).padEnd(10) + "  due " +
                        (invoice.dueDate.ifBlank { "—" }).take(10).padEnd(10) + "  " +
                        (invoice.daysOverdue.toString() + "d overdue").padStart(11) + "  " +
                        formatMoney(invoice.amount, input.currencyCode).padStart(14)
                appendLine(row.trimEnd())
            }
        }

    /**
     * RFC 3986 query-component percent-encoding without java.net (KMP-pure):
     * UTF-8 bytes, unreserved characters (plus [extraAllowed]) kept verbatim.
     */
    internal fun encodeQueryParam(
        raw: String,
        extraAllowed: String = "",
    ): String {
        val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~" + extraAllowed
        val hex = "0123456789ABCDEF"
        val out = StringBuilder()
        for (byte in raw.encodeToByteArray()) {
            val value = byte.toInt() and 0xFF
            val char = value.toChar()
            if (char in unreserved) {
                out.append(char)
            } else {
                out.append('%').append(hex[value shr 4]).append(hex[value and 0x0F])
            }
        }
        return out.toString()
    }
}
