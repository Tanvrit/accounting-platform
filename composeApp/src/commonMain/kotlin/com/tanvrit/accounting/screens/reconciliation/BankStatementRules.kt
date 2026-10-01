package com.tanvrit.accounting.screens.reconciliation

import com.tanvrit.core.feature.accounting.model.BankTransaction
import kotlinx.serialization.Serializable

/** How a [BankRule] tests a statement narration. */
enum class BankRuleMatchType {
    CONTAINS,
    STARTS_WITH,
    EQUALS,

    /** Full-regex match, gated by [BankStatementRules]' nested-quantifier deny check. */
    REGEX_SAFE,
}

/**
 * One local "narration → suggested ledger account" rule (roadmap #16 lite).
 * Client-local only: persisted via `data/BankRulesStore` under
 * `bankRules.<businessId>`, never sent on the wire — so field names carry no
 * `@SerialName` wire contract.
 */
@Serializable
data class BankRule(
    val id: String = "",
    val matchType: BankRuleMatchType = BankRuleMatchType.CONTAINS,
    val pattern: String = "",
    val suggestedAccountId: String = "",
    val suggestedAccountCode: String = "",
    /** Lower wins; ties keep rule-list order ([applyRules] uses a stable sort). */
    val priority: Int = 0,
)

/**
 * Pure bank-statement rule engine (roadmap #16 lite — client-side only, no
 * server hits). Deterministic: rules are sorted by [BankRule.priority] with a
 * stable sort, and the first matching rule wins per transaction.
 */
object BankStatementRules {
    /** Regex patterns beyond this length are rejected outright. */
    const val MAX_PATTERN_LENGTH = 128

    /**
     * Conservative ReDoS deny-check: a quantified group whose body itself
     * carries a quantifier (`(a+)+`, `(\w{1,3})*`) — the classic
     * catastrophic-backtracking shape. Over-rejects a few safe patterns on
     * purpose; this is a "lite" engine, not a regex sandbox.
     */
    private val NESTED_QUANTIFIER = Regex("""\([^)]*[+*{][^)]*\)\s*[+*{]""")

    /** Null when [rule] is usable; a human-readable reject reason otherwise. */
    fun validate(rule: BankRule): String? {
        if (rule.pattern.isBlank()) return "Pattern is blank"
        if (rule.suggestedAccountId.isBlank()) return "Pick a suggested account"
        return when (rule.matchType) {
            BankRuleMatchType.CONTAINS, BankRuleMatchType.STARTS_WITH, BankRuleMatchType.EQUALS -> null
            BankRuleMatchType.REGEX_SAFE ->
                when {
                    rule.pattern.length > MAX_PATTERN_LENGTH -> "Pattern longer than $MAX_PATTERN_LENGTH chars"
                    NESTED_QUANTIFIER.containsMatchIn(rule.pattern) ->
                        "Nested quantified groups (e.g. (a+)+) are rejected — catastrophic backtracking risk"
                    else ->
                        runCatching { Regex(rule.pattern) }.fold(
                            onSuccess = { null },
                            onFailure = { "Invalid regex: ${it.message}" },
                        )
                }
        }
    }

    /** True when [rule] matches [narration]; invalid REGEX_SAFE patterns never match. */
    fun matches(
        rule: BankRule,
        narration: String,
    ): Boolean =
        when (rule.matchType) {
            BankRuleMatchType.CONTAINS -> narration.contains(rule.pattern, ignoreCase = true)
            BankRuleMatchType.STARTS_WITH -> narration.startsWith(rule.pattern, ignoreCase = true)
            BankRuleMatchType.EQUALS -> narration.equals(rule.pattern, ignoreCase = true)
            BankRuleMatchType.REGEX_SAFE ->
                runCatching { Regex(rule.pattern, RegexOption.IGNORE_CASE).containsMatchIn(narration) }
                    .getOrDefault(false)
        }

    /**
     * Applies [rules] against each transaction's narration (case-insensitive).
     * Invalid rules are skipped. Returns `(transactionIndex, suggestedAccountId)`
     * pairs in transaction order; each transaction yields at most one pair —
     * the first matching rule (by priority) wins.
     */
    fun applyRules(
        transactions: List<BankTransaction>,
        rules: List<BankRule>,
    ): List<Pair<Int, String>> {
        val usable = rules.filter { validate(it) == null }.sortedBy { it.priority }
        return transactions.mapIndexedNotNull { index, transaction ->
            val rule = usable.firstOrNull { matches(it, transaction.narration) } ?: return@mapIndexedNotNull null
            index to rule.suggestedAccountId
        }
    }
}
