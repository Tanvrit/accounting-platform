package com.tanvrit.accounting.screens.budget

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
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.tanvrit.accounting.screens.common.ChipTone
import com.tanvrit.accounting.screens.common.DropdownPickerField
import com.tanvrit.accounting.screens.common.ErrorBanner
import com.tanvrit.accounting.screens.common.LoadingPane
import com.tanvrit.accounting.screens.common.MoneyText
import com.tanvrit.accounting.screens.common.ScreenHeader
import com.tanvrit.accounting.screens.common.StatusChip
import com.tanvrit.accounting.screens.common.formatMoney
import com.tanvrit.accounting.screens.common.signedPercent
import com.tanvrit.core.feature.accounting.model.FiscalPeriod
import com.tanvrit.core.feature.money.Money
import com.tanvrit.ui.component.lifecycle.rememberViewModel
import com.tanvrit.ui.component.premium.GlassSheet
import com.tanvrit.ui.component.premium.PremiumEmptyState
import com.tanvrit.ui.component.premium.PremiumListRow
import com.tanvrit.ui.theme.TanvritDesignSystem

/** Budget — editor grid, budget-vs-actual variance, indicative forecast. */
@Composable
fun BudgetScreen() {
    val viewModel = rememberViewModel { BudgetViewModel() }
    val state by viewModel.state.collectAsState()
    val spacing = TanvritDesignSystem.spacing

    Column(modifier = Modifier.fillMaxSize().padding(spacing.lg)) {
        ScreenHeader(
            title = "Budget",
            subtitle = "Plan · variance · forecast by fiscal period",
            actions = {
                Button(onClick = { viewModel.openEditor(null) }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(spacing.xs))
                    Text("New line")
                }
            },
        )

        state.error?.let {
            ErrorBanner(message = it, onRetry = { viewModel.refresh() })
            Spacer(Modifier.height(spacing.sm))
        }
        state.notice?.let {
            StatusChip(label = it, tone = ChipTone.Success)
            Spacer(Modifier.height(spacing.sm))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DropdownPickerField(
                label = "Period",
                options = state.periods,
                selected = state.periods.firstOrNull { it.id == state.selectedPeriodId },
                onSelected = { period: FiscalPeriod -> viewModel.selectPeriod(period.id) },
                modifier = Modifier.weight(1f),
                optionLabel = { it.name },
            )
            FilterToggle(
                showVariance = state.showVariance,
                onToggle = viewModel::toggleVariance,
            )
        }
        Spacer(Modifier.height(spacing.md))

        if (state.isLoading) {
            LoadingPane(Modifier.weight(1f))
        } else if (state.budgets.isEmpty()) {
            PremiumEmptyState(
                icon = Icons.Outlined.AccountBalance,
                title = "No budget lines",
                description = "Add budgeted amounts per account for the selected fiscal period.",
            )
        } else {
            BudgetContent(state, viewModel)
        }
    }

    if (state.editor.visible) {
        BudgetEditorSheet(
            state = state,
            onChange = viewModel::updateEditor,
            onSave = viewModel::saveBudget,
            onDismiss = viewModel::closeEditor,
        )
    }
}

@Composable
private fun FilterToggle(
    showVariance: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(TanvritDesignSystem.spacing.xs)) {
        OutlinedButton(onClick = { onToggle(true) }) {
            Text(
                "Variance",
                fontWeight = if (showVariance) FontWeight.Bold else FontWeight.Normal,
            )
        }
        OutlinedButton(onClick = { onToggle(false) }) {
            Text(
                "Forecast",
                fontWeight = if (showVariance) FontWeight.Normal else FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun BudgetContent(
    state: BudgetUiState,
    viewModel: BudgetViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = TanvritDesignSystem.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = TanvritDesignSystem.elevation.e1,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(spacing.lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Total budgeted", style = MaterialTheme.typography.labelSmall)
                        MoneyText(money = state.totalBudgeted, bold = true)
                    }
                    Column {
                        Text(if (state.showVariance) "Actual" else "Forecast base", style = MaterialTheme.typography.labelSmall)
                        MoneyText(money = state.totalActual, bold = true)
                    }
                    Column {
                        Text("Variance", style = MaterialTheme.typography.labelSmall)
                        MoneyText(money = state.totalVariance, bold = true, colorizeSign = true)
                    }
                }
            }
        }
        items(
            if (state.showVariance) state.varianceRows else state.forecastRows,
            key = { it.accountCode + it.budgeted.amountInSmallestUnit },
        ) { row ->
            VarianceRow(row = row, forecast = !state.showVariance, onEdit = {
                state.budgets.firstOrNull { it.accountCode == row.accountCode }?.let { viewModel.openEditor(it) }
            })
        }
    }
}

@Composable
private fun VarianceRow(
    row: BudgetVarianceRow,
    forecast: Boolean,
    onEdit: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    val usedPercent =
        if (row.budgeted == Money.ZERO) 0f else (row.actual.toDouble() / row.budgeted.toDouble()).toFloat().coerceIn(0f, 1f)
    PremiumListRow(
        title = "${row.accountCode} — ${row.accountName}",
        subtitle =
            if (forecast) {
                "Forecast ${formatMoney(row.budgeted)} (indicative)"
            } else {
                "Budget ${formatMoney(row.budgeted)} · Actual ${formatMoney(row.actual)}"
            },
        supporting =
            if (forecast) {
                ""
            } else {
                signedPercent(row.variancePercent)
            },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(money = if (forecast) row.actual else row.variance, bold = true, colorizeSign = !forecast)
                Spacer(Modifier.height(spacing.xxs))
                LinearProgressIndicator(
                    progress = { usedPercent },
                    modifier = Modifier.width(TanvritDesignSystem.spacing.avatar),
                )
            }
        },
        onClick = onEdit,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetEditorSheet(
    state: BudgetUiState,
    onChange: ((BudgetEditorState) -> BudgetEditorState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val editor = state.editor
    val spacing = TanvritDesignSystem.spacing
    GlassSheet(onDismiss = onDismiss) {
        Text(
            text = if (editor.id.isBlank()) "New budget line" else "Edit budget line",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(spacing.lg))
        DropdownPickerField(
            label = "Account",
            options = state.accounts.sortedBy { it.accountCode },
            selected = state.accounts.firstOrNull { it.id == editor.accountId },
            onSelected = { account -> onChange { it.copy(accountId = account.id) } },
            modifier = Modifier.fillMaxWidth(),
            optionLabel = { "${it.accountCode} — ${it.name}" },
            enabled = editor.id.isBlank(),
        )
        Spacer(Modifier.height(spacing.sm))
        OutlinedTextField(
            value = editor.amountText,
            onValueChange = { v -> onChange { it.copy(amountText = v) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Budgeted amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )
        Spacer(Modifier.height(spacing.xl))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save budget") }
    }
}
