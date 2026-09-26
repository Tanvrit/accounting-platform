package com.tanvrit.accounting.screens.dunning

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.tanvrit.accounting.screens.common.ChipTone
import com.tanvrit.accounting.screens.common.ErrorBanner
import com.tanvrit.accounting.screens.common.LoadingPane
import com.tanvrit.accounting.screens.common.MoneyText
import com.tanvrit.accounting.screens.common.ScreenHeader
import com.tanvrit.accounting.screens.common.StatusChip
import com.tanvrit.core.feature.money.Money
import com.tanvrit.ui.component.lifecycle.rememberViewModel
import com.tanvrit.ui.component.premium.GlassSheet
import com.tanvrit.ui.component.premium.PremiumEmptyState
import com.tanvrit.ui.component.premium.PremiumListRow
import com.tanvrit.ui.theme.TanvritDesignSystem

/** Honesty footnote shown wherever full invoice values are presented as "outstanding". */
private const val NETTING_FOOTNOTE =
    "Amounts are full invoice values marked OPEN — payments, credit notes and reversals are not netted " +
        "(allocation sync pending server-side)."

/**
 * Receivables · Payment reminders (roadmap feature #9): the aging strip of
 * unpaid sale invoices per customer, and a locally generated reminder letter
 * per party (copy / mailto — never a fake send).
 */
@Composable
fun DunningScreen() {
    val viewModel = rememberViewModel { DunningViewModel() }
    val state by viewModel.state.collectAsState()
    val spacing = TanvritDesignSystem.spacing

    Column(modifier = Modifier.fillMaxSize().padding(spacing.lg)) {
        ScreenHeader(
            title = "Receivables · Reminders",
            subtitle = "Aging of unpaid sale invoices, per customer",
            actions = {
                OutlinedButton(onClick = { viewModel.refresh() }, enabled = !state.isLoading) { Text("Refresh") }
            },
        )

        state.error?.let {
            ErrorBanner(message = it, onRetry = { viewModel.refresh() })
            Spacer(Modifier.height(spacing.sm))
        }

        when {
            state.businessId.isBlank() -> NoBusinessPane()
            state.isLoading && state.parties.isEmpty() -> LoadingPane(Modifier.weight(1f))
            state.isEmpty -> {
                PremiumEmptyState(
                    icon = Icons.Outlined.MarkEmailUnread,
                    title = "No outstanding receivables",
                    description = "No posted sale invoices with a receivable leg — nothing to remind anyone about.",
                )
            }
            else -> DunningBody(state = state, viewModel = viewModel)
        }
    }

    val reminderParty = state.reminderParty
    if (reminderParty != null) {
        ReminderLetterSheet(
            state = state,
            party = reminderParty,
            onTone = viewModel::selectTone,
            onNotice = viewModel::setNotice,
            onDismiss = viewModel::closeReminder,
        )
    }
}

@Composable
private fun NoBusinessPane() {
    PremiumEmptyState(
        icon = Icons.Outlined.AccountBalanceWallet,
        title = "No business selected",
        description = "Select or create a business workspace to review receivables and send payment reminders.",
    )
}

@Composable
private fun DunningBody(
    state: DunningUiState,
    viewModel: DunningViewModel,
) {
    val spacing = TanvritDesignSystem.spacing

    Column(modifier = Modifier.fillMaxSize()) {
        BucketStrip(parties = state.parties)
        Spacer(Modifier.height(spacing.sm))
        Text(
            text = NETTING_FOOTNOTE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.md))

        FilterRow(state = state, viewModel = viewModel)
        Spacer(Modifier.height(spacing.md))

        val visible = state.visibleParties
        if (visible.isEmpty()) {
            PremiumEmptyState(
                icon = Icons.Outlined.Search,
                title = "No parties match",
                description = "Adjust the search, minimum amount or bucket filter.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                items(visible, key = { it.partyAccountId }) { party ->
                    PartyRow(party = party, onReminder = { viewModel.openReminder(party.partyAccountId) })
                }
            }
        }
    }
}

