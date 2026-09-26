package com.tanvrit.accounting.screens.voucherEntry

import com.tanvrit.core.feature.accounting.model.VoucherStatus

/**
 * The client-side approval ladder for vouchers (feature #11).
 *
 * SDK 3.0.7 `VoucherStatus` carries only DRAFT / POSTED / CANCELLED / REVERSED
 * — there is NO VERIFIED on the wire. [VoucherWorkflowStage.VERIFIED] is
 * therefore an overlay: a voucher whose wire status is DRAFT plus a local
 * verification record (see `VoucherWorkflowStore`) presents as VERIFIED.
 * POSTED, CANCELLED and REVERSED map 1:1 onto the wire status and always win
 * over the overlay.
 */
enum class VoucherWorkflowStage { DRAFT, VERIFIED, POSTED, CANCELLED, REVERSED }

/**
 * Pure transition rules — no Koin, no state — so the table is fully
 * commonTest-able. The ladder is deliberately strict: a draft cannot be
 * posted without verification through this UI, and nothing leaves POSTED.
 */
object VoucherWorkflow {
    private val allowedTransitions: Map<VoucherWorkflowStage, Set<VoucherWorkflowStage>> =
        mapOf(
            VoucherWorkflowStage.DRAFT to setOf(VoucherWorkflowStage.VERIFIED),
            VoucherWorkflowStage.VERIFIED to setOf(VoucherWorkflowStage.POSTED),
            VoucherWorkflowStage.POSTED to emptySet(),
            VoucherWorkflowStage.CANCELLED to emptySet(),
            VoucherWorkflowStage.REVERSED to emptySet(),
        )

    /** True only for DRAFT→VERIFIED and VERIFIED→POSTED; every other move is rejected. */
    fun allowed(
        from: VoucherWorkflowStage,
        to: VoucherWorkflowStage,
    ): Boolean = allowedTransitions[from]?.contains(to) == true

    /** Effective stage of a voucher: wire status, lifted by the local verification overlay. */
    fun stageOf(
        status: VoucherStatus,
        verified: Boolean = false,
    ): VoucherWorkflowStage =
        when (status) {
            VoucherStatus.DRAFT -> if (verified) VoucherWorkflowStage.VERIFIED else VoucherWorkflowStage.DRAFT
            VoucherStatus.POSTED -> VoucherWorkflowStage.POSTED
            VoucherStatus.CANCELLED -> VoucherWorkflowStage.CANCELLED
            VoucherStatus.REVERSED -> VoucherWorkflowStage.REVERSED
        }

    /** True when the stage still participates in the approval queue (not terminal). */
    fun isActionable(stage: VoucherWorkflowStage): Boolean = allowedTransitions[stage]?.isNotEmpty() == true
}
