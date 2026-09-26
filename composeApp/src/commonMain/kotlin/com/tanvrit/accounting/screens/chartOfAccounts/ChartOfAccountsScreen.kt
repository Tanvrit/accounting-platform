package com.tanvrit.accounting.screens.chartOfAccounts

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
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.tanvrit.core.feature.accounting.model.Account
import com.tanvrit.core.feature.accounting.model.AccountType
import com.tanvrit.ui.component.lifecycle.rememberViewModel
import com.tanvrit.ui.component.premium.GlassSheet
import com.tanvrit.ui.component.premium.PremiumEmptyState
import com.tanvrit.ui.component.premium.PremiumListRow
import com.tanvrit.ui.theme.TanvritDesignSystem

/** Chart of Accounts — searchable, hierarchical tree with create/edit sheet. */
@Composable
fun ChartOfAccountsScreen(
    // WIRING(report→ledger): pass nav to AccountLedgerRoute — e.g.
    // ChartOfAccountsScreen(onViewLedger = { id -> navController.navigate(AppRoute.AccountLedger(id)) })
    onViewLedger: (accountId: String) -> Unit = {},
) {
    val viewModel = rememberViewModel { ChartOfAccountsViewModel() }
    val state by viewModel.state.collectAsState()
    val spacing = TanvritDesignSystem.spacing

    Column(modifier = Modifier.fillMaxSize().padding(spacing.lg)) {
        ScreenHeader(
            title = "Chart of Accounts",
            subtitle = "${state.accounts.size} accounts",
            actions = {
                Button(onClick = { viewModel.openCreate() }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(spacing.xs))
                    Text("New account")
                }
            },
        )

        state.error?.let {
            ErrorBanner(message = it, onRetry = { viewModel.refresh() })
            Spacer(Modifier.height(spacing.lg))
        }
        state.notice?.let {
            SuccessNotice(it, onDismiss = { viewModel.clearNotice() })
        }

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.onSearchChange(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by name or code") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
        )
        Spacer(Modifier.height(spacing.sm))

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            FilterChip(
                selected = state.typeFilter == null,
                onClick = { viewModel.onTypeFilterChange(null) },
                label = { Text("All") },
            )
            listOf(AccountType.ASSET, AccountType.LIABILITY, AccountType.EQUITY, AccountType.REVENUE, AccountType.EXPENSE)
                .forEach { type ->
                    FilterChip(
                        selected = state.typeFilter == type,
                        onClick = { viewModel.onTypeFilterChange(if (state.typeFilter == type) null else type) },
                        label = { Text(type.code) },
                    )
                }
        }
        Spacer(Modifier.height(spacing.lg))

        if (state.isLoading) {
            LoadingPane(Modifier.weight(1f))
        } else {
            val visible = filterAndTree(state)
            if (visible.isEmpty()) {
                PremiumEmptyState(
                    icon = Icons.Outlined.AccountTree,
                    title = "No accounts",
                    description =
                        if (state.searchQuery.isBlank()) {
                            "Create your first account to start posting vouchers."
                        } else {
                            "No accounts match \"${state.searchQuery}\"."
                        },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    items(visible, key = { it.account.id }) { node ->
                        AccountTreeRow(
                            node = node,
                            expanded = node.account.id in state.expanded,
                            onToggle = { viewModel.toggleExpanded(node.account.id) },
                            onEdit = { viewModel.openEdit(node.account) },
                            onAddChild = { viewModel.openCreate(node.account.id) },
                            onViewLedger = { onViewLedger(node.account.id) },
                        )
                    }
                }
            }
        }
    }

    if (state.editor.visible) {
        AccountEditorSheet(
            state = state,
            onChange = viewModel::updateEditor,
            onSave = viewModel::saveAccount,
            onDismiss = viewModel::closeEditor,
        )
    }
}

private data class AccountTreeNode(
    val account: Account,
    val depth: Int,
    val hasChildren: Boolean,
)

/** Builds a render list grouped by type, parents before children, honouring the search/type filters. */
private fun filterAndTree(state: ChartOfAccountsUiState): List<AccountTreeNode> {
    val query = state.searchQuery.trim().lowercase()
    val matchesQuery: (Account) -> Boolean = {
        query.isBlank() || it.name.lowercase().contains(query) || it.accountCode.lowercase().contains(query)
    }
    val scoped =
        state.accounts
            .filter { state.typeFilter == null || it.type == state.typeFilter }
            .filter(matchesQuery)
    val byParent = scoped.groupBy { it.parentAccountId ?: "" }
    val roots = byParent[""].orEmpty() + scoped.filter { (it.parentAccountId ?: "") !in scoped.map { a -> a.id } }
    val out = mutableListOf<AccountTreeNode>()

    fun walk(
        account: Account,
        depth: Int,
    ) {
        val children = byParent[account.id].orEmpty()
        out += AccountTreeNode(account = account, depth = depth, hasChildren = children.isNotEmpty())
        children.sortedBy { it.accountCode }.forEach { walk(it, depth + 1) }
    }
    roots.sortedBy { it.accountCode }.forEach { walk(it, 0) }
    return out
}

