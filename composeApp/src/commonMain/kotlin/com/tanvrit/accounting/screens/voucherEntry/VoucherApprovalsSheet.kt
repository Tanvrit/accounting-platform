package com.tanvrit.accounting.screens.voucherEntry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tanvrit.accounting.screens.common.ChipTone
import com.tanvrit.accounting.screens.common.MoneyText
import com.tanvrit.accounting.screens.common.StatusChip
import com.tanvrit.core.feature.money.Money
import com.tanvrit.ui.component.premium.GlassSheet
import com.tanvrit.ui.theme.TanvritDesignSystem

/**
 * Approval queue bottom sheet (feature #11): vouchers whose effective stage is
 * DRAFT ("Awaiting verification") or VERIFIED ("Ready to post"). Row actions
 * follow [VoucherWorkflow]: Verify is offered only on drafts (a free-text
 * approver note can ride along, stored client-side), Post only on verified.
 * Rendered from `VoucherEntryScreen`'s header "Approvals" action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherApprovalsSheet(
    entries: List<ApprovalEntry>,
    noteDrafts: Map<String, String>,
    isBusy: Boolean,
    onNoteChange: (voucherId: String, note: String) -> Unit,
    onVerify: (String) -> Unit,
    onPost: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    val awaitingVerification = entries.filter { it.stage == VoucherWorkflowStage.DRAFT }
    val readyToPost = entries.filter { it.stage == VoucherWorkflowStage.VERIFIED }

    GlassSheet(onDismiss = onDismiss) {
        Text("Voucher approvals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Draft → verified → posted. Verification is tracked on this device until the server workflow ships.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.lg))

        if (entries.isEmpty()) {
            Text(
                "Nothing awaiting approval.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = spacing.massive * MAX_VISIBLE_ROWS)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                if (readyToPost.isNotEmpty()) {
                    SectionLabel("Ready to post")
                    readyToPost.forEach { entry ->
                        ApprovalRow(
                            entry = entry,
                            noteDraft = "",
                            isBusy = isBusy,
                            onNoteChange = onNoteChange,
                            onVerify = onVerify,
                            onPost = onPost,
                        )
                    }
                }
                if (awaitingVerification.isNotEmpty()) {
                    SectionLabel("Awaiting verification")
                    awaitingVerification.forEach { entry ->
                        ApprovalRow(
                            entry = entry,
                            noteDraft = noteDrafts[entry.voucher.id].orEmpty(),
                            isBusy = isBusy,
                            onNoteChange = onNoteChange,
                            onVerify = onVerify,
                            onPost = onPost,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ApprovalRow(
    entry: ApprovalEntry,
    noteDraft: String,
    isBusy: Boolean,
    onNoteChange: (voucherId: String, note: String) -> Unit,
    onVerify: (String) -> Unit,
    onPost: (String) -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    val voucher = entry.voucher
    val totalDebit = voucher.lineItems.fold(Money.ZERO) { acc, line -> acc + line.debit }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TanvritDesignSystem.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = voucher.voucherNumber.ifBlank { voucher.id },
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    StatusChip(label = voucher.voucherType.code, tone = ChipTone.Info)
                    StatusChip(
                        label = if (entry.stage == VoucherWorkflowStage.VERIFIED) "VERIFIED (local)" else entry.stage.name,
                        tone = if (entry.stage == VoucherWorkflowStage.VERIFIED) ChipTone.Primary else ChipTone.Neutral,
                    )
                }
            }
            Text(
                text =
                    listOf(voucher.date, voucher.narration)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total debit", style = MaterialTheme.typography.labelSmall)
                MoneyText(money = totalDebit, bold = true)
            }
            if (entry.approverNote.isNotBlank()) {
                Text(
                    text = "Approver note: ${entry.approverNote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (entry.stage) {
                VoucherWorkflowStage.DRAFT -> {
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { onNoteChange(voucher.id, it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Approver note (optional)") },
                        singleLine = true,
                        enabled = !isBusy,
                    )
                    OutlinedButton(
                        onClick = { onVerify(voucher.id) },
                        enabled = !isBusy && VoucherWorkflow.allowed(entry.stage, VoucherWorkflowStage.VERIFIED),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Verify") }
                }
                VoucherWorkflowStage.VERIFIED -> {
                    Button(
                        onClick = { onPost(voucher.id) },
                        enabled = !isBusy && VoucherWorkflow.allowed(entry.stage, VoucherWorkflowStage.POSTED),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (isBusy) "Posting…" else "Post voucher") }
                }
                else -> Unit
            }
        }
    }
}

private const val MAX_VISIBLE_ROWS = 6
