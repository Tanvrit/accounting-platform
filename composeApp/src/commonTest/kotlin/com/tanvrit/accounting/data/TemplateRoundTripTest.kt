package com.tanvrit.accounting.data

import com.tanvrit.core.network.AppJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Persistence round trips for [VoucherTemplate] / [VoucherTemplateLeg] through
 * `AppJson.json` — the exact codec `VoucherTemplateStore` writes/reads under
 * `voucherTemplates.<businessId>` in UserDefaults. The store itself is not
 * constructed here: it resolves UserDefaults via TanvritKoin at property init
 * (see AccountingAppModuleTest for why that is off-limits in commonTest).
 */
class TemplateRoundTripTest {
    @Test
    fun templateWithLegsRoundTrips() {
        val template =
            VoucherTemplate(
                id = "tpl-1",
                businessId = "biz-1",
                name = "Monthly rent",
                voucherType = "PAYMENT",
                legs =
                    listOf(
                        VoucherTemplateLeg(
                            accountId = "acc-rent",
                            accountCode = "5200",
                            accountName = "Rent expense",
                            debit = "45000.00",
                            narration = "April rent",
                        ),
                        VoucherTemplateLeg(
                            accountId = "acc-cash",
                            accountCode = "1000",
                            accountName = "Cash",
                            credit = "45000.00",
                        ),
                    ),
                createdAt = "2026-09-26T10:00:00",
            )
        val encoded = AppJson.json.encodeToString(template)
        assertEquals(template, AppJson.json.decodeFromString<VoucherTemplate>(encoded))
    }

    @Test
    fun optionalLegAmountsSurviveAsBlankStrings() {
        // A "skeleton" template: accounts + narrations, amounts left blank —
        // the editor must get blank debit/credit sides back, not "0".
        val template =
            VoucherTemplate(
                id = "tpl-2",
                businessId = "biz-1",
                name = "Salary JV skeleton",
                voucherType = "JOURNAL",
                legs = listOf(VoucherTemplateLeg(accountId = "acc-salary", narration = "Monthly salary")),
            )
        val decoded = AppJson.json.decodeFromString<VoucherTemplate>(AppJson.json.encodeToString(template))
        assertEquals(template, decoded)
        assertEquals("", decoded.legs.single().debit)
        assertEquals("", decoded.legs.single().credit)
    }

    @Test
    fun defaultFieldsAreOmittedFromTheStoredPayload() {
        // AppJson is encodeDefaults=false: blank/empty values emit no keys, so
        // the UserDefaults blob stays small; decode restores the defaults.
        val encoded =
            AppJson.json.encodeToString(
                VoucherTemplate(
                    id = "tpl-3",
                    name = "Cash sale",
                    voucherType = "SALE",
                    legs = listOf(VoucherTemplateLeg(accountId = "acc-cash")),
                ),
            )
        assertFalse(encoded.contains("\"narration\""), "default narration must be omitted: $encoded")
        assertFalse(encoded.contains("\"createdAt\""), "default createdAt must be omitted: $encoded")
    }

    @Test
    fun listPayloadRoundTrips() {
        // The store persists the whole per-business list as ONE JSON blob.
        val templates =
            listOf(
                VoucherTemplate(id = "a", businessId = "biz", name = "A", voucherType = "SALE"),
                VoucherTemplate(id = "b", businessId = "biz", name = "B", voucherType = "JOURNAL", legs = emptyList()),
            )
        val encoded = AppJson.json.encodeToString(templates)
        assertEquals(templates, AppJson.json.decodeFromString<List<VoucherTemplate>>(encoded))
    }

    @Test
    fun unknownKeysFromFutureVersionsAreIgnoredOnDecode() {
        val futureJson =
            """{"id":"tpl-9","businessId":"biz","name":"Rent","voucherType":"PAYMENT","legs":[],"serverField":42}"""
        val decoded = AppJson.json.decodeFromString<VoucherTemplate>(futureJson)
        assertEquals("tpl-9", decoded.id)
        assertEquals("Rent", decoded.name)
    }

    @Test
    fun workflowOverlayRoundTripsKeyedByVoucherId() {
        // VoucherWorkflowStore persists Map<voucherId, VoucherApproval> the same way.
        val approvals =
            mapOf(
                "v-1" to VoucherApproval(voucherId = "v-1", verified = true, verifiedBy = "user-9", note = "Checked bills"),
            )
        val encoded = AppJson.json.encodeToString(approvals)
        assertEquals(approvals, AppJson.json.decodeFromString<Map<String, VoucherApproval>>(encoded))
    }
}
