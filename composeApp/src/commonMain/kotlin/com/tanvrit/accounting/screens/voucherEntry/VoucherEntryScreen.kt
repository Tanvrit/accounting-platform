package com.tanvrit.accounting.screens.voucherEntry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.tanvrit.accounting.screens.common.ChipTone
import com.tanvrit.accounting.screens.common.DropdownPickerField
import com.tanvrit.accounting.screens.common.ErrorBanner
import com.tanvrit.accounting.screens.common.LoadingPane
import com.tanvrit.accounting.screens.common.MoneyText
import com.tanvrit.accounting.screens.common.ScreenHeader
import com.tanvrit.accounting.screens.common.StatusChip
import com.tanvrit.accounting.screens.keyboard.tanvritShortcutLayer
import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.VoucherType
import com.tanvrit.ui.component.lifecycle.rememberViewModel
import com.tanvrit.ui.theme.TanvritDesignSystem

private val ENTRY_TYPES =
    listOf(
        VoucherType.SALE,
        VoucherType.PURCHASE,
        VoucherType.RECEIPT,
        VoucherType.PAYMENT,
        VoucherType.JOURNAL,
        VoucherType.CONTRA,
    )

/**
 * Voucher Entry — single-screen, keyboard-first voucher composer for Sales,
 * Purchase, Receipt, Payment, Journal and Contra. The bottom bar shows the
 * running totals; Post stays disabled until debits equal credits non-zero.
 * Header actions open the template picker / save-as-template sheet (#10) and
 * the approval queue (#11); the Voucher # field previews the business'
 * client-local numbering series.
 */
