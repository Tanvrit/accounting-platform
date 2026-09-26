package com.tanvrit.accounting.screens.multiCurrency

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
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
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
import com.tanvrit.accounting.screens.common.formatMoney
import com.tanvrit.core.feature.accounting.model.FxRate
import com.tanvrit.core.feature.accounting.model.RevaluationLineItem
import com.tanvrit.core.feature.accounting.model.RevaluationResult
import com.tanvrit.core.feature.money.Money
import com.tanvrit.ui.component.lifecycle.rememberViewModel
import com.tanvrit.ui.component.premium.PremiumEmptyState
import com.tanvrit.ui.component.premium.PremiumListRow
import com.tanvrit.ui.theme.TanvritDesignSystem

/**
 * Multi-currency (roadmap feature #3): FX rates vs the base currency with
 * inline editing, plus the server-side period revaluation run and its
 * returned unrealized gain/loss lines — shown verbatim, never synthesized.
 */
@Composable
fun MultiCurrencyScreen() {
    val viewModel = rememberViewModel { MultiCurrencyViewModel() }
    val state by viewModel.state.collectAsState()
    val spacing = TanvritDesignSystem.spacing

    Column(modifier = Modifier.fillMaxSize().padding(spacing.lg)) {
        ScreenHeader(
            title = "Multi-currency",
            subtitle = "FX rates vs ${state.baseCurrency} · period revaluation",
            actions = {
                OutlinedButton(onClick = { viewModel.refresh() }, enabled = !state.isLoading) { Text("Refresh") }
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
            state.isLoading && state.rates.isEmpty() -> LoadingPane(Modifier.weight(1f))
            else -> MultiCurrencyBody(state = state, viewModel = viewModel)
        }
    }

    if (state.revalConfirmArmed) {
        val periodName = state.openPeriods.firstOrNull { it.id == state.revalPeriodId }?.name ?: state.revalPeriodId
        ConfirmDialog(
            title = "Run revaluation?",
            message =
                "Revalue open foreign-currency balances in \"$periodName\" as of today, against the current " +
                    "${state.baseCurrency} rates. Runs in DRAFT mode — unrealized gain/loss lines come back for " +
                    "review, nothing is posted to the ledger.",
            confirmLabel = "Run revaluation",
            onConfirm = viewModel::confirmRevaluation,
            onDismiss = viewModel::disarmRevaluation,
        )
    }
}

@Composable
private fun NoBusinessPane() {
    PremiumEmptyState(
        icon = Icons.Outlined.AccountBalanceWallet,
        title = "No business selected",
        description = "Select or create a business workspace to manage FX rates and run revaluations.",
    )
}

@Composable
private fun MultiCurrencyBody(
    state: MultiCurrencyUiState,
    viewModel: MultiCurrencyViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item(key = "rates-note") {
            Text(
                text = "Rates are tenant-wide quotes per currency pair — source and date are per row.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.isEmpty) {
            item(key = "rates-empty") {
                PremiumEmptyState(
                    icon = Icons.Outlined.CurrencyExchange,
                    title = "No FX rates vs ${state.baseCurrency}",
                    description =
                        "Add a rate below, or enable Settings ▸ System ▸ auto-fetch FX rates to pull " +
                            "the latest quotes.",
                )
            }
        } else {
            items(state.rates, key = { "rate-${it.id}" }) { rate ->
                FxRateRow(
                    rate = rate,
                    text = state.rateEdits[rate.id] ?: FxFormat.formatRate(rate.rate.toString()),
                    saving = rate.id in state.savingRateIds,
                    error = state.rowErrors[rate.id],
                    onTextChange = { viewModel.onRateTextChange(rate.id, it) },
                    onSave = { viewModel.saveRate(rate) },
                )
            }
        }
        item(key = "add-rate") {
            AddRateCard(state = state, viewModel = viewModel)
        }
        item(key = "reval-card") {
            RevaluationCard(state = state, viewModel = viewModel)
        }
        state.revaluation?.let { result ->
            item(key = "reval-summary") {
                RevaluationSummary(result = result, baseCurrency = state.baseCurrency)
            }
            if (result.lineItems.isEmpty()) {
                item(key = "reval-lines-empty") {
                    Text(
                        text = "The server returned no revaluation lines for this period.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(
                    result.lineItems,
                    key = { "line-${it.id.ifBlank { "${it.accountId}-${it.currency}-${it.accountCode}" }}" },
                ) { line ->
                    RevaluationLineRow(line = line, baseCurrency = state.baseCurrency)
                }
            }
        }
    }
}

/** One rate row: pair label, current quote + date/source, inline editor with per-row save + error chip. */
@Composable
private fun FxRateRow(
    rate: FxRate,
    text: String,
    saving: Boolean,
    error: String?,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    PremiumListRow(
        title = rate.counterCurrency,
        subtitle =
            buildString {
                append("1 ${rate.baseCurrency} = ${FxFormat.formatRate(rate.rate.toString())} ${rate.counterCurrency}")
                if (rate.rateDate.isNotBlank()) append(" · as of ${rate.rateDate}")
                if (rate.source.isNotBlank()) append(" · ${rate.source}")
            },
        leading = { StatusChip(label = rate.rateType, tone = ChipTone.Neutral) },
        trailing = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.width(spacing.xxl * 5),
                        label = { Text("Rate") },
                        singleLine = true,
                        enabled = !saving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedButton(onClick = onSave, enabled = !saving && FxFormat.parseRate(text) != null) {
                        Icon(Icons.Outlined.Save, contentDescription = "Save ${rate.counterCurrency} rate")
                    }
                }
                error?.let { StatusChip(label = it, tone = ChipTone.Error) }
            }
        },
    )
}

