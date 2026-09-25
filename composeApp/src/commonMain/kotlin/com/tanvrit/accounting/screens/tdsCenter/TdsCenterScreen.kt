package com.tanvrit.accounting.screens.tdsCenter

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
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.tanvrit.accounting.screens.common.MoneyText
import com.tanvrit.accounting.screens.common.ScreenHeader
import com.tanvrit.accounting.screens.common.StatusChip
import com.tanvrit.accounting.screens.common.formatMoney
import com.tanvrit.core.feature.accounting.model.FiscalPeriod
import com.tanvrit.ui.component.lifecycle.rememberViewModel
import com.tanvrit.ui.component.premium.PremiumEmptyState
import com.tanvrit.ui.component.premium.PremiumListRow
import com.tanvrit.ui.theme.TanvritDesignSystem

private val RETURN_TYPES = listOf("26Q", "27Q", "24Q")
private val QUARTERS = listOf("Q1", "Q2", "Q3", "Q4")
private val SECTIONS = listOf("192", "194A", "194C", "194H", "194I", "194J")

/** TDS Center — returns (26Q/27Q/24Q), challan linkage, Form 16 / 16A. */
@Composable
fun TdsCenterScreen() {
    val viewModel = rememberViewModel { TdsCenterViewModel() }
    val state by viewModel.state.collectAsState()
    val spacing = TanvritDesignSystem.spacing

    Column(modifier = Modifier.fillMaxSize().padding(spacing.lg)) {
        ScreenHeader(
            title = "TDS Center",
            subtitle = "Returns, challans and certificates",
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
                value = state.tan,
                onValueChange = { viewModel.setTan(it) },
                modifier = Modifier.weight(1f),
                label = { Text("TAN") },
                singleLine = true,
            )
            DropdownPickerField(
                label = "Period",
                options = state.periods,
                selected = state.periods.firstOrNull { it.id == state.selectedPeriodId },
                onSelected = { period: FiscalPeriod -> viewModel.selectPeriod(period.id) },
                modifier = Modifier.weight(1f),
                optionLabel = { it.name },
            )
        }
        Spacer(Modifier.height(spacing.md))

        TabRow(selectedTabIndex = state.activeTab.ordinal) {
            TdsTab.entries.forEach { tab ->
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
        } else {
            when (state.activeTab) {
                TdsTab.RETURNS -> ReturnsTab(state, viewModel)
                TdsTab.CHALLANS -> ChallansTab(state)
                TdsTab.CERTIFICATES -> CertificatesTab(state, viewModel)
            }
        }
    }
}

@Composable
private fun ReturnsTab(
    state: TdsCenterUiState,
    viewModel: TdsCenterViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                DropdownPickerField(
                    label = "Return",
                    options = RETURN_TYPES,
                    selected = state.returnType,
                    onSelected = { viewModel.setReturnType(it) },
                    modifier = Modifier.weight(1f),
                )
                DropdownPickerField(
                    label = "Quarter",
                    options = QUARTERS,
                    selected = state.quarter,
                    onSelected = { viewModel.setQuarter(it) },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.financialYear,
                    onValueChange = { viewModel.setFinancialYear(it) },
                    modifier = Modifier.weight(1f),
                    label = { Text("FY (e.g. 2025-26)") },
                    singleLine = true,
                )
            }
        }
        item {
            Button(onClick = { viewModel.generateReturn() }) { Text("Generate ${state.returnType}") }
        }
        state.tdsReturn?.let { tdsReturn ->
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${state.returnType} · ${tdsReturn.quarter} FY ${tdsReturn.financialYear}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    StatusChip(
                        label = tdsReturn.status,
                        tone = if (tdsReturn.status == "FILED") ChipTone.Success else ChipTone.Warning,
                    )
                }
            }
            state.fvuPath?.let { path ->
                item {
                    PremiumListRow(
                        title = "FVU file ready",
                        subtitle = path,
                        leading = { StatusChip(label = "FVU", tone = ChipTone.Info) },
                    )
                }
            }
            items(tdsReturn.deductions) { deduction ->
                PremiumListRow(
                    title = "${deduction.deducteeName.ifBlank { deduction.deducteePan }} · §${deduction.section}",
                    subtitle = "Paid ${deduction.dateOfDeduction} · rate ${deduction.rate}%",
                    supporting = "Challan ${deduction.challanNumber.ifBlank { "—" }}",
                    trailing = {
                        Column {
                            MoneyText(money = deduction.tdsDeducted, bold = true)
                            Text(
                                "of ${formatMoney(deduction.amountPaid)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    isStandalone = false,
                )
            }
        }
    }
}

