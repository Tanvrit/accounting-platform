package com.tanvrit.accounting.screens.consolidation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Hub
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.tanvrit.accounting.screens.common.ChipTone
import com.tanvrit.accounting.screens.common.ConfirmDialog
import com.tanvrit.accounting.screens.common.DropdownPickerField
import com.tanvrit.accounting.screens.common.ErrorBanner
import com.tanvrit.accounting.screens.common.LoadingPane
import com.tanvrit.accounting.screens.common.MoneyText
import com.tanvrit.accounting.screens.common.ScreenHeader
import com.tanvrit.accounting.screens.common.StatusChip
import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.ConsolidatedReport
import com.tanvrit.core.feature.accounting.model.ConsolidationGroup
import com.tanvrit.core.feature.accounting.model.EliminationEntry
import com.tanvrit.core.feature.money.Money
import com.tanvrit.ui.component.lifecycle.rememberViewModel
import com.tanvrit.ui.component.premium.GlassSheet
import com.tanvrit.ui.component.premium.PremiumEmptyState
import com.tanvrit.ui.component.premium.PremiumListRow
import com.tanvrit.ui.theme.TanvritDesignSystem

/**
 * Group consolidation viewer (roadmap #4): consolidation groups on the
 * current business, a server-run consolidated TB/P&L/BS/cash-flow for a
 * chosen OPEN period, and manual intercompany elimination entries.
 */
@Composable
fun ConsolidationScreen() {
    val viewModel = rememberViewModel { ConsolidationViewModel() }
    val state by viewModel.state.collectAsState()
    val spacing = TanvritDesignSystem.spacing

    Column(modifier = Modifier.fillMaxSize().padding(spacing.lg)) {
        ScreenHeader(
            title = "Consolidation",
            subtitle = "Group companies — consolidated statements and intercompany eliminations",
            actions = {
                OutlinedButton(
                    onClick = { viewModel.refresh() },
                    enabled = !state.isLoading && !state.generating,
                ) { Text("Refresh") }
                Button(
                    onClick = viewModel::openAddGroup,
                    enabled = state.businessId.isNotBlank() && !state.isLoading,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("New group")
                }
            },
        )

        state.error?.let {
            ErrorBanner(message = it, onRetry = { viewModel.refresh() })
            Spacer(Modifier.height(spacing.sm))
        }
        state.notice?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(spacing.sm))
        }

        when {
            state.businessId.isBlank() -> NoBusinessPane()
            state.isLoading && state.groups.isEmpty() -> LoadingPane(Modifier.weight(1f))
            state.isEmpty -> {
                PremiumEmptyState(
                    icon = Icons.Outlined.Hub,
                    title = "No consolidation groups",
                    description =
                        "Create a group — the current business becomes the parent and subsidiary ids are " +
                            "entered by hand — then run consolidation for an OPEN fiscal period.",
                )
            }
            else -> ConsolidationBody(state = state, viewModel = viewModel)
        }
    }

    if (state.confirmArmed) {
        val groupName = state.selectedGroup?.name ?: ""
        val periodName = state.openPeriods.firstOrNull { it.id == state.periodId }?.name ?: state.periodId
        ConfirmDialog(
            title = "Run consolidation?",
            message =
                "Generate the consolidated report for \"$groupName\" in \"$periodName\". The run covers the " +
                    "current business; subsidiary inclusion follows the group's member list server-side.",
            confirmLabel = "Run consolidation",
            onConfirm = viewModel::confirmConsolidation,
            onDismiss = viewModel::disarmConsolidation,
        )
    }

    if (state.addGroupOpen) {
        AddGroupSheet(state = state, viewModel = viewModel)
    }

    if (state.eliminationOpen) {
        EliminationSheet(state = state, viewModel = viewModel)
    }
}

@Composable
private fun NoBusinessPane() {
    PremiumEmptyState(
        icon = Icons.Outlined.AccountBalanceWallet,
        title = "No business selected",
        description = "Select or create a business workspace to define consolidation groups and generate reports.",
    )
}

