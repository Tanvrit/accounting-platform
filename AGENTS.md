<!-- Tool-neutral mirror of CLAUDE.md. Keep both in sync; edit the same fact set in both. -->
# Tanvrit Accounting — Compose Multiplatform accounting platform

KMP app (Android, iOS, Desktop/JVM, WasmJS) built on the Tanvrit SDK
(`com.tanvrit:accounting` + `core-accounting`/`ui`/`storage`/`auth`/`business`
from `https://maven.tanvrit.com`). The customer-facing shell for the accounting
module: dashboard, chart of accounts, vouchers, GST/TDS centers, reports,
fiscal periods, budgets, bank reconciliation, audit trail, system settings.

## Build / Run / Test

```bash
./gradlew :composeApp:run                          # Desktop (JVM)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun  # Web dev server (:3000)
./gradlew :composeApp:assembleDebug                # Android
./gradlew :composeApp:desktopTest                  # Tests (commonTest runs on JVM)
./gradlew :composeApp:compileKotlinDesktop         # Fastest compile check
./gradlew ktlintCheck                              # Lint gate — CI-blocking, ignoreFailures=false
./gradlew ktlintFormat                             # Auto-fix before PR
./gradlew :composeApp:wasmJsBrowserProductionWebpack  # Production web bundle (what deploy ships)
```

SDK pins live in `gradle/libs.versions.toml` as a deliberate **split pin**:
`tanvrit` (SDK modules) and `tanvrit-core` (core + core-accounting). Never
collapse them and never bump either without verifying the artifact for the
target that ships (fetch the wasm-js klib from maven.tanvrit.com, grep it for a
symbol the release adds). The comment block in the TOML records what was
verified and when.

## Repo-specific conventions

Cross-cutting rules (BaseDataClass/preserveBase, @SerialName wire format, Koin
DI, AppJson, Design 2.0 tokens) are owned by the monorepo root `AGENTS.md` and
`docs/development-patterns.md` — not restated (ADR-037). App-specific deltas:

- **Bootstrap:** every platform entry point calls
  `app/SdkInit.kt:initTanvritAccounting(AppStartupConfig(...))` before
  composing `App()`. Koin load order: `coreModule`, `storageModule`,
  `authModule`, `businessModule`, `uiModule`, `AccountingModule` (SDK
  accounting), `accountingAppModule` (app).
- **Screen ViewModels** live in `screens/<name>/<Name>ViewModel.kt`, extend the
  SDK `AppViewModel`, are constructed with `rememberViewModel { ... }` (never
  `koinViewModel()` — AppViewModel is not an androidx ViewModel), and hold a
  single `UiState` `MutableStateFlow`. Networks via `XxxNetwork.shared()`,
  repositories via `TanvritKoin.get<...>()`.
- **Reads offline-first:** cached lists come from the SDK repositories
  (`AccountRepository.findByBusinessId`, …); server reports/mutations go
  through the networks; successful mutations upsert the local cache.
- **Money:** `Money` (minor-unit Long) end-to-end; parse input with
  `screens/common/AccountingComponents.parseMoneyInput`, render with
  `formatMoney` / `MoneyText`. Never pass money as `Double` to a DTO — DTOs
  that take amounts take `String` major units.
- **Theme:** `theme/AccountingTheme.kt` is the only file with raw `Color(...)`.
  Screens use `MaterialTheme.colorScheme.*` and `TanvritDesignSystem.*`.
  Dark/light/black follows `TanvritTheme` mode, set from Settings → Appearance.

## Layout

```
composeApp/src/
  commonMain/kotlin/com/tanvrit/accounting/
    App.kt                 # Theme + nav shell
    app/SdkInit.kt         # identity + TanvritSDK.init + module load order
    di/                    # accountingAppModule (workspace + settings store)
    data/                  # AccountingWorkspace, AccountingSettingsStore
    navigation/            # Routes.kt (typed), Navigation.kt (NavigationSuite)
    screens/               # one dir per screen — <Name>Screen + <Name>ViewModel
      common/              # shared MoneyText/StatusChip/Dialog/pickers + parse/format
      dashboard|chartOfAccounts|voucherEntry|gstCenter|tdsCenter|reports|
      fiscalPeriods|budget|reconciliation|auditTrail|settings/
    theme/AccountingTheme.kt
  androidMain/  desktopMain/  iosMain/  wasmJsMain/   # entry points
  commonTest/                                        # pure-Kotlin unit tests
```

## Blast Radius — needs explicit authorization

Auto-mode does **not** override these. Confirm each time.

- **Never** run `./deploy.sh web` (deploys the WasmJS bundle to Cloudflare
  Pages, `tanvrit-accounting` project) unless the user says deploy.
- **Never** bump `tanvrit` / `tanvrit-core` / `TANVRIT_SDK_VERSION` or
  `VERSION_NAME` / `VERSION_CODE` without instruction and a verified
  published artifact.
- **Never** change `@SerialName` values, touch `BaseDataClass`, or edit the
  module set in `settings.gradle.kts`.
- **Never** edit `.env*` / `local.properties` / signing material, and never
  commit secrets. Android release signing is env-driven (`RELEASE_STORE_*`);
  with the keystore absent, release builds are unsigned by design.
- **Never** bypass git hooks (`--no-verify`) — investigate failures instead.

## AI assistant

Monorepo docs catalog: `docs/INDEX.md`; master plan:
`tanvrit/accounting/MASTER_PLAN.md` (Phase 3) + `phase-3/PHASE_3_PLAN.md`.
