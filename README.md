# Tanvrit Accounting Platform

A Compose Multiplatform accounting app built on the Tanvrit SDK to manage business financial operations across all platforms.

## What This Does

This app provides a complete accounting solution:
- **Chart of Accounts** - Hierarchical account management
- **Voucher Entry** - Sales, purchases, receipts, payments, journals
- **GST Center** - GSTR-1, GSTR-3B, E-Invoice, E-Way Bill generation
- **TDS Center** - 26Q/27Q/24Q returns, Form 16/16A
- **Bank Reconciliation** - Auto-match, manual match, discrepancy report
- **Multi-Currency** - FX rates, revaluation, hedge accounting
- **Financial Reports** - Trial Balance, P&L, Balance Sheet, Cash Flow
- **Audit Trail** - Immutable audit logs with hash chain verification
- **Budget** - Budgeting, variance analysis, forecasting

## Build & Run

### Desktop (Compose Multiplatform)
```bash
./gradlew :composeApp:run
```

### Android
```bash
./gradlew :composeApp:assembleDebug
```

### iOS
```bash
# Open iosApp stub in Xcode
open iosApp/iosApp.xcodeproj
```

### Web
```bash
./gradlew :composeApp:composeWebWasmJsDevelopmentRun
```

## Dependencies

The app uses the Tanvrit SDK for its core accounting functions:
- `com.tanvrit:accounting` (core-accounting models + DTOs)
- `com.tanvrit:business` (repository implementations)
- `com.tanvrit:storage` (offline-first persistence)
- `com.tanvrit:auth` (authentication)

The SDK is published to `https://maven.tanvrit.com` as of version 3.0.5.

## Structure

```
composeApp/
  src/commonMain/kotlin/com/tanvrit/accounting/
    App.kt                 # Main app entry
    Main.kt                # Desktop entry point
    navigation/
      Navigation.kt        # Navigation graph
    screens/
      DashboardScreen.kt
      VoucherEntryScreen.kt
      ChartOfAccountsScreen.kt
      GstCenterScreen.kt
      TdsCenterScreen.kt
      FiscalPeriodsScreen.kt
      BudgetScreen.kt
      AuditTrailScreen.kt
    theme/
      Theme.kt             # Tanvrit brand tokens

shared/                   # Shared business logic
  src/commonMain/kotlin/
    AccountRepository.kt

build.gradle.kts         # Root platform build
settings.gradle.kts      # Module inclusion
```

## Deployment

### Web (Cloudflare Pages)
```bash
./deploy.sh
./deploy.sh --platform=web
```

### Desktop
- Mac: `build/package-release/dmg`
- Windows: `build/package-release/msi`
- Linux: `build/package-release/deb`

### Mobile Deployment
```bash
./deploy.sh --platform=android
./deploy.sh --platform=ios
```

## Testing

```bash
./gradlew :composeApp:test
```

## Note

This is Phase 3 under active development - the platform app skeleton is scaffolded but routes not yet wired to real screens (Dashboard screen placeholders only). Once Phase 3 work continues, detailed UI screens will be implemented.