# Tanvrit Accounting — Feature Roadmap (Top 20)

> **Thesis.** KMP everywhere (Android/iOS/Desktop/Web from one codebase) + offline-first
> storage + deep Indian compliance (GST e-invoice, e-way bill, TDS) = the moat.
> Everyone else (Tally, Zoho Books, QBO, Xero, Vyapar) forces a trade — Tally is fast
> but desktop-bound and cloud-weak; Zoho/QBO are cloud-strong but thin offline and
> India-compliance-shallow vs Tally. We win by being Tally-fast, Zoho-cloud, and
> offline-real — on every device.
>
> Verified against the published SDK @3.0.7 surface (see § SDK reality). Marking:
> **CLIENT-ONLY** = buildable today in `composeApp` against existing SDK 3.0.7
> networks/models; **SDK-SERVER** = requires new server endpoints/SDK releases —
> those ship as honest stub UI + plan, never fake-backend calls.

| # | Feature | What | Why it wins | Depends | Effort | Benchmark |
|---|---------|------|-------------|---------|--------|-----------|
| 1 | Drill-down-anywhere reporting | TB row → account ledger → voucher detail, 2 taps | Tally's #1 moat; Zoho/QBO bury it 4-5 clicks deep | CLIENT-ONLY | M | Tally |
| 2 | Recurring vouchers | Local templates (rent, salary, EMI) auto-draft vouchers on schedule | Table-stakes automation; works offline | CLIENT-ONLY | M | Zoho/QBO |
| 3 | Multi-currency mode | FX rates, per-voucher currency, revaluation run view | Exporters/importers — Zoho/Xero territory | CLIENT-ONLY (MultiCurrencyNetwork + FxRate exist) | M | Zoho/Xero |
| 4 | Group consolidation viewer | Consolidated TB/P&L across businesses, eliminations display | Multi-entity SMBs; Tally group-company parity | CLIENT-ONLY (ConsolidationNetwork exists) | M | Tally |
| 5 | Opening-balance + CSV import wizard | Bulk-import chart of accounts w/ opening balances from CSV/TSV | Makes migration from Tally/Excel real in minutes | CLIENT-ONLY (AccountNetwork.importAccountsAsync) | M | Zoho migration doc, Tally XML |
| 6 | Fixed-assets & depreciation register | Asset register, SLM/WDV depreciation, periodic depreciation JV drafts | Companies Act need; Zoho FA parity | CLIENT-ONLY (draft journals via VoucherNetwork) | M | Zoho "Fixed assets" |
| 7 | Project / cost-center tags | Tag voucher lines to cost centers; filtered P&L per tag | Divisional reporting SMBs demand | CLIENT-ONLY | M | Zoho reporting tags |
| 8 | ITC workspace (GSTR-2B-style) | Import supplier 2B JSON/CSV manually, match against purchase register, action IM/REJECT/PENDING | The daily pain of every Indian accountant — ITC money lost monthly | CLIENT-ONLY (manual import; auto-pull = SDK-SERVER later) | M | Zoho IMS, ClearTax |
| 9 | Payment reminders / dunning | Receivables aging buckets → ready-to-send reminder letters/PDFs | Cash-flow feature SMBs feel daily | CLIENT-ONLY (letters generated locally; WhatsApp send = SDK-SERVER) | M | Zoho reminders |
| 10 | Voucher templates + smart numbering | Save-as-template, per-series numbering presets, quick-entry defaults | Tally voucher-type ergonomics; cuts entry time | CLIENT-ONLY | S | Tally |
| 11 | Approval workflow flags | Draft → verified → posted with approver note on voucher lifecycle | Error control without slowing small teams | CLIENT-ONLY (VoucherStatus already modeled) | S | Zoho approvals |
| 12 | Keyboard-first entry mode | Global hotkeys (Alt+C create account, Enter drill, Esc back) in VoucherEntry + nav | The reason CAs refuse to leave Tally | CLIENT-ONLY (desktop/web) | M | Tally |
| 13 | Voucher attachments | Attach/link receipts & invoices to voucher entries | Audit-readiness; Zoho documents parity | CLIENT-ONLY (Attachment model already exists; upload via sdk/media) | M | Zoho/Zoho Docs |
| 14 | Test safety net | Per-screen VM tests, Koin module check, serializer round-trips | Engineering moat — ship weekly without fear | CLIENT-ONLY | M | (internal) |
| 15 | i18n foundation + Hindi | Extract strings, add `hi` locale, Devanagari-safe fonts | 90% of Indian SMB users prefer Hindi/GU/TA | CLIENT-ONLY (start) | M | Zoho 25 languages |
| 16 | Bank-feed rules engine | Client-side rules over imported CSV statements (narration → ledger suggestions); AA feeds = stub UI + plan | Auto-categorization is the QBO/Xero killer app | CLIENT-ONLY rules + SDK-SERVER feeds | L (split) | QBO bank rules |
| 17 | E-invoice IRN + QR display | Show IRN + signed QR on invoice/voucher print; submission exists (TaxNetwork) | Statutory display format; audit-proof invoices | CLIENT-ONLY display (+ SDK-SERVER IRN push flows) | S | Zoho e-invoice |
| 18 | WhatsApp/SMS payment reminders | Template-based reminder send from dunning list via sdk/communication | India's business language is WhatsApp — 10x response vs email | SDK-SERVER-ready (WhatsApp template/send surface exists in core) | M | Zoho/WhatsApp Pay |
| 19 | Payroll-lite register | Employee salary register + monthly payroll JVs (basic/HRA/deductions/TDS-192B) | TDS-24Q tie-in already shipped; payroll is the biggest attach feature | CLIENT-ONLY register + SDK-SERVER statutory later | L | Tally payroll |
| 20 | Live ledger collaboration | Real-time presence + live ledger updates (WebSocket channel) | "Multi-user Tally over internet" is the holy grail | SDK-SERVER (no accounting WS endpoint today; keep 30s polling) | L | Tally multi-user / Zoho collab |

