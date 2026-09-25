package com.tanvrit.accounting.screens.auditTrail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.tanvrit.core.feature.accounting.model.AuditAction
import com.tanvrit.core.feature.accounting.model.AuditLogEntry
import com.tanvrit.ui.component.lifecycle.rememberViewModel
import com.tanvrit.ui.component.premium.GlassSheet
import com.tanvrit.ui.component.premium.PremiumEmptyState
import com.tanvrit.ui.component.premium.PremiumListRow
import com.tanvrit.ui.theme.TanvritDesignSystem

private val ENTITY_TYPES = listOf("", "VOUCHER", "ACCOUNT", "FISCAL_PERIOD", "BUDGET", "RECONCILIATION")

/** Audit Trail — immutable, hash-chained event log with integrity verify. */
@Composable
fun AuditTrailScreen() {
    val viewModel = rememberViewModel { AuditTrailViewModel() }
    val state by viewModel.state.collectAsState()
    val spacing = TanvritDesignSystem.spacing

    Column(modifier = Modifier.fillMaxSize().padding(spacing.lg)) {
        ScreenHeader(
            title = "Audit Trail",
            subtitle = "${state.totalCount} events",
            actions = {
                Button(onClick = { viewModel.verifyChain() }, enabled = !state.isLoading) {
                    Icon(Icons.Outlined.VerifiedUser, contentDescription = null)
                    Spacer(Modifier.padding(start = spacing.xs))
                    Text("Verify integrity")
                }
            },
        )

        state.error?.let {
            ErrorBanner(message = it, onRetry = { viewModel.refresh() })
            Spacer(Modifier.height(spacing.sm))
        }
        state.chainValid?.let { valid ->
            StatusChip(
                label = if (valid) "Hash chain verified" else "Chain broken at ${state.chainBrokenAt ?: "unknown"}",
                tone = if (valid) ChipTone.Success else ChipTone.Error,
            )
            Spacer(Modifier.height(spacing.sm))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            DropdownPickerField(
                label = "Entity",
                options = ENTITY_TYPES,
                selected = state.entityTypeFilter,
                onSelected = { viewModel.setEntityTypeFilter(it) },
                modifier = Modifier.weight(1f),
                optionLabel = { it.ifBlank { "All entities" } },
            )
            DropdownPickerField(
                label = "Action",
                options = listOf("") + AuditAction.entries.map { it.code },
                selected = state.actionFilter,
                onSelected = { viewModel.setActionFilter(it) },
                modifier = Modifier.weight(1f),
                optionLabel = { it.ifBlank { "All actions" } },
            )
        }
        Spacer(Modifier.height(spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedTextField(
                value = state.dateFrom,
                onValueChange = { viewModel.setDateFrom(it) },
                modifier = Modifier.weight(1f),
                label = { Text("From (YYYY-MM-DD)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.dateTo,
                onValueChange = { viewModel.setDateTo(it) },
                modifier = Modifier.weight(1f),
                label = { Text("To (YYYY-MM-DD)") },
                singleLine = true,
            )
        }
        Spacer(Modifier.height(spacing.lg))

        when {
            state.isLoading -> LoadingPane(Modifier.weight(1f))
            state.entries.isEmpty() ->
                PremiumEmptyState(
                    icon = Icons.AutoMirrored.Outlined.FactCheck,
                    title = "No audit events",
                    description = "Every create/post/reverse/lock in the ledger lands here, hash-chained.",
                )
            else ->
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        AuditRow(entry = entry, onClick = { viewModel.select(entry) })
                    }
                }
        }
    }

    state.selected?.let { entry ->
        AuditDetailSheet(entry = entry, onDismiss = { viewModel.select(null) })
    }
}

@Composable
private fun AuditRow(
    entry: AuditLogEntry,
    onClick: () -> Unit,
) {
    PremiumListRow(
        title = "${entry.entityType} ${entry.entityId.takeLast(6).ifBlank { "—" }}",
        subtitle = "${entry.action.code} · by ${entry.userId.ifBlank { "system" }}",
        supporting = entry.createdAt,
        leading = {
            StatusChip(
                label = entry.action.code,
                tone =
                    when (entry.action) {
                        AuditAction.POST, AuditAction.CREATE -> ChipTone.Success
                        AuditAction.REVERSE, AuditAction.DELETE -> ChipTone.Error
                        AuditAction.LOCK, AuditAction.CLOSE -> ChipTone.Warning
                        else -> ChipTone.Neutral
                    },
            )
        },
        trailing = {
            Text(
                text = "#${entry.hash.takeLast(6)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = onClick,
        isStandalone = false,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditDetailSheet(
    entry: AuditLogEntry,
    onDismiss: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    GlassSheet(onDismiss = onDismiss) {
        Text(
            text = "${entry.action.code} — ${entry.entityType}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = "Entity ${entry.entityId} · by ${entry.userId.ifBlank { "system" }} at ${entry.createdAt}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.lg))
        Text("Before", style = MaterialTheme.typography.labelLarge)
        Text(
            text = entry.beforeState ?: "(no prior state)",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
        Spacer(Modifier.height(spacing.sm))
        Text("After", style = MaterialTheme.typography.labelLarge)
        Text(
            text = entry.afterState ?: "(unchanged)",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
        Spacer(Modifier.height(spacing.lg))
        Text("Hash chain", style = MaterialTheme.typography.labelLarge)
        Text(
            text = "prev ${entry.prevHash.ifBlank { "—" }}\nhash ${entry.hash.ifBlank { "—" }}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}