/** Five-card summary: total + one card per bucket — count and MoneyText sum each. */
@Composable
private fun BucketStrip(parties: List<PartyAging>) {
    val spacing = TanvritDesignSystem.spacing
    val summaries = AgingMath.summaries(parties)
    val total = parties.fold(Money.ZERO) { acc, party -> acc + party.total }
    val count = parties.sumOf { it.invoiceCount }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        BucketCard(label = "Total", count = count, money = total, tone = ChipTone.Primary, modifier = Modifier.weight(1f))
        summaries.forEach { summary ->
            BucketCard(
                label = summary.bucket.label,
                count = summary.count,
                money = summary.total,
                tone = bucketTone(summary.bucket),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BucketCard(
    label: String,
    count: Int,
    money: Money,
    tone: ChipTone,
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
            StatusChip(label = label, tone = tone)
            Text(
                text = "$count invoice(s)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyText(money = money, bold = true)
        }
    }
}

private fun bucketTone(bucket: AgingBucket): ChipTone =
    when (bucket) {
        AgingBucket.DAYS_0_30 -> ChipTone.Info
        AgingBucket.DAYS_31_60 -> ChipTone.Neutral
        AgingBucket.DAYS_61_90 -> ChipTone.Warning
        AgingBucket.DAYS_91_PLUS -> ChipTone.Error
    }

@Composable
private fun FilterRow(
    state: DunningUiState,
    viewModel: DunningViewModel,
) {
    val spacing = TanvritDesignSystem.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::setSearchQuery,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search party or code") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.minAmountText,
            onValueChange = viewModel::setMinAmount,
            modifier = Modifier.weight(0.4f),
            label = { Text("Min outstanding") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            value = state.referenceDate,
            onValueChange = viewModel::setReferenceDate,
            modifier = Modifier.weight(0.5f),
            label = { Text("As of (YYYY-MM-DD)") },
            singleLine = true,
        )
    }
    Spacer(Modifier.height(spacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        FilterChip(
            selected = state.bucketFilter == null,
            onClick = { viewModel.setBucketFilter(null) },
            label = { Text("All buckets") },
        )
        AgingBucket.entries.forEach { bucket ->
            FilterChip(
                selected = state.bucketFilter == bucket,
                onClick = { viewModel.setBucketFilter(bucket) },
                label = { Text("${bucket.label} d") },
            )
        }
    }
}

/** One party: contact bits, per-bucket split chips and the emphasized total. */
@Composable
private fun PartyRow(
    party: PartyAging,
    onReminder: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    val subtitle =
        buildString {
            if (party.accountCode.isNotBlank()) append(party.accountCode).append(" · ")
            append(party.invoiceCount).append(" invoice(s)")
            if (party.lastInvoiceDate.isNotBlank()) append(" · last ").append(party.lastInvoiceDate)
        }
    val contact =
        listOf(party.email, party.phone, party.gstin.takeIf { it.isNotBlank() }?.let { "GSTIN $it" })
            .mapNotNull { it?.takeIf(String::isNotBlank) }
            .joinToString(" · ")
            .ifBlank { null }

    PremiumListRow(
        title = party.name,
        subtitle = subtitle,
        supporting = contact,
        trailing = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    AgingBucket.entries.forEach { bucket ->
                        val bucketCount = party.bucketCount(bucket)
                        if (bucketCount > 0) {
                            StatusChip(
                                label = "${bucket.label}·$bucketCount",
                                tone = bucketTone(bucket),
                            )
                        }
                    }
                }
                MoneyText(money = party.total, bold = true)
                OutlinedButton(onClick = onReminder) { Text("Reminder letter") }
            }
        },
        onClick = onReminder,
    )
}

/** The generated letter in a sheet — tone switch, copy, and mailto (if the platform opens URIs). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderLetterSheet(
    state: DunningUiState,
    party: PartyAging,
    onTone: (ReminderTone) -> Unit,
    onNotice: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = TanvritDesignSystem.spacing
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val input =
        LetterInput(
            businessName = state.businessName,
            businessGstin = state.letterheadGstin,
            asOfDate = state.referenceDate,
            party = party,
        )
    val letter = ReminderLetter.generate(input, state.reminderTone)

    GlassSheet(onDismiss = onDismiss) {
        Text(
            text = "Reminder letter — ${party.name}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = ReminderLetter.subjectFor(input, state.reminderTone),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.sm))

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            ReminderTone.entries.forEach { tone ->
                FilterChip(
                    selected = state.reminderTone == tone,
                    onClick = { onTone(tone) },
                    label = { Text(tone.label) },
                )
            }
        }
        state.notice?.let {
            Spacer(Modifier.height(spacing.xs))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(spacing.md))

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            shape = TanvritDesignSystem.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(spacing.md),
            ) {
                Text(
                    text = letter,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Spacer(Modifier.height(spacing.md))

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(letter))
                    onNotice("Letter copied to clipboard")
                },
            ) { Text("Copy letter") }
            OutlinedButton(
                onClick = {
                    onNotice(null)
                    runCatching { uriHandler.openUri(ReminderLetter.mailtoFor(input, state.reminderTone)) }
                        .onFailure { onNotice("No mail client handled it — the letter is ready to paste anywhere.") }
                },
            ) { Text("Open e-mail") }
        }
        if (party.email.isBlank()) {
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = "No e-mail on this account — the composer opens without a recipient; add one there.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