@Composable
fun VoucherEntryScreen(initialVoucherType: String = "SALE") {
    val viewModel = rememberViewModel(initialVoucherType) { VoucherEntryViewModel(initialVoucherType) }
    val state by viewModel.state.collectAsState()
    val spacing = TanvritDesignSystem.spacing

    // Voucher-entry keyboard chords (desktop/web): the speed-entry layer.
    // CTRL+S saves a draft, CTRL+ENTER saves+posts, ALT+ENTER adds a leg.
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(spacing.lg)
                .tanvritShortcutLayer { chord ->
                    when (chord) {
                        "ALT+ENTER" -> {
                            viewModel.addLine()
                            true
                        }
                        "CTRL+S", "META+S" -> {
                            viewModel.submit(andPost = false)
                            true
                        }
                        "CTRL+ENTER", "META+ENTER" -> {
                            viewModel.submit(andPost = true)
                            true
                        }
                        else -> false
                    }
                },
    ) {
        ScreenHeader(
            title = "Voucher entry",
            subtitle = "Double-entry — debits must equal credits",
            actions = {
                OutlinedButton(onClick = viewModel::openApprovals) { Text("Approvals") }
                OutlinedButton(onClick = viewModel::openTemplatePicker) { Text("Templates") }
                OutlinedButton(onClick = viewModel::openSaveTemplate) { Text("Save as template") }
            },
        )

        state.error?.let {
            ErrorBanner(message = it, onRetry = { viewModel.clearError() })
            Spacer(Modifier.height(spacing.sm))
        }
        state.notice?.let {
            StatusChip(label = it, tone = if (state.lastOutcome == "OFFLINE") ChipTone.Warning else ChipTone.Success)
            Spacer(Modifier.height(spacing.sm))
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ENTRY_TYPES.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = state.voucherType == type,
                    onClick = { viewModel.setVoucherType(type) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ENTRY_TYPES.size),
                ) { Text(type.code, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(spacing.lg))

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedTextField(
                value = state.date,
                onValueChange = { v -> viewModel.updateHeader { it.copy(date = v) } },
                modifier = Modifier.weight(1f),
                label = { Text("Date (YYYY-MM-DD)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.voucherNumber,
                onValueChange = { v -> viewModel.setVoucherNumber(v) },
                modifier = Modifier.weight(1f),
                label = { Text("Voucher #") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.referenceId,
                onValueChange = { v -> viewModel.updateHeader { it.copy(referenceId = v) } },
                modifier = Modifier.weight(1f),
                label = { Text("Reference #") },
                singleLine = true,
            )
        }
        Spacer(Modifier.height(spacing.sm))
        OutlinedTextField(
            value = state.narration,
            onValueChange = { v -> viewModel.updateHeader { it.copy(narration = v) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Narration") },
        )
        Spacer(Modifier.height(spacing.lg))

        if (state.isLoading && state.accounts.isEmpty()) {
            LoadingPane(Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                items(state.lines, key = { it.localId }) { line ->
                    VoucherLineRow(
                        line = line,
                        accounts = state.accounts,
                        onChange = { transform -> viewModel.updateLine(line.localId, transform) },
                        onAccount = { account -> viewModel.setAccountOnLine(line.localId, account) },
                        onRemove = { viewModel.removeLine(line.localId) },
                    )
                }
                item {
                    OutlinedButton(onClick = { viewModel.addLine() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(spacing.xs))
                        Text("Add line")
                    }
                }
            }
        }

        Spacer(Modifier.height(spacing.md))
        TotalsBar(state = state)
        Spacer(Modifier.height(spacing.md))

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedButton(
                onClick = { viewModel.submit(andPost = false) },
                enabled = !state.isPosting,
                modifier = Modifier.weight(1f),
            ) { Text("Save draft") }
            Button(
                onClick = { viewModel.submit(andPost = true) },
                enabled = state.isBalanced && !state.isPosting && state.businessId.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors =
                    if (state.isBalanced) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
            ) { Text(if (state.isPosting) "Posting…" else "Post voucher") }
        }
    }

    if (state.showTemplatePicker) {
        VoucherTemplatePickerSheet(
            templates = state.templates,
            onApply = viewModel::applyTemplate,
            onDelete = { viewModel.deleteTemplate(it.id) },
            onDismiss = viewModel::closeTemplatePicker,
        )
    }
    if (state.showSaveTemplate) {
        SaveVoucherTemplateSheet(
            voucherTypeCode = state.voucherType.code,
            legCount = state.lines.count { it.accountId.isNotBlank() },
            onSave = viewModel::saveCurrentAsTemplate,
            onDismiss = viewModel::closeSaveTemplate,
        )
    }
    if (state.showApprovals) {
        VoucherApprovalsSheet(
            entries = state.approvals,
            noteDrafts = state.noteDrafts,
            isBusy = state.approvalBusy,
            onNoteChange = viewModel::setNoteDraft,
            onVerify = viewModel::verifyVoucher,
            onPost = viewModel::postVerifiedVoucher,
            onDismiss = viewModel::closeApprovals,
        )
    }
}

@Composable
private fun VoucherLineRow(
    line: VoucherLineDraft,
    accounts: List<Account>,
    onChange: ((VoucherLineDraft) -> VoucherLineDraft) -> Unit,
    onAccount: (Account) -> Unit,
    onRemove: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DropdownPickerField(
                label = "Account",
                options = accounts.sortedBy { it.accountCode },
                selected = accounts.firstOrNull { it.id == line.accountId },
                onSelected = onAccount,
                modifier = Modifier.weight(1f),
                optionLabel = { "${it.accountCode} — ${it.name}" },
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = "Remove line")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedTextField(
                value = line.debitText,
                onValueChange = { v -> onChange { it.copy(debitText = v, creditText = "") } },
                modifier = Modifier.weight(1f),
                label = { Text("Debit") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
            OutlinedTextField(
                value = line.creditText,
                onValueChange = { v -> onChange { it.copy(creditText = v, debitText = "") } },
                modifier = Modifier.weight(1f),
                label = { Text("Credit") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
        }
        OutlinedTextField(
            value = line.narration,
            onValueChange = { v -> onChange { it.copy(narration = v) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Line narration") },
            singleLine = true,
        )
    }
}

@Composable
private fun TotalsBar(state: VoucherEntryUiState) {
    val spacing = TanvritDesignSystem.spacing
    val scheme = MaterialTheme.colorScheme
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TanvritDesignSystem.shapes.medium,
        color = if (state.isBalanced) TanvritDesignSystem.colors.successContainer else scheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Debit total", style = MaterialTheme.typography.labelSmall)
                MoneyText(money = state.totalDebit, bold = true)
            }
            Column {
                Text("Credit total", style = MaterialTheme.typography.labelSmall)
                MoneyText(money = state.totalCredit, bold = true)
            }
            Column {
                Text("Difference", style = MaterialTheme.typography.labelSmall)
                MoneyText(
                    money = state.totalDebit - state.totalCredit,
                    bold = true,
                    colorizeSign = true,
                )
            }
            StatusChip(
                label = if (state.isBalanced) "Balanced" else "Unbalanced",
                tone = if (state.isBalanced) ChipTone.Success else ChipTone.Warning,
            )
        }
    }
}