/** Add-rate editor: curated ISO-4217 counters that do not already have a rate for this base. */
@Composable
private fun AddRateCard(
    state: MultiCurrencyUiState,
    viewModel: MultiCurrencyViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    val options =
        MultiCurrencyViewModel.ADD_CURRENCIES
            .filter { code ->
                code != state.baseCurrency && state.rates.none { it.counterCurrency.equals(code, ignoreCase = true) }
            }
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
            Text("Add rate", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DropdownPickerField(
                    label = "Currency",
                    options = options,
                    selected = state.addCurrency.takeIf { it.isNotBlank() },
                    onSelected = viewModel::onAddCurrencyChange,
                    modifier = Modifier.weight(1f),
                    enabled = !state.addSaving,
                )
                OutlinedTextField(
                    value = state.addRateText,
                    onValueChange = viewModel::onAddRateTextChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Rate (1 ${state.baseCurrency} = …)") },
                    singleLine = true,
                    enabled = !state.addSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Button(
                    onClick = viewModel::addRate,
                    enabled =
                        !state.addSaving &&
                            state.addCurrency.isNotBlank() &&
                            FxFormat.parseRate(state.addRateText) != null,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("Add")
                }
            }
            state.rowErrors[MultiCurrencyViewModel.NEW_ROW_KEY]?.let {
                StatusChip(label = it, tone = ChipTone.Error)
            }
        }
    }
}

/** Revaluation run: OPEN-period picker + confirmed draft-only run. */
@Composable
private fun RevaluationCard(
    state: MultiCurrencyUiState,
    viewModel: MultiCurrencyViewModel,
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
            Text("Period revaluation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Computes unrealized gain/loss on open foreign-currency balances for an OPEN fiscal period. " +
                    "Runs on the server in DRAFT mode — review the returned lines before anything is posted.",
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
                        selected = state.openPeriods.firstOrNull { it.id == state.revalPeriodId },
                        onSelected = { viewModel.onRevalPeriodChange(it.id) },
                        modifier = Modifier.weight(1f),
                        optionLabel = { it.name },
                        enabled = !state.revalRunning,
                    )
                    Button(
                        onClick = viewModel::armRevaluation,
                        enabled = state.revalPeriodId.isNotBlank() && !state.revalRunning && !state.isLoading,
                    ) { Text("Run revaluation") }
                }
            }
        }
    }
}

/** Result summary as the server returned it: status + gain/loss/net totals. */
@Composable
private fun RevaluationSummary(
    result: RevaluationResult,
    baseCurrency: String,
) {
    val spacing = TanvritDesignSystem.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Revaluation result", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            StatusChip(
                label = result.status,
                tone = if (result.status == "POSTED") ChipTone.Success else ChipTone.Neutral,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            SummaryCard("Total gain", result.totalUnrealizedGain, baseCurrency, Modifier.weight(1f))
            SummaryCard("Total loss", result.totalUnrealizedLoss, baseCurrency, Modifier.weight(1f))
            SummaryCard("Net gain/loss", result.netUnrealizedGainLoss, baseCurrency, Modifier.weight(1f))
        }
        result.postedVoucherId?.let {
            Text(
                text = "Posted as voucher $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    money: Money,
    currencyCode: String,
    modifier: Modifier = Modifier,
) {
    val spacing = TanvritDesignSystem.spacing
    Surface(
        modifier = modifier,
        shape = TanvritDesignSystem.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = TanvritDesignSystem.elevation.e1,
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyText(money = money, currencyCode = currencyCode, bold = true, colorizeSign = true)
        }
    }
}

/**
 * One returned revaluation line. The model carries a single `fxRate` (the
 * revaluation rate) — no "old" rate is returned, so none is shown.
 */
@Composable
private fun RevaluationLineRow(
    line: RevaluationLineItem,
    baseCurrency: String,
) {
    PremiumListRow(
        title = if (line.accountCode.isNotBlank()) "${line.accountName} (${line.accountCode})" else line.accountName,
        subtitle =
            buildString {
                append(formatMoney(line.originalAmount, line.currency))
                if (line.currency.isNotBlank()) append(" ${line.currency}")
                append(" @ ${FxFormat.formatRate(line.fxRate.toString())} → ")
                append(formatMoney(line.revaluedAmount, baseCurrency))
            },
        trailing = {
            MoneyText(
                money = line.unrealizedGainLoss,
                currencyCode = baseCurrency,
                bold = true,
                colorizeSign = true,
            )
        },
    )
}
