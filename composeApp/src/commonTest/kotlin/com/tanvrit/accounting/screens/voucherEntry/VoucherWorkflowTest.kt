package com.tanvrit.accounting.screens.voucherEntry

import com.tanvrit.core.feature.accounting.model.VoucherStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Approval transition table (#11). SDK 3.0.7 `VoucherStatus` has entries
 * DRAFT / POSTED / CANCELLED / REVERSED only — VERIFIED is a client-local
 * overlay (see [VoucherWorkflow]'s KDoc) — so the ladder DRAFT → VERIFIED →
 * POSTED is enforced here and mirrored by the Verify/Post buttons in the
 * approvals sheet. Every other pair must be rejected.
 */
class VoucherWorkflowTest {
    @Test
    fun draftCanBeVerifiedAndVerifiedCanBePosted() {
        assertTrue(VoucherWorkflow.allowed(VoucherWorkflowStage.DRAFT, VoucherWorkflowStage.VERIFIED))
        assertTrue(VoucherWorkflow.allowed(VoucherWorkflowStage.VERIFIED, VoucherWorkflowStage.POSTED))
    }

    @Test
    fun draftCannotSkipVerification() {
        assertFalse(VoucherWorkflow.allowed(VoucherWorkflowStage.DRAFT, VoucherWorkflowStage.POSTED))
    }

    @Test
    fun verifiedCannotDropBackToDraft() {
        assertFalse(VoucherWorkflow.allowed(VoucherWorkflowStage.VERIFIED, VoucherWorkflowStage.DRAFT))
    }

    @Test
    fun transitionTableRejectsEverythingExceptTheTwoLadderSteps() {
        VoucherWorkflowStage.entries.forEach { from ->
            VoucherWorkflowStage.entries.forEach { to ->
                val expected =
                    (from == VoucherWorkflowStage.DRAFT && to == VoucherWorkflowStage.VERIFIED) ||
                        (from == VoucherWorkflowStage.VERIFIED && to == VoucherWorkflowStage.POSTED)
                assertEquals(expected, VoucherWorkflow.allowed(from, to), "allowed($from → $to)")
            }
        }
    }

    @Test
    fun noStageTransitionsToItself() {
        VoucherWorkflowStage.entries.forEach { stage ->
            assertFalse(VoucherWorkflow.allowed(stage, stage), "self-transition $stage")
        }
    }

    @Test
    fun draftWithVerificationOverlayIsVerified() {
        assertEquals(VoucherWorkflowStage.DRAFT, VoucherWorkflow.stageOf(VoucherStatus.DRAFT, verified = false))
        assertEquals(VoucherWorkflowStage.VERIFIED, VoucherWorkflow.stageOf(VoucherStatus.DRAFT, verified = true))
    }

    @Test
    fun wireStatusAlwaysWinsOverTheOverlay() {
        // A posted voucher whose local record lingers must still present POSTED.
        assertEquals(VoucherWorkflowStage.POSTED, VoucherWorkflow.stageOf(VoucherStatus.POSTED, verified = true))
        assertEquals(VoucherWorkflowStage.POSTED, VoucherWorkflow.stageOf(VoucherStatus.POSTED, verified = false))
    }

    @Test
    fun terminalWireStatusesMapThroughUnchanged() {
        assertEquals(VoucherWorkflowStage.CANCELLED, VoucherWorkflow.stageOf(VoucherStatus.CANCELLED, verified = false))
        assertEquals(VoucherWorkflowStage.REVERSED, VoucherWorkflow.stageOf(VoucherStatus.REVERSED, verified = true))
    }

    @Test
    fun onlyDraftAndVerifiedAreActionable() {
        assertTrue(VoucherWorkflow.isActionable(VoucherWorkflowStage.DRAFT))
        assertTrue(VoucherWorkflow.isActionable(VoucherWorkflowStage.VERIFIED))
        assertFalse(VoucherWorkflow.isActionable(VoucherWorkflowStage.POSTED))
        assertFalse(VoucherWorkflow.isActionable(VoucherWorkflowStage.CANCELLED))
        assertFalse(VoucherWorkflow.isActionable(VoucherWorkflowStage.REVERSED))
    }
}
