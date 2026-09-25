package com.tanvrit.accounting.screens.gstCenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tanvrit.accounting.screens.common.ChipTone
import com.tanvrit.accounting.screens.common.DropdownPickerField
import com.tanvrit.accounting.screens.common.ErrorBanner
import com.tanvrit.accounting.screens.common.LoadingPane
import com.tanvrit.accounting.screens.common.ScreenHeader
import com.tanvrit.accounting.screens.common.StatusChip
import com.tanvrit.core.feature.accounting.model.FiscalPeriod
import com.tanvrit.core.feature.accounting.model.Gstr1Return
import com.tanvrit.core.feature.accounting.model.Gstr3bReturn
import com.tanvrit.ui.component.lifecycle.rememberViewModel
import com.tanvrit.ui.component.premium.PremiumEmptyState
import com.tanvrit.ui.component.premium.PremiumListRow
import com.tanvrit.ui.theme.TanvritDesignSystem

/** GST Center — returns, e-invoice, e-way bill, pre-filing health. */
@Composable
fun GstCenterScreen() {
    val viewModel = rememberViewModel { GstCenterViewModel() }
    val state by viewModel.state.collectAsState()
    val spacing = TanvritDesignSystem.spacing

    Column(modifier = Modifier.fillMaxSize().padding(spacing.lg)) {
        ScreenHeader(
            title = "GST Center",
            subtitle = "GSTR-1 · GSTR-3B · e-invoice · e-way bill",
        )

        state.error?.let {
            ErrorBanner(message = it, onRetry = { viewModel.clearMessages() })
            Spacer(Modifier.height(spacing.sm))
        }
        state.notice?.let {
            StatusChip(label = it, tone = ChipTone.Info)
            Spacer(Modifier.height(spacing.sm))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedTextField(
                value = state.gstin,
                onValueChange = { viewModel.setGstin(it) },
                modifier = Modifier.weight(1f),
                label = { Text("GSTIN") },
                singleLine = true,
            )
            DropdownPickerField(
                label = "Period",
                options = state.periods,
                selected = state.periods.firstOrNull { it.id == state.selectedPeriodId },
                onSelected = { period: FiscalPeriod -> viewModel.selectPeriod(period.id) },
                modifier = Modifier.weight(1f),
                optionLabel = { "${it.name} (${it.startDate} → ${it.endDate})" },
            )
        }
        Spacer(Modifier.height(spacing.md))

        TabRow(selectedTabIndex = state.activeTab.ordinal) {
            GstTab.entries.forEach { tab ->
                Tab(
                    selected = state.activeTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = { Text(tab.label) },
                )
            }
        }
        Spacer(Modifier.height(spacing.lg))

        if (state.isLoading) {
            LoadingPane(Modifier.weight(1f))
            return@Column
        }

        when (state.activeTab) {
            GstTab.RETURNS -> ReturnsTab(state, viewModel)
            GstTab.EINVOICE -> EinvoiceTab(state, viewModel)
            GstTab.EWAY_BILL -> EwayBillTab(state, viewModel)
            GstTab.HEALTH -> HealthTab(state)
        }
    }
}

@Composable
private fun ReturnsTab(
    state: GstCenterUiState,
    viewModel: GstCenterViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        item {
            ReturnCard(
                title = "GSTR-1",
                subtitle = "Outward supplies — B2B, B2C, exports, credit/debit notes, HSN summary",
                status = state.gstr1?.status,
                actionLabel = if (state.gstr1 == null) "Generate" else "Regenerate",
                onAction = { viewModel.generateGstr1(validateOnly = false) },
                secondaryLabel = "Validate only",
                onSecondary = { viewModel.generateGstr1(validateOnly = true) },
            )
        }
        state.gstr1?.let { gstr1 -> item { Gstr1Preview(gstr1) } }
        if (state.gstr1ValidationErrors.isNotEmpty()) {
            item {
                Text("Validation issues", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            items(state.gstr1ValidationErrors) { issue ->
                PremiumListRow(
                    title = issue,
                    isStandalone = false,
                    leading = { StatusChip(label = "Check", tone = ChipTone.Warning) },
                )
            }
        }
        item {
            ReturnCard(
                title = "GSTR-3B",
                subtitle = "Summary return — auto-computed from outward/inward supplies",
                status = state.gstr3b?.status,
                actionLabel = "Auto-compute",
                onAction = { viewModel.generateGstr3b() },
            )
        }
        state.gstr3b?.let { gstr3b -> item { Gstr3bPreview(gstr3b) } }
    }
}

@Composable
private fun ReturnCard(
    title: String,
    subtitle: String,
    status: String?,
    actionLabel: String,
    onAction: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val spacing = TanvritDesignSystem.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TanvritDesignSystem.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = TanvritDesignSystem.elevation.e1,
    ) {
        Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusChip(
                    label = status ?: "NOT STARTED",
                    tone =
                        when (status) {
                            "FILED" -> ChipTone.Success
                            "DRAFT", null -> ChipTone.Neutral
                            "ERROR" -> ChipTone.Error
                            else -> ChipTone.Warning
                        },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Button(onClick = onAction) { Text(actionLabel) }
                if (secondaryLabel != null && onSecondary != null) {
                    OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
                }
            }
        }
    }
}