@Composable
private fun ConsolidationBody(
    state: ConsolidationUiState,
    viewModel: ConsolidationViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    val group = state.selectedGroup

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item(key = "groups") {
            GroupsCard(state = state, viewModel = viewModel)
        }

        if (group != null) {
            item(key = "panel") {
                ConsolidatePanel(state = state, group = group, viewModel = viewModel)
            }

            state.report?.let { report ->
                item(key = "report-summary") { ReportSummaryCard(report = report) }

                val sections = reportSections(report)
                if (sections.isEmpty()) {
                    item(key = "report-empty") {
                        Text(
                            text = "The server returned a report with no statements — the group or period may be unsettled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(sections, key = { "section-${it.title}" }) { section ->
                        ReportSectionCard(section = section)
                    }
                }

                item(key = "eliminations-header") {
                    EliminationsHeader(entries = report.eliminations)
                }
                if (report.eliminations.isEmpty()) {
                    item(key = "eliminations-empty") {
                        Text(
                            text = "No elimination entries returned with this report.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(
                        report.eliminations,
                        key = { "elim-${it.id.ifBlank { "${it.debitAccountId}-${it.creditAccountId}-${it.description}" }}" },
                    ) { entry ->
                        EliminationRow(entry = entry, accountsById = state.accountsById)
                    }
                }
            }
        }
    }
}

/** Groups card: one row per group with member-business-id chips and a member count. */
@Composable
private fun GroupsCard(
    state: ConsolidationUiState,
    viewModel: ConsolidationViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TanvritDesignSystem.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = TanvritDesignSystem.elevation.e1,
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text("Groups", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            state.groups.forEach { group ->
                GroupRow(
                    group = group,
                    selected = group.id == state.selectedGroupId,
                    onClick = { viewModel.selectGroup(group.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupRow(
    group: ConsolidationGroup,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    val summary = groupSummary(group)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = TanvritDesignSystem.shapes.medium,
        color =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        tonalElevation = TanvritDesignSystem.elevation.e1,
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summary.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(
                    label = if (group.isActive) "ACTIVE" else "INACTIVE",
                    tone = if (group.isActive) ChipTone.Success else ChipTone.Neutral,
                )
            }
            Text(
                text = summary.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val ids = memberIds(group)
            if (ids.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    ids.forEach { id -> StatusChip(label = id, tone = ChipTone.Info) }
                }
            }
        }
    }
}

/** Run panel: OPEN-period picker + confirmed run + the elimination-entry entrypoint. */
@Composable
private fun ConsolidatePanel(
    state: ConsolidationUiState,
    group: ConsolidationGroup,
    viewModel: ConsolidationViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TanvritDesignSystem.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = TanvritDesignSystem.elevation.e1,
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text("Consolidate · ${group.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Consolidation runs for the current business against the group definition — the report " +
                    "request carries this business plus an OPEN fiscal period; elimination entries are then " +
                    "fine-tuned against the same group and period.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.openPeriods.isEmpty()) {
                Text(
                    text = "No OPEN fiscal period — create and open one in Periods.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DropdownPickerField(
                        label = "Period",
                        options = state.openPeriods,
                        selected = state.openPeriods.firstOrNull { it.id == state.periodId },
                        onSelected = { viewModel.onPeriodChange(it.id) },
                        modifier = Modifier.weight(1f),
                        optionLabel = { it.name },
                        enabled = !state.generating,
                    )
                    Button(
                        onClick = viewModel::armConsolidation,
                        enabled = state.periodId.isNotBlank() && !state.generating && !state.isLoading,
                    ) { Text("Run consolidation") }
                    OutlinedButton(
                        onClick = viewModel::openElimination,
                        enabled = !state.generating && !state.isLoading,
                    ) { Text("New elimination") }
                }
            }
        }
    }
}

/** Header-level figures of the returned report — ids, periods and the minority interest. */
@Composable
private fun ReportSummaryCard(report: ConsolidatedReport) {
    val spacing = TanvritDesignSystem.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TanvritDesignSystem.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = TanvritDesignSystem.elevation.e1,
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text("Consolidated report", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text =
                    listOf(
                        "business ${report.businessId}".takeIf { report.businessId.isNotBlank() },
                        "group ${report.groupId}".takeIf { report.groupId.isNotBlank() },
                        "period ${report.fiscalPeriodId}".takeIf { report.fiscalPeriodId.isNotBlank() },
                    ).filterNotNull().joinToString(" · ").ifBlank { "as returned by the server" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (report.minorityInterest != Money.ZERO) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Minority interest",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MoneyText(money = report.minorityInterest, bold = true, colorizeSign = true)
                }
            }
        }
    }
}