@Composable
private fun AccountTreeRow(
    node: AccountTreeNode,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onAddChild: () -> Unit,
    onViewLedger: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    Row {
        Spacer(Modifier.width(spacing.xxl * node.depth))
        Column(modifier = Modifier.weight(1f)) {
            PremiumListRow(
                title = "${node.account.accountCode} — ${node.account.name}",
                subtitle = node.account.type.code + if (node.account.description.isNotBlank()) " · ${node.account.description}" else "",
                leading = {
                    if (node.hasChildren) {
                        IconButton(onClick = onToggle) {
                            Icon(
                                imageVector = if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                            )
                        }
                    }
                },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoneyText(money = node.account.currentBalance, currencyCode = node.account.currencyCode, bold = true)
                        StatusChip(
                            label = if (node.account.isActive) "Active" else "Inactive",
                            tone = if (node.account.isActive) ChipTone.Success else ChipTone.Neutral,
                            modifier = Modifier.padding(start = spacing.sm),
                        )
                        IconButton(onClick = onViewLedger) { Text("Ledger") }
                        IconButton(onClick = onEdit) { Text("Edit") }
                    }
                },
                onClick = onEdit,
            )
        }
    }

    // quick add-child affordance sits behind long-discoverability — keep visible only on expanded parents
    if (node.hasChildren && expanded) {
        Row {
            Spacer(Modifier.width(spacing.xxl * (node.depth + 1)))
            IconButton(onClick = onAddChild) {
                Icon(Icons.Outlined.Add, contentDescription = "Add child account")
            }
            Text(
                text = "Add child account",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = spacing.md),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountEditorSheet(
    state: ChartOfAccountsUiState,
    onChange: ((AccountEditorState) -> AccountEditorState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val editor = state.editor
    val spacing = TanvritDesignSystem.spacing
    GlassSheet(onDismiss = onDismiss) {
        Text(
            text = if (editor.id.isBlank()) "New account" else "Edit account",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(spacing.lg))

        OutlinedTextField(
            value = editor.accountCode,
            onValueChange = { v -> onChange { it.copy(accountCode = v) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Account code") },
            singleLine = true,
        )
        Spacer(Modifier.height(spacing.sm))
        OutlinedTextField(
            value = editor.name,
            onValueChange = { v -> onChange { it.copy(name = v) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Account name") },
            singleLine = true,
        )
        Spacer(Modifier.height(spacing.sm))

        DropdownPickerField(
            label = "Type",
            options = AccountType.entries.toList(),
            selected = editor.type,
            onSelected = { type -> onChange { it.copy(type = type) } },
            modifier = Modifier.fillMaxWidth(),
            optionLabel = { it.code },
        )
        Spacer(Modifier.height(spacing.sm))

        DropdownPickerField(
            label = "Parent account",
            options = listOf<Account?>(null) + state.accounts.filter { it.id != editor.id },
            selected = state.accounts.firstOrNull { it.id == editor.parentAccountId },
            onSelected = { parent -> onChange { it.copy(parentAccountId = parent?.id ?: "") } },
            modifier = Modifier.fillMaxWidth(),
            optionLabel = { it?.let { a -> "${a.accountCode} — ${a.name}" } ?: "None (top level)" },
        )
        Spacer(Modifier.height(spacing.sm))

        OutlinedTextField(
            value = editor.openingBalance,
            onValueChange = { v -> onChange { it.copy(openingBalance = v) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Opening balance") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            enabled = editor.id.isBlank(),
        )
        Spacer(Modifier.height(spacing.sm))
        OutlinedTextField(
            value = editor.description,
            onValueChange = { v -> onChange { it.copy(description = v) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Description") },
        )
        Spacer(Modifier.height(spacing.xl))

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                Text(if (editor.id.isBlank()) "Create account" else "Save changes")
            }
        }
    }
}

@Composable
private fun SuccessNotice(
    message: String,
    onDismiss: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = TanvritDesignSystem.shapes.medium,
        color = TanvritDesignSystem.colors.successContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, color = TanvritDesignSystem.colors.success, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Dismiss") }
        }
    }
    Spacer(Modifier.height(spacing.lg))
}
