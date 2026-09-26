# Tanvrit Accounting Platform

Compose Multiplatform accounting app (Android, iOS, Desktop/JVM, Web/WasmJS) on
the Tanvrit SDK — real-time double-entry bookkeeping with Indian compliance
(GST, TDS/TCS), bank reconciliation, budgets, fiscal-period controls, and an
immutable audit trail. Phase 3 of `tanvrit/accounting/MASTER_PLAN.md`.

## Screens

| Screen | Route | Notes |
|---|---|---|
| Dashboard | `dashboard` | KPIs (cash, revenue, expenses, net profit, GST liability), top expenses, recent vouchers; 30s auto-refresh |
| Chart of Accounts | `coa` | Searchable hierarchical tree, type filters, create/edit in a `GlassSheet` |
| Voucher Entry | `voucher` | Sale/Purchase/Receipt/Payment/Journal/Contra, smart default legs, live Dr=Cr balance bar, offline draft fallback |
| GST Center | `gst` | GSTR-1 / GSTR-3B generation + validation, e-invoice (IRN), e-way bill, pre-filing health checklist |
| TDS Center | `tds` | 26Q/27Q/24Q returns, challan linkage, Form 16/16A certificates |
| Reports | `reports` | Trial Balance, P&L, Balance Sheet, Cash Flow, Ratio Analysis; PDF/Excel/CSV export |
| Fiscal Periods | `periods` | Open / lock / close, carry-forward of opening balances |
| Budget | `budget` | Per-account budget lines, budget-vs-actual variance, indicative forecast |
| Bank Reconciliation | `recon` | CSV statement import, auto-match, manual match, complete, history |
| Audit Trail | `audit` | Immutable hash-chained event log, filters, before/after diff, integrity verify |
| System Settings | `settings` | Numbering series, GSTIN/TAN, base currency, fiscal defaults, dark mode |

## Build / Run / Test

**Marketing site:** the static landing page for `https://accounting.tanvrit.com`
lives in `landing/` (Next.js 15 static export → Cloudflare Pages). See
`landing/README.md`; it deploys independently of this Gradle project.

```bash
./gradlew :composeApp:run                          # Desktop (JVM)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun  # Web dev server (:3000)
./gradlew :composeApp:assembleDebug                # Android APK
./gradlew :composeApp:desktopTest                  # Tests
./gradlew :composeApp:compileKotlinDesktop         # Fastest compile check
./gradlew ktlintCheck                              # lint gate (engine 1.5.0)
./gradlew :composeApp:wasmJsBrowserProductionWebpack  # Production web bundle
./deploy.sh web --dry-run                          # build-only deploy rehearsal
```

No credentials are needed to build — the SDK resolves from
`https://maven.tanvrit.com` (unauthenticated Cloudflare proxy), then
`mavenLocal()` last so a stale local artifact never shadows a published one.

## SDK integration

Pins live in `gradle/libs.versions.toml` (split `tanvrit` / `tanvrit-core`,
deliberate — they publish from different repos; never collapse the two refs,
and never bump either without verifying the artifact for the target that ships
on `maven.tanvrit.com`).

Modules consumed: `core`, `core-accounting`, `storage`, `auth`, `business`,
`ui`, `accounting`. Every screen ViewModel talks to the SDK `accounting`
module — networks (`AccountNetwork`, `VoucherNetwork`, `FiscalPeriodNetwork`,
`AccountingBudgetNetwork`, `TaxNetwork`, `ReconciliationNetwork`,
`ReportNetwork`, `AuditNetwork`) for server calls, offline-first repositories
(`AccountRepository`, `VoucherRepository`, `FiscalPeriodRepository`,
`BudgetRepository`) for cached reads — via `XxxNetwork.shared()` / Koin
(`TanvritKoin.get`, modules loaded by `app/SdkInit.kt`).

Bootstrap per platform entry point: `initTanvritAccounting(AppStartupConfig(...))`
then `App()`. The active business comes from the SDK `BusinessRepository`
(`AccountingWorkspace`); with no business selected, screens render a guided
empty state.

## Conventions

- Design 2.0 tokens only — `TanvritDesignSystem.spacing/shapes/elevation/blur/
  icons`, `Modifier.tanvritPress`, `tanvritComposable<Route>` navigation,
  `rememberPremiumChartTheme().series` for charts, `Premium*` components. App
  colors live ONLY in `theme/AccountingTheme.kt`; screens read
  `MaterialTheme.colorScheme.*` / `TanvritDesignSystem.colors.*`.
- Money is `com.tanvrit.core.feature.money.Money` (minor-unit `Long`); display
  via `MoneyFormatter.format(money, currencyCode)`; never `Double` over the wire.
- `BaseDataClass` models: after `copy()` chain `.preserveBase(source)`.
- Never rename `@SerialName` values (wire/Mongo/cache format).