## Implementation notes (per feature)

1. **Drill-down**: `ReportsScreen` rows become clickable → new `screens/ledger/AccountLedgerScreen` (vouchers for account from `VoucherNetwork.retrieveVoucherAsync` filter + offline repo) → tap row → existing voucher detail sheet. Pure client composition of existing networks.
2. **Recurring vouchers**: model `RecurringVoucherTemplate` already in `core-accounting` (SDK network methods unwired). Implement client-local store (settings-scoped in `AccountingWorkspace` storage); a scheduler on app start/dashboard creates DRAFT vouchers when due. Server RPC later swaps the store.
3. **Multi-currency**: `MultiCurrencyNetwork.getFxRatesAsync/updateFxRateAsync/runRevaluationAsync` + `FxRate` model exist. New `screens/multiCurrency/` (rates manager + revaluation), and currency selector on voucher entry displaying converted base amounts live.
4. **Consolidation viewer**: `ConsolidationNetwork` (groups, eliminations, generateConsolidation) — new `screens/consolidation/` consuming `ConsolidatedReport`/`ConsolidationGroup`/`EliminationEntry` models, read-only viewer + group management sheet.
5. **Import wizard**: new `screens/importWizard/` — paste/file CSV (reuse `StatementCsvParser` pattern from reconciliation), map columns → `AccountNetwork.importAccountsAsync` batch; preview + error rows UI. Extends chart-of-accounts.
6. **Fixed assets**: new `screens/fixedAssets/` — local register (workspace storage) with SLM/WDV engine in pure Kotlin (commonTest-able), period JV drafts pushed through `VoucherNetwork.createVoucherAsync` as drafts awaiting review.
7. **Project tags**: add `projectTag`/`costCenter` free-text-with-suggestions on `VoucherLineItem` row editor (client-side fields stored in voucher narration metadata + settings list), filtered views in reports tab (client-side filter over retrieved vouchers). Server schema field later.
8. **ITC workspace**: new `screens/itcWorkspace/` — import button parses GSTR-2B JSON/CSV into local rows, matches purchase vouchers by GSTIN+invoice#+amount, shows MATCHED/MISSING_IN_BOOKS/DIFF columns with actions. Export action list as CSV. Pure client computation in commonTest-covered matcher.
9. **Dunning**: extends `reports` aging data — new `screens/dunning/` computes buckets (0-30/31-60/61-90/90+) from receivable vouchers (client-side), generates printable reminder letter text per party with placeholders. Send: copy-to-clipboard + mailto; WhatsApp = #18.
10. **Templates + numbering**: `SettingsScreen` gains numbering-series editor; `VoucherEntryScreen` gains save-as-template + template picker (`AccountingSettingsStore`-scoped storage). S effort, big perceived speed.
11. **Approvals**: `VoucherEntryScreen` status chip cycle DRAFT→VERIFIED→POSTED using existing `VoucherStatus` + `VoucherNetwork.postVoucherAsync`; list filters by status. Approver note in narration metadata until server field exists.
12. **Keyboard-first**: desktop/web key-event layer in `App.kt`/`navigation`: Alt+C (new account), Alt+V (new voucher), Enter on report row = drill, Esc = back, Ctrl+P print/export where applicable. Desktop-focused; documented cheat-sheet dialog.
13. **Attachments**: `Attachment` model is in `Voucher`'s line items — add attach UI row in voucher editor storing `Attachment(name=..., url=...)` (URL-paste + file picker on desktop/android; upload lands when sdk/media upload helper confirmed at 3.0.7).
14. **Tests**: `commonTest`: ViewModel tests for VoucherEntry (balancing, drafts), TdsCenter (quarter/FY logic), Reports (tab/run), Reconciliation matcher, ITC matcher (#8 reuses), depreciation engine (#6), plus `KoinTest` module check + kotlinx-serialization round-trips for DTOs used by new features. Runs under `desktopTest`.
15. **i18n**: move UI strings of Dashboard/VoucherEntry/Settings to Compose Resources (`composeResources/values-hi`), add `hi` translations, font fallback verified for Devanagari; rollout rest-of-app in waves listed here.
16. **Bank rules**: rules editor (contains/equals/starts-with → suggest ledger + confidence) applied to imported statement rows in Reconciliation screen before auto-match; feeds/AA = stub card linking to plan.
17. **IRN/QR**: when an e-invoice response carries IRN (`EinvoiceResponse` model), voucher/print view renders QR (pure-Kotlin QR encode — e.g. kge/kotlin QR generation lib verified KMP-safe) + IRN line; else "IRN pending".
18. **WhatsApp reminders**: dunning list "Send via WhatsApp" → uses existing WhatsApp template/send DTOs from core `feature/whatsapp` IF the app session has a configured provider; falls back to copy-text; honest capability gating.
19. **Payroll-lite**: `screens/payroll/` register (employee, month, earnings/deductions grid) computing TDS-192B estimate client-side (slab math in commonTest) → posts monthly salary JV as draft. PF/ESI/statutory marked "server roadmap".
20. **Live collab**: no accounting WS in SDK 3.0.7 — UI stub (banner "Live sync coming") + retain dashboard 30s polling. Plan: reuse core `TanvritSocketClient` when server ships `/api/accounting/ws`.

## Sequencing — 4 waves

- **Wave 1 (speed + trust, table stakes)**: #1 drill-down, #10 templates/numbering, #11 approvals, #12 keyboard-first, #14 test net. Rationale: cheap, immedi­ately changes daily UX; tests de-risk everything after.
- **Wave 2 (money movement)**: #2 recurring, #3 multi-currency, #5 import wizard, #9 dunning, #13 attachments. Rationale: cash-flow + migration — the two reasons SMBs actually switch.
- **Wave 3 (compliance depth)**: #6 fixed assets, #7 project tags, #8 ITC workspace, #4 consolidation viewer, #15 i18n/Hindi. Rationale: premium/compliance pull + reach.
- **Wave 4 (server-gated, honest stubs)**: #16 bank rules+feeds, #17 IRN QR, #18 WhatsApp reminders, #19 payroll-lite, #20 live collab. Rationale: client-possible parts ship; server parts planned, never faked.

## SDK reality (verified 2026-09-26, @3.0.7)

- 10 accounting Networks: Account, Voucher, FiscalPeriod, AccountingBudget, Tax, Reconciliation, **MultiCurrency** (getFxRates/updateFxRate/runRevaluation/hedging), Report (+**consolidatedReport**), Audit, **Consolidation**. RPC action-style routes under `/api/accounting/`.
- Declared-but-unwired events: RECURRING_VOUCHER, CREATE_RECURRING_TEMPLATE, UPDATE_RECURRING_TEMPLATE, GENERATE_GSTR9, CANCEL_EINVOICE, CANCEL_EWAY_BILL, TDS 24Q/27Q (event constants exist, no sdk Network funs) → features built client-side first, RPC swap later.
- Inventory stack EXISTS (sdk/commerce: InventoryNetwork/Batches/Movements/Reservations/Barcode UI) — accounting exposes ledger side; full inventory-accounting merge is a separate track.
- NOT FOUND in SDK: GSTR-2A/2B pull, OCR, e-sign/DSC, payroll domain, accounting WebSocket. → all stubbed honestly in Wave 4 or client-computed.
- WhatsApp: full model+DTO surface in core `feature/whatsapp` (config, templates, send/bulk) — provider-config gated.