@Composable
private fun ChallansTab(state: TdsCenterUiState) {
    val deductions = state.tdsReturn?.deductions.orEmpty()
    val challanLinked = deductions.filter { it.challanNumber.isNotBlank() }
    val unlinked = deductions.filter { it.challanNumber.isBlank() }
    val spacing = TanvritDesignSystem.spacing

    if (deductions.isEmpty()) {
        PremiumEmptyState(
            icon = Icons.Outlined.Percent,
            title = "No return generated",
            description = "Generate a 26Q/27Q/24Q return first — challan linkage is derived from its deduction rows.",
        )
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        item {
            Text(
                "Linked to challan (${challanLinked.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        items(challanLinked) { deduction ->
            PremiumListRow(
                title = "Challan ${deduction.challanNumber} · ${deduction.challanDate}",
                subtitle = deduction.deducteeName,
                trailing = { MoneyText(money = deduction.tdsDeducted, bold = true) },
                leading = { StatusChip(label = "LINKED", tone = ChipTone.Success) },
                isStandalone = false,
            )
        }
        item {
            Text(
                "Awaiting challan (${unlinked.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        items(unlinked) { deduction ->
            PremiumListRow(
                title = deduction.deducteeName.ifBlank { deduction.deducteePan },
                subtitle = "§${deduction.section} · ${deduction.dateOfDeduction}",
                trailing = { MoneyText(money = deduction.tdsDeducted, bold = true) },
                leading = { StatusChip(label = "UNLINKED", tone = ChipTone.Warning) },
                isStandalone = false,
            )
        }
    }
}

@Composable
private fun CertificatesTab(
    state: TdsCenterUiState,
    viewModel: TdsCenterViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text("Form 16 (salary, annual)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = state.certificateEmployeePan,
            onValueChange = { viewModel.setCertificateEmployeePan(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Employee PAN") },
            singleLine = true,
        )
        Button(
            onClick = { viewModel.generateForm16() },
            enabled = state.certificateEmployeePan.isNotBlank(),
        ) { Text("Generate Form 16") }
        state.form16?.let { form16 ->
            PremiumListRow(
                title = "Form 16 · ${form16.employeeName} (${form16.employeePan})",
                subtitle = "FY ${form16.financialYear} · ${form16.quarters.size} quarter(s)",
                supporting = "Gross ${formatMoney(form16.grossSalary)}",
                leading = { StatusChip(label = "READY", tone = ChipTone.Success) },
            )
        }

        Spacer(Modifier.height(spacing.lg))

        Text("Form 16A (non-salary, quarterly)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedTextField(
                value = state.certificateDeducteePan,
                onValueChange = { viewModel.setCertificateDeducteePan(it) },
                modifier = Modifier.weight(2f),
                label = { Text("Deductee PAN") },
                singleLine = true,
            )
            DropdownPickerField(
                label = "Section",
                options = SECTIONS,
                selected = state.certificateSection,
                onSelected = { viewModel.setCertificateSection(it) },
                modifier = Modifier.weight(1f),
            )
        }
        Button(
            onClick = { viewModel.generateForm16a() },
            enabled = state.certificateDeducteePan.isNotBlank(),
        ) { Text("Generate Form 16A") }
        state.form16a?.let { form16a ->
            PremiumListRow(
                title = "Form 16A · ${form16a.deducteeName} (${form16a.deducteePan})",
                subtitle = "§${form16a.section} · FY ${form16a.financialYear} · ${form16a.challanDetails.size} challan(s)",
                leading = { StatusChip(label = "READY", tone = ChipTone.Success) },
            )
        }
    }
}
