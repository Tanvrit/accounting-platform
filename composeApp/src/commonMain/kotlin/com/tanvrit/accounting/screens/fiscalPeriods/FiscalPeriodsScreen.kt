package com.tanvrit.accounting.screens.fiscalPeriods

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tanvrit.accounting.screens.common.ChipTone
import com.tanvrit.accounting.screens.common.ConfirmDialog
import com.tanvrit.accounting.screens.common.DropdownPickerField
import com.tanvrit.accounting.screens.common.ErrorBanner
import com.tanvrit.accounting.screens.common.LoadingPane
import com.tanvrit.accounting.screens.common.ScreenHeader
import com.tanvrit.accounting.screens.common.StatusChip
import com.tanvrit.core.feature.accounting.model.FiscalPeriod
import com.tanvrit.core.feature.accounting.model.FiscalPeriodStatus
import com.tanvrit.ui.component.lifecycle.rememberViewModel
import com.tanvrit.ui.component.premium.GlassSheet
import com.tanvrit.ui.component.premium.PremiumEmptyState
import com.tanvrit.ui.component.premium.PremiumListRow
import com.tanvrit.ui.theme.TanvritDesignSystem

/** Fiscal Period management — open/lock/close, opening-balance carry forward. */
@Composable
fun FiscalPeriodsScreen() {
    val viewModel = rememberViewModel { FiscalPeriodsViewModel() }
    val state by viewModel.state.collectAsState()
    val spacing = TanvritDesignSystem.spacing

    Column(modifier = Modifier.fillMaxSize().padding(spacing.lg)) {
        ScreenHeader(
            title = "Fiscal Periods",
            subtitle = "${state.periods.size} periods",
            actions = {
                Button(onClick = { viewModel.openEditor() }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(spacing.xs))
                    Text("New period")
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

        when {
            state.isLoading -> LoadingPane(Modifier.weight(1f))
            state.periods.isEmpty() ->
                PremiumEmptyState(
                    icon = Icons.Outlined.DateRange,
                    title = "No fiscal periods",
                    description = "Create FY → Quarter → Month periods to control posting windows and year-end close.",
                )
            else -> PeriodList(state, viewModel)
        }
    }

    if (state.editor.visible) {
        PeriodEditorSheet(
            state = state,
            onChange = viewModel::updateEditor,
            onCreate = viewModel::createPeriod,
            onDismiss = viewModel::closeEditor,
        )
    }

    state.pendingAction?.let { pending ->
        val period = state.periods.firstOrNull { it.id == pending.periodId }
        when (pending.kind) {
            PendingPeriodAction.Kind.LOCK ->
                ConfirmDialog(
                    title = "Lock period ${period?.name ?: ""}?",
                    message = "Locked periods reject new vouchers and postings. Unlocking requires a separate authorized action.",
                    confirmLabel = "Lock period",
                    onConfirm = { viewModel.confirmPendingAction() },
                    onDismiss = { viewModel.disarmAction() },
                )
            PendingPeriodAction.Kind.CLOSE ->
                ConfirmDialog(
                    title = "Close period ${period?.name ?: ""}?",
                    message =
                        "Closing is permanent. Checklist: all vouchers posted, bank reconciliation done, " +
                            "GST returns filed. Opening balances carry forward.",
                    confirmLabel = "Close period",
                    onConfirm = { viewModel.confirmPendingAction() },
                    onDismiss = { viewModel.disarmAction() },
                    destructive = true,
                )
        }
    }
}

@Composable
private fun PeriodList(
    state: FiscalPeriodsUiState,
    viewModel: FiscalPeriodsViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        items(state.periods, key = { it.id }) { period ->
            PeriodRow(
                period = period,
                onLock = { viewModel.armAction(period.id, PendingPeriodAction.Kind.LOCK) },
                onClose = { viewModel.armAction(period.id, PendingPeriodAction.Kind.CLOSE) },
            )
        }
        item { CarryForwardCard(state, viewModel) }
    }
}

@Composable
private fun PeriodRow(
    period: FiscalPeriod,
    onLock: () -> Unit,
    onClose: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    PremiumListRow(
        title = period.name,
        subtitle = "${period.startDate} → ${period.endDate}",
        supporting =
            buildList {
                if (period.carriedForward) add("Balances carried forward")
                period.closedAt?.let { add("Closed $it") }
            }.joinToString(" · ").ifBlank { null },
        leading = {
            StatusChip(
                label = period.status.code,
                tone =
                    when (period.status) {
                        FiscalPeriodStatus.OPEN -> ChipTone.Success
                        FiscalPeriodStatus.LOCKED -> ChipTone.Warning
                        FiscalPeriodStatus.CLOSED -> ChipTone.Neutral
                    },
            )
        },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                if (period.status == FiscalPeriodStatus.OPEN) {
                    OutlinedButton(onClick = onLock) {
                        Icon(Icons.Outlined.Lock, contentDescription = null)
                        Text("Lock")
                    }
                    Button(onClick = onClose) { Text("Close") }
                } else {
                    Icon(
                        imageVector = if (period.status == FiscalPeriodStatus.LOCKED) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                        contentDescription = period.status.code,
                    )
                }
            }
        },
    )
}

