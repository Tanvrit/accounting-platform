package com.tanvrit.accounting.screens.reconciliation

import com.tanvrit.core.feature.accounting.model.BankTransaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * BankStatementRules engine pins (roadmap #16 lite). The engine is a pure
 * object taking only in-memory inputs — there is no network surface to hit,
 * and these tests deliberately construct accounts/rules by hand rather than
 * booting Koin.
 */
class BankRulesTest {
    private fun tx(vararg narrations: String): List<BankTransaction> = narrations.map { BankTransaction(narration = it) }

    @Test
    fun containsStartsWithAndEqualsMatchIgnoringCase() {
        val rules =
            listOf(
                BankRule(id = "contains", matchType = BankRuleMatchType.CONTAINS, pattern = "rent", suggestedAccountId = "acc-rent"),
                BankRule(id = "starts", matchType = BankRuleMatchType.STARTS_WITH, pattern = "neft", suggestedAccountId = "acc-neft"),
                BankRule(id = "equals", matchType = BankRuleMatchType.EQUALS, pattern = "upi sales", suggestedAccountId = "acc-upi"),
            )
        val matches =
            BankStatementRules.applyRules(
                tx("Monthly RENT April", "NEFT 12345 transfer", "UPI SALES", "cash deposit"),
                rules,
            )
        assertEquals(
            listOf(0 to "acc-rent", 1 to "acc-neft", 2 to "acc-upi"),
            matches,
        )
    }

    @Test
    fun lowerPriorityWinsAndFirstMatchStopsTheSearch() {
        val rules =
            listOf(
                BankRule(
                    id = "broad",
                    matchType = BankRuleMatchType.CONTAINS,
                    pattern = "rent",
                    suggestedAccountId = "acc-broad",
                    priority = 50,
                ),
                BankRule(
                    id = "specific",
                    matchType = BankRuleMatchType.CONTAINS,
                    pattern = "office rent",
                    suggestedAccountId = "acc-specific",
                    priority = 10,
                ),
            )
        val matches = BankStatementRules.applyRules(tx("Office rent April"), rules)
        assertEquals(listOf(0 to "acc-specific"), matches)
    }

    @Test
    fun equalPrioritiesKeepRuleListOrder() {
        val rules =
            listOf(
                BankRule(
                    id = "first",
                    matchType = BankRuleMatchType.CONTAINS,
                    pattern = "rent",
                    suggestedAccountId = "acc-first",
                    priority = 10,
                ),
                BankRule(
                    id = "second",
                    matchType = BankRuleMatchType.CONTAINS,
                    pattern = "rent",
                    suggestedAccountId = "acc-second",
                    priority = 10,
                ),
            )
        val matches = BankStatementRules.applyRules(tx("Rent April"), rules)
        assertEquals(listOf(0 to "acc-first"), matches)
    }

    @Test
    fun unmatchedTransactionsYieldNoSuggestion() {
        val rules =
            listOf(
                BankRule(id = "rent", matchType = BankRuleMatchType.CONTAINS, pattern = "rent", suggestedAccountId = "acc-rent"),
            )
        val matches = BankStatementRules.applyRules(tx("Salary payout", "AWS bill"), rules)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun nestedQuantifierRegexIsRejectedAndSkipped() {
        val bad =
            BankRule(
                id = "bad",
                matchType = BankRuleMatchType.REGEX_SAFE,
                pattern = "(a+)+",
                suggestedAccountId = "acc-x",
            )
        assertNotNull(BankStatementRules.validate(bad))
        // A "redos-shaped" rule must never run — it is skipped, not executed.
        assertTrue(BankStatementRules.applyRules(tx("aaaa"), listOf(bad)).isEmpty())
    }

    @Test
    fun invalidRegexSyntaxIsRejected() {
        val bad =
            BankRule(
                id = "bad-syntax",
                matchType = BankRuleMatchType.REGEX_SAFE,
                pattern = "[unclosed",
                suggestedAccountId = "acc-x",
            )
        assertNotNull(BankStatementRules.validate(bad))
    }

    @Test
    fun safeRegexMatchesAndOverlongPatternsAreRejected() {
        val good =
            BankRule(
                id = "ok",
                matchType = BankRuleMatchType.REGEX_SAFE,
                pattern = "neft\\s*\\d+",
                suggestedAccountId = "acc-neft",
            )
        assertNull(BankStatementRules.validate(good))
        assertEquals(
            listOf(0 to "acc-neft"),
            BankStatementRules.applyRules(tx("NEFT 88991 CREDIT"), listOf(good)),
        )
        val tooLong = good.copy(pattern = "a".repeat(BankStatementRules.MAX_PATTERN_LENGTH + 1))
        assertNotNull(BankStatementRules.validate(tooLong))
    }

    @Test
    fun blankPatternAndBlankAccountAreRejected() {
        assertNotNull(
            BankStatementRules.validate(BankRule(matchType = BankRuleMatchType.CONTAINS, pattern = "  ", suggestedAccountId = "acc")),
        )
        assertNotNull(
            BankStatementRules.validate(BankRule(matchType = BankRuleMatchType.CONTAINS, pattern = "rent", suggestedAccountId = "")),
        )
    }
}