/** One statement section — label/amount rows, totals emphasized by the mapper. */
@Composable
private fun ReportSectionCard(section: ReportSection) {
    val spacing = TanvritDesignSystem.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TanvritDesignSystem.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = TanvritDesignSystem.elevation.e1,
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            section.rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    MoneyText(
                        money = row.amount,
                        bold = row.emphasis,
                        colorizeSign = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun EliminationsHeader(entries: List<EliminationEntry>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Eliminations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text =
                    "${entries.size} entry(ies) returned in the report payload (no separate list endpoint in " +
                        "the SDK) · total →",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MoneyText(money = eliminationTotal(entries), bold = true)
    }
}

@Composable
private fun EliminationRow(
    entry: EliminationEntry,
    accountsById: Map<String, Account>,
) {
    val spacing = TanvritDesignSystem.spacing
    val debitLabel =
        accountsById[entry.debitAccountId]?.let { "${it.accountCode} · ${it.name}" } ?: entry.debitAccountId
    val creditLabel =
        accountsById[entry.creditAccountId]?.let { "${it.accountCode} · ${it.name}" } ?: entry.creditAccountId
    PremiumListRow(
        title = entry.description.ifBlank { entry.type.ifBlank { "(no description)" } },
        subtitle = "${entry.type.ifBlank { "ENTRY" }} · DR $debitLabel · CR $creditLabel",
        trailing = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                MoneyText(money = entry.amount, bold = true)
                StatusChip(
                    label = if (entry.eliminated) "ELIMINATED" else "PENDING",
                    tone = if (entry.eliminated) ChipTone.Success else ChipTone.Warning,
                )
            }
        },
    )
}

/** New group — name + comma-separated member business ids; no directory lookup exists in this app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGroupSheet(
    state: ConsolidationUiState,
    viewModel: ConsolidationViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    GlassSheet(onDismiss = viewModel::closeAddGroup) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                "New consolidation group",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(spacing.md))

            state.sheetError?.let {
                ErrorBanner(message = it)
                Spacer(Modifier.height(spacing.sm))
            }

            OutlinedTextField(
                value = state.groupName,
                onValueChange = viewModel::onGroupNameChange,
                label = { Text("Group name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.addGroupSaving,
            )
            Spacer(Modifier.height(spacing.sm))

            OutlinedTextField(
                value = state.memberIdsText,
                onValueChange = viewModel::onMemberIdsTextChange,
                label = { Text("Business IDs, comma-separated") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                enabled = !state.addGroupSaving,
            )
            Text(
                text =
                    "Member ids are entered by hand — this app has no business-directory lookup (the workspace " +
                        "exposes only the current business); the current business becomes the group parent.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = "${state.parsedMemberIds.size} member(s) parsed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.lg))

            Button(
                onClick = viewModel::createGroup,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.addGroupSaving && state.groupName.isNotBlank() && state.parsedMemberIds.isNotEmpty(),
            ) { Text("Create group") }
        }
    }
}

/** New elimination entry — debit/credit account pickers, amount and description. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EliminationSheet(
    state: ConsolidationUiState,
    viewModel: ConsolidationViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    GlassSheet(onDismiss = viewModel::closeElimination) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                "New elimination entry",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(spacing.md))

            state.sheetError?.let {
                ErrorBanner(message = it)
                Spacer(Modifier.height(spacing.sm))
            }

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                enabled = !state.eliminationSaving,
            )
            Spacer(Modifier.height(spacing.sm))

            DropdownPickerField(
                label = "Type",
                options = ConsolidationViewModel.ELIMINATION_TYPES,
                selected = state.eliminationType,
                onSelected = viewModel::onEliminationTypeChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.eliminationSaving,
            )
            Spacer(Modifier.height(spacing.sm))

            AccountPickerField(
                label = "Debit account",
                selectedId = state.debitAccountId,
                accounts = state.accounts,
                enabled = !state.eliminationSaving,
                onSelected = viewModel::onDebitAccountChange,
            )
            AccountPickerField(
                label = "Credit account",
                selectedId = state.creditAccountId,
                accounts = state.accounts,
                enabled = !state.eliminationSaving,
                onSelected = viewModel::onCreditAccountChange,
            )
            Spacer(Modifier.height(spacing.sm))

            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::onAmountTextChange,
                label = { Text("Amount") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.eliminationSaving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(Modifier.height(spacing.sm))

            OutlinedTextField(
                value = state.linkedVoucherId,
                onValueChange = viewModel::onLinkedVoucherChange,
                label = { Text("Linked voucher / journal id (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.eliminationSaving,
            )
            Text(
                text =
                    "The API has no dedicated link field — this reference is recorded inside the entry " +
                        "description.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.lg))

            Button(
                onClick = viewModel::createElimination,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.eliminationSaving,
            ) { Text("Save entry") }
        }
    }
}

@Composable
private fun AccountPickerField(
    label: String,
    selectedId: String,
    accounts: List<Account>,
    enabled: Boolean,
    onSelected: (String) -> Unit,
) {
    DropdownPickerField(
        label = label,
        options = accounts,
        selected = accounts.firstOrNull { it.id == selectedId },
        onSelected = { account -> onSelected(account.id) },
        modifier = Modifier.fillMaxWidth(),
        optionLabel = { "${it.accountCode} · ${it.name}" },
        enabled = enabled,
    )
}