@Composable
private fun Gstr1Preview(gstr1: Gstr1Return) {
    val spacing = TanvritDesignSystem.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TanvritDesignSystem.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text("Section preview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "B2B invoices: ${gstr1.sections.b2b.size} · B2C large: ${gstr1.sections.b2cl.size} · " +
                    "B2C small: ${gstr1.sections.b2cs.size} · Exports: ${gstr1.sections.export.size}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Credit/debit notes: ${gstr1.sections.cdnr.size} · HSN lines: ${gstr1.sections.hsnSummary.size}",
                style = MaterialTheme.typography.bodySmall,
            )
            gstr1.acknowledgmentNumber?.let {
                Text("Acknowledgment: $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Gstr3bPreview(gstr3b: Gstr3bReturn) {
    val spacing = TanvritDesignSystem.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TanvritDesignSystem.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text("3B summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Outward taxable: ${gstr3b.outwardSupplies.taxable} · IGST ${gstr3b.outwardSupplies.igst} · " +
                    "CGST ${gstr3b.outwardSupplies.cgst} · SGST ${gstr3b.outwardSupplies.sgst}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "ITC: inputs ${gstr3b.itcEligibility.inputs} + services ${gstr3b.itcEligibility.inputServices} " +
                    "− reversal ${gstr3b.itcEligibility.reversal} = net ${gstr3b.itcEligibility.netItc}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EinvoiceTab(
    state: GstCenterUiState,
    viewModel: GstCenterViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        Text(
            "Generate an IRN for a posted sales voucher via the e-invoice portal integration.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = state.einvoiceVoucherId,
            onValueChange = { viewModel.setEinvoiceVoucherId(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Voucher ID") },
            singleLine = true,
        )
        Button(onClick = { viewModel.generateEinvoice() }, enabled = state.einvoiceVoucherId.isNotBlank()) {
            Text("Generate IRN")
        }
        if (state.einvoiceIrn.isNotBlank()) {
            PremiumListRow(
                title = "IRN: ${state.einvoiceIrn}",
                subtitle = "Ack: ${state.einvoiceAck}",
                leading = { StatusChip(label = "GENERATED", tone = ChipTone.Success) },
            )
        }
    }
}

@Composable
private fun EwayBillTab(
    state: GstCenterUiState,
    viewModel: GstCenterViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        Text(
            "Generate an e-way bill for goods movement on a posted voucher.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = state.ewayVoucherId,
            onValueChange = { viewModel.setEwayVoucherId(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Voucher ID") },
            singleLine = true,
        )
        Button(onClick = { viewModel.generateEwayBill() }, enabled = state.ewayVoucherId.isNotBlank()) {
            Text("Generate e-way bill")
        }
        if (state.ewayBillNumber.isNotBlank()) {
            PremiumListRow(
                title = "EWB: ${state.ewayBillNumber}",
                subtitle = "Valid until ${state.ewayValidUpto}",
                leading = { StatusChip(label = "GENERATED", tone = ChipTone.Success) },
            )
        }
    }
}

@Composable
private fun HealthTab(state: GstCenterUiState) {
    val gstr1 = state.gstr1
    if (gstr1 == null) {
        PremiumEmptyState(
            icon = Icons.Outlined.CurrencyRupee,
            title = "No return generated",
            description = "Run GSTR-1 validation for the selected period to see the pre-filing checklist.",
        )
        return
    }
    val checks =
        listOf(
            "GSTIN present" to state.gstin.isNotBlank(),
            "B2B invoices have recipient GSTIN" to gstr1.sections.b2b.all { it.ctin.isNotBlank() },
            "HSN summary present" to gstr1.sections.hsnSummary.isNotEmpty(),
            "No validation errors" to state.gstr1ValidationErrors.isEmpty(),
        )
    val spacing = TanvritDesignSystem.spacing
    LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        items(checks.size) { index ->
            val (label, ok) = checks[index]
            PremiumListRow(
                title = label,
                trailing = { StatusChip(label = if (ok) "PASS" else "FAIL", tone = if (ok) ChipTone.Success else ChipTone.Error) },
                isStandalone = false,
            )
        }
    }
}
