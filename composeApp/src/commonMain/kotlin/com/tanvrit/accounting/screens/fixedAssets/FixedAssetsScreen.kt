package com.tanvrit.accounting.screens.fixedAssets

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Domain
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.tanvrit.accounting.data.FixedAsset
import com.tanvrit.accounting.screens.common.ChipTone
import com.tanvrit.accounting.screens.common.ConfirmDialog
import com.tanvrit.accounting.screens.common.DropdownPickerField
import com.tanvrit.accounting.screens.common.ErrorBanner
import com.tanvrit.accounting.screens.common.LoadingPane
import com.tanvrit.accounting.screens.common.MoneyText
import com.tanvrit.accounting.screens.common.ScreenHeader
import com.tanvrit.accounting.screens.common.StatusChip
import com.tanvrit.accounting.screens.common.parseMoneyInput
import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.money.Money
import com.tanvrit.ui.component.lifecycle.rememberViewModel
import com.tanvrit.ui.component.premium.GlassSheet
import com.tanvrit.ui.component.premium.PremiumEmptyState
import com.tanvrit.ui.theme.TanvritDesignSystem

/**
 * Fixed assets (roadmap #6) — asset register + monthly depreciation charged as
 * DRAFT journal vouchers (Approvals queue reviews & posts).
 */