@Composable
private fun CarryForwardCard(
    state: FiscalPeriodsUiState,
    viewModel: FiscalPeriodsViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    val closed = state.periods.filter { it.status == FiscalPeriodStatus.CLOSED }
    val open = state.periods.filter { it.status == FiscalPeriodStatus.OPEN }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TanvritDesignSystem.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = TanvritDesignSystem.elevation.e1,
    ) {
        Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text("Carry opening balances forward", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Copies closing balances from a closed period as opening balances of the target open period.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                DropdownPickerField(
                    label = "From (closed)",
                    options = closed,
                    selected = closed.firstOrNull { it.id == state.carrySourceId },
                    onSelected = { viewModel.setCarrySource(it.id) },
                    modifier = Modifier.weight(1f),
                    optionLabel = { it.name },
                )
                DropdownPickerField(
                    label = "To (open)",
                    options = open,
                    selected = open.firstOrNull { it.id == state.carryTargetId },
                    onSelected = { viewModel.setCarryTarget(it.id) },
                    modifier = Modifier.weight(1f),
                    optionLabel = { it.name },
                )
            }
            Button(
                onClick = { viewModel.carryForward() },
                enabled = state.carrySourceId.isNotBlank() && state.carryTargetId.isNotBlank() && !state.isLoading,
            ) { Text("Carry forward") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodEditorSheet(
    state: FiscalPeriodsUiState,
    onChange: ((PeriodEditorState) -> PeriodEditorState) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val editor = state.editor
    val spacing = TanvritDesignSystem.spacing
    GlassSheet(onDismiss = onDismiss) {
        Text("New fiscal period", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(spacing.lg))
        OutlinedTextField(
            value = editor.name,
            onValueChange = { v -> onChange { it.copy(name = v) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name (e.g. FY 2026-27)") },
            singleLine = true,
        )
        Spacer(Modifier.height(spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedTextField(
                value = editor.startDate,
                onValueChange = { v -> onChange { it.copy(startDate = v) } },
                modifier = Modifier.weight(1f),
                label = { Text("Start (YYYY-MM-DD)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = editor.endDate,
                onValueChange = { v -> onChange { it.copy(endDate = v) } },
                modifier = Modifier.weight(1f),
                label = { Text("End (YYYY-MM-DD)") },
                singleLine = true,
            )
        }
        Spacer(Modifier.height(spacing.sm))
        DropdownPickerField(
            label = "Parent period",
            options = listOf<FiscalPeriod?>(null) + state.periods,
            selected = state.periods.firstOrNull { it.id == editor.parentPeriodId },
            onSelected = { parent -> onChange { it.copy(parentPeriodId = parent?.id ?: "") } },
            modifier = Modifier.fillMaxWidth(),
            optionLabel = { it?.name ?: "None (fiscal year)" },
        )
        Spacer(Modifier.height(spacing.xl))
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Create period") }
    }
}
