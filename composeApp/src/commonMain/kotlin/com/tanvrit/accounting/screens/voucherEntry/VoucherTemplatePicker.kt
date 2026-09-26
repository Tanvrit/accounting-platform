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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tanvrit.accounting.data.VoucherTemplate
import com.tanvrit.accounting.data.VoucherTemplateLeg
import com.tanvrit.accounting.screens.common.ChipTone
import com.tanvrit.accounting.screens.common.ConfirmDialog
import com.tanvrit.accounting.screens.common.StatusChip
import com.tanvrit.ui.component.premium.GlassSheet
import com.tanvrit.ui.theme.TanvritDesignSystem

/**
 * Template picker bottom sheet (feature #10): every template saved for this
 * business, with per-row Apply (prefills the composer legs) and Delete
 * (confirmed). Rendered from `VoucherEntryScreen`'s header "Templates" action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherTemplatePickerSheet(
    templates: List<VoucherTemplate>,
    onApply: (VoucherTemplate) -> Unit,
    onDelete: (VoucherTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    var pendingDelete by remember { mutableStateOf<VoucherTemplate?>(null) }

    GlassSheet(onDismiss = onDismiss) {
        Text("Voucher templates", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Apply a saved set of legs to this voucher.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.lg))

        if (templates.isEmpty()) {
            Text(
                "No templates yet — compose a voucher and use “Save as template”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = spacing.massive * MAX_ROWS)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                templates.forEach { template ->
                    TemplateRow(
                        template = template,
                        onApply = { onApply(template) },
                        onDelete = { pendingDelete = template },
                    )
                }
            }
        }
    }

    pendingDelete?.let { template ->
        ConfirmDialog(
            title = "Delete template?",
            message = "“${template.name}” (${template.legs.size} legs) will be removed for this business.",
            confirmLabel = "Delete",
            onConfirm = {
                onDelete(template)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
            destructive = true,
        )
    }
}

@Composable
private fun TemplateRow(
    template: VoucherTemplate,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
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
                Text(template.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                StatusChip(label = template.voucherType.ifBlank { "?" }, tone = ChipTone.Info)
            }
            template.legs.forEach { leg ->
                Text(
                    text = legSummary(leg),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete template")
                }
                Button(onClick = onApply) { Text("Apply") }
            }
        }
    }
}

private fun legSummary(leg: VoucherTemplateLeg): String {
    val account = listOf(leg.accountCode, leg.accountName).filter { it.isNotBlank() }.joinToString(" — ")
    val side =
        when {
            leg.debit.isNotBlank() -> "Dr ${leg.debit}"
            leg.credit.isNotBlank() -> "Cr ${leg.credit}"
            else -> ""
        }
    return listOf(account.ifBlank { "Account" }, side, leg.narration)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

/**
 * "Save as template" sheet: name + a summary of what will be captured. The
 * caller (`VoucherEntryViewModel.saveCurrentAsTemplate`) does the leg capture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveVoucherTemplateSheet(
    voucherTypeCode: String,
    legCount: Int,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    var name by remember { mutableStateOf("") }

    GlassSheet(onDismiss = onDismiss) {
        Text("Save as template", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Captures the $legCount account legs of this $voucherTypeCode voucher — accounts, narrations and amounts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.lg))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Template name") },
            placeholder = { Text("e.g. Monthly rent") },
            singleLine = true,
        )
        Spacer(Modifier.height(spacing.lg))
        Button(
            onClick = { onSave(name) },
            enabled = name.isNotBlank() && legCount > 0,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save template") }
    }
}

private const val MAX_ROWS = 8
