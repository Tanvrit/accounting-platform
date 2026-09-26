package com.tanvrit.accounting.screens.recurring

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tanvrit.accounting.data.RecurringLeg
import com.tanvrit.accounting.data.RecurringSchedule
import com.tanvrit.accounting.screens.common.ChipTone
import com.tanvrit.accounting.screens.common.ConfirmDialog
import com.tanvrit.accounting.screens.common.DropdownPickerField
import com.tanvrit.accounting.screens.common.ErrorBanner
import com.tanvrit.accounting.screens.common.LoadingPane
import com.tanvrit.accounting.screens.common.ScreenHeader
import com.tanvrit.accounting.screens.common.StatusChip
import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.VoucherType
import com.tanvrit.ui.component.lifecycle.rememberViewModel
import com.tanvrit.ui.component.premium.GlassSheet
import com.tanvrit.ui.component.premium.PremiumEmptyState
import com.tanvrit.ui.theme.TanvritDesignSystem

/**
 * Recurring vouchers (roadmap #2) — client-local schedules that draft vouchers
 * automatically on their due dates. Drafts land in the Approvals queue; this
 * screen never posts.
 */
@Composable
fun RecurringVouchersScreen() {
    val viewModel = rememberViewModel { RecurringVouchersViewModel() }
    val state by viewModel.state.collectAsState()
    val spacing = TanvritDesignSystem.spacing

    Column(modifier = Modifier.fillMaxSize().padding(spacing.lg)) {
        ScreenHeader(
            title = "Recurring vouchers",
            subtitle = "Schedules that draft vouchers automatically (rent, salary, EMIs)",
            actions = {
                OutlinedButton(onClick = viewModel::processDue, enabled = !state.isLoading) { Text("Process due") }
                Button(onClick = viewModel::openCreate) { Text("New schedule") }
            },
        )

        state.error?.let {
            ErrorBanner(message = it, onRetry = { viewModel.clearError() })
            Spacer(Modifier.height(spacing.sm))
        }
        state.notice?.let {
            StatusChip(label = it, tone = ChipTone.Success)
            Spacer(Modifier.height(spacing.sm))
        }

        when {
            state.businessId.isBlank() ->
                PremiumEmptyState(
                    icon = Icons.Outlined.Autorenew,
                    title = "No business selected",
                    description = "Pick a business to manage its recurring voucher schedules.",
                )
            state.isLoading && state.schedules.isEmpty() -> LoadingPane(Modifier.weight(1f))
            state.schedules.isEmpty() ->
                PremiumEmptyState(
                    icon = Icons.Outlined.Autorenew,
                    title = "No recurring schedules",
                    description = "Create one for rent, salaries, or EMIs — due vouchers draft themselves for review.",
                )
            else ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    items(state.schedules, key = { it.id }) { schedule ->
                        ScheduleRow(
                            schedule = schedule,
                            draftedCount = state.lastRun[schedule.id] ?: 0,
                            onRunNow = { viewModel.runNow(schedule) },
                            onEdit = { viewModel.openEdit(schedule) },
                            onToggle = { viewModel.toggleEnabled(schedule) },
                            onDelete = { viewModel.askDelete(schedule) },
                        )
                    }
                }
        }
    }

    if (state.showEditor) {
        RecurringEditorSheet(
            state = state,
            onChange = viewModel::updateForm,
            onSave = viewModel::saveForm,
            onDismiss = viewModel::closeEditor,
        )
    }

    state.pendingDelete?.let { pending ->
        ConfirmDialog(
            title = "Delete schedule",
            message = "Delete “${pending.name}”? Past drafted vouchers are kept; future due dates stop.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

@Composable
private fun ScheduleRow(
    schedule: RecurringSchedule,
    draftedCount: Int,
    onRunNow: () -> Unit,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    Surface(
        shape = TanvritDesignSystem.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = TanvritDesignSystem.elevation.e1,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = schedule.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(spacing.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(label = schedule.voucherType, tone = ChipTone.Neutral)
                        StatusChip(
                            label = RecurrenceFrequency.fromCodeOrDefault(schedule.frequency).label,
                            tone = ChipTone.Info,
                        )
                        StatusChip(
                            label = "Next: ${schedule.nextDue.ifBlank { schedule.startDate }}",
                            tone = if (schedule.enabled) ChipTone.Warning else ChipTone.Neutral,
                        )
                        if (schedule.runHistory.isNotEmpty()) {
                            StatusChip(label = "${schedule.runHistory.size} runs", tone = ChipTone.Success)
                        }
                        if (draftedCount > 0) {
                            StatusChip(label = "+$draftedCount drafted just now", tone = ChipTone.Success)
                        }
                    }
                    schedule.lastError?.let {
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                IconButton(onClick = onRunNow) { Icon(Icons.Outlined.PlayArrow, contentDescription = "Run now") }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
                Switch(checked = schedule.enabled, onCheckedChange = { onToggle() })
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RecurringEditorSheet(
    state: RecurringVouchersUiState,
    onChange: ((RecurringFormState) -> RecurringFormState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    val form = state.form
    GlassSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = if (form.id.isBlank()) "New recurring schedule" else "Edit schedule",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(spacing.md))

            OutlinedTextField(
                value = form.name,
                onValueChange = { value -> onChange { it.copy(name = value) } },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(spacing.sm))

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                DropdownPickerField(
                    label = "Voucher type",
                    options = VoucherType.entries.toList(),
                    selected = form.voucherType,
                    onSelected = { type -> onChange { it.copy(voucherType = type) } },
                    modifier = Modifier.weight(1f),
                    optionLabel = { it.code },
                )
                DropdownPickerField(
                    label = "Frequency",
                    options = RecurrenceFrequency.entries.toList(),
                    selected = form.frequency,
                    onSelected = { freq -> onChange { it.copy(frequency = freq) } },
                    modifier = Modifier.weight(1f),
                    optionLabel = { it.label },
                )
            }
            Spacer(Modifier.height(spacing.sm))

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                OutlinedTextField(
                    value = form.startDate,
                    onValueChange = { value -> onChange { it.copy(startDate = value) } },
                    label = { Text("Start date (yyyy-MM-dd)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = form.endDate,
                    onValueChange = { value -> onChange { it.copy(endDate = value) } },
                    label = { Text("End date (optional)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(spacing.sm))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Enabled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = form.enabled,
                    onCheckedChange = { value -> onChange { it.copy(enabled = value) } },
                )
            }
            Spacer(Modifier.height(spacing.md))

            Text(
                text = "Legs (must balance)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(spacing.xs))
            form.legs.forEachIndexed { index, leg ->
                LegEditor(
                    leg = leg,
                    accounts = state.accounts,
                    onChange = { updated ->
                        onChange { current -> current.copy(legs = current.legs.mapIndexed { i, l -> if (i == index) updated else l }) }
                    },
                    onRemove = {
                        onChange { current -> current.copy(legs = current.legs.filterIndexed { i, _ -> i != index }) }
                    },
                )
                Spacer(Modifier.height(spacing.xs))
            }
            OutlinedButton(
                onClick = { onChange { it.copy(legs = it.legs + RecurringLeg()) } },
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(spacing.xs))
                Text("Add leg")
            }
            Spacer(Modifier.height(spacing.lg))

            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("Save schedule")
            }
        }
    }
}

@Composable
private fun LegEditor(
    leg: RecurringLeg,
    accounts: List<Account>,
    onChange: (RecurringLeg) -> Unit,
    onRemove: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    Surface(
        shape = TanvritDesignSystem.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DropdownPickerField(
                    label = "Account",
                    options = accounts,
                    selected = accounts.firstOrNull { it.id == leg.accountId },
                    onSelected = { account ->
                        onChange(
                            leg.copy(
                                accountId = account.id,
                                accountCode = account.accountCode,
                                accountName = account.name,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    optionLabel = { "${it.accountCode} · ${it.name}" },
                )
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, contentDescription = "Remove leg") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                OutlinedTextField(
                    value = leg.debit,
                    onValueChange = { value -> onChange(leg.copy(debit = value)) },
                    label = { Text("Debit") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = leg.credit,
                    onValueChange = { value -> onChange(leg.copy(credit = value)) },
                    label = { Text("Credit") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(spacing.xs))
            OutlinedTextField(
                value = leg.narration,
                onValueChange = { value -> onChange(leg.copy(narration = value)) },
                label = { Text("Narration (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}