@Composable
fun FixedAssetsScreen() {
    val viewModel = rememberViewModel { FixedAssetsViewModel() }
    val state by viewModel.state.collectAsState()
    val spacing = TanvritDesignSystem.spacing

    Column(modifier = Modifier.fillMaxSize().padding(spacing.lg)) {
        ScreenHeader(
            title = "Fixed assets",
            subtitle = "Asset register with monthly depreciation as draft journals",
            actions = { Button(onClick = viewModel::openCreate) { Text("Add asset") } },
        )

        state.error?.let {
            ErrorBanner(message = it, onRetry = { viewModel.clearError() })
            Spacer(Modifier.height(spacing.sm))
        }
        state.notice?.let {
            StatusChip(
                label = it,
                tone =
                    if (it.startsWith("Charged") ||
                        it.startsWith("Saved") ||
                        it.startsWith("Deleted")
                    ) {
                        ChipTone.Success
                    } else {
                        ChipTone.Error
                    },
            )
            Spacer(Modifier.height(spacing.sm))
        }

        when {
            state.businessId.isBlank() ->
                PremiumEmptyState(
                    icon = Icons.Outlined.Domain,
                    title = "No business selected",
                    description = "Pick a business to manage its fixed-asset register.",
                )
            state.isLoading && state.assets.isEmpty() -> LoadingPane(Modifier.weight(1f))
            state.assets.isEmpty() ->
                PremiumEmptyState(
                    icon = Icons.Outlined.Domain,
                    title = "No fixed assets",
                    description = "Add machinery, vehicles, or equipment — depreciation drafts itself monthly into the journal.",
                )
            else ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    items(state.assets, key = { it.id }) { asset ->
                        AssetRow(
                            asset = asset,
                            nbv = state.nbvById[asset.id],
                            outcome = state.lastOutcome[asset.id],
                            onCharge = { viewModel.askCharge(asset) },
                            onEdit = { viewModel.openEdit(asset) },
                            onDelete = { viewModel.askDelete(asset) },
                        )
                    }
                }
        }
    }

    if (state.showEditor) {
        FixedAssetEditorSheet(
            state = state,
            onChange = viewModel::updateForm,
            onSave = viewModel::saveForm,
            onDismiss = viewModel::closeEditor,
        )
    }

    state.pendingDelete?.let { target ->
        ConfirmDialog(
            title = "Delete asset",
            message = "Delete “${target.name}”? Already-drafted depreciation vouchers stay in the journal.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }

    state.pendingCharge?.let { target ->
        ConfirmDialog(
            title = "Charge depreciation",
            message = "Draft a journal voucher charging this month's depreciation on “${target.name}”?",
            confirmLabel = "Draft voucher",
            onConfirm = viewModel::confirmCharge,
            onDismiss = viewModel::dismissCharge,
        )
    }
}

@Composable
private fun AssetRow(
    asset: FixedAsset,
    nbv: Long?,
    outcome: String?,
    onCharge: () -> Unit,
    onEdit: () -> Unit,
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
            Text(asset.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(label = asset.method, tone = ChipTone.Info)
                StatusChip(label = "${asset.ratePercent}% p.a.", tone = ChipTone.Neutral)
                StatusChip(
                    label = "${asset.postedRows.size} charged",
                    tone = if (asset.postedRows.isEmpty()) ChipTone.Neutral else ChipTone.Success,
                )
            }
            Spacer(Modifier.height(spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cost", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(money = parseMoneyInput(asset.cost))
                    Spacer(Modifier.height(spacing.xxs))
                    Text("Net book value", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(
                        money = moneyOfMinor(nbv ?: 0L),
                        bold = true,
                    )
                }
                OutlinedButton(onClick = onCharge) { Text("Charge period") }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
            }
            outcome?.let {
                Spacer(Modifier.height(spacing.xs))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FixedAssetEditorSheet(
    state: FixedAssetsUiState,
    onChange: ((FixedAssetForm) -> FixedAssetForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    val form = state.form
    GlassSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                if (form.id.isBlank()) "New fixed asset" else "Edit fixed asset",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(spacing.md))

            OutlinedTextField(
                value = form.name,
                onValueChange = { value -> onChange { it.copy(name = value) } },
                label = { Text("Asset name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(spacing.sm))

            AccountPickerField(label = "Asset account", selectedId = form.assetAccountId, accounts = state.accounts) { id ->
                onChange { it.copy(assetAccountId = id) }
            }
            AccountPickerField(
                label = "Depreciation expense account",
                selectedId = form.depExpenseAccountId,
                accounts = state.accounts,
            ) { id ->
                onChange { it.copy(depExpenseAccountId = id) }
            }
            AccountPickerField(
                label = "Accumulated depreciation account",
                selectedId = form.accumDepAccountId,
                accounts = state.accounts,
            ) { id ->
                onChange { it.copy(accumDepAccountId = id) }
            }
            Spacer(Modifier.height(spacing.sm))

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                OutlinedTextField(
                    value = form.cost,
                    onValueChange = { value -> onChange { it.copy(cost = value) } },
                    label = { Text("Cost") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = form.salvage,
                    onValueChange = { value -> onChange { it.copy(salvage = value) } },
                    label = { Text("Salvage") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(spacing.sm))

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                DropdownPickerField(
                    label = "Method",
                    options = DepreciationMethod.entries.toList(),
                    selected = form.method,
                    onSelected = { method -> onChange { it.copy(method = method) } },
                    modifier = Modifier.weight(1f),
                    optionLabel = { it.label },
                )
                OutlinedTextField(
                    value = form.ratePercent,
                    onValueChange = { value -> onChange { it.copy(ratePercent = value) } },
                    label = { Text("Rate % p.a.") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(spacing.sm))

            OutlinedTextField(
                value = form.startDate,
                onValueChange = { value -> onChange { it.copy(startDate = value) } },
                label = { Text("Put-to-use date (yyyy-MM-dd)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(spacing.lg))

            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save asset") }
        }
    }
}

@Composable
private fun AccountPickerField(
    label: String,
    selectedId: String,
    accounts: List<Account>,
    onSelected: (String) -> Unit,
) {
    DropdownPickerField(
        label = label,
        options = accounts,
        selected = accounts.firstOrNull { it.id == selectedId },
        onSelected = { account -> onSelected(account.id) },
        modifier = Modifier.fillMaxWidth(),
        optionLabel = { "${it.accountCode} · ${it.name}" },
    )
    Spacer(Modifier.height(com.tanvrit.ui.theme.TanvritDesignSystem.spacing.xs))
}

private fun moneyOfMinor(minorUnits: Long): Money = Money.fromDouble(minorUnits / 100.0)
