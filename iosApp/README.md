# iosApp — Tanvrit Accounting

Compose Multiplatform iOS host. The Xcode project here wraps the
`composeApp` framework produced by Gradle.

## Build / run

1. Open `iosApp/iosApp.xcodeproj` in Xcode.
2. Pick a simulator (or a device) and Run the `iosApp` scheme.

There is no `Podfile` and no `Package.swift`: the project's
**Compile Kotlin Framework** build phase shells out to
`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`, which builds the
static `ComposeApp` framework from `:composeApp` (iosArm64 /
iosSimulatorArm64, `baseName = "ComposeApp"`, `isStatic = true` — see
`composeApp/build.gradle.kts`) and embeds it into the app. Building from the
command line also works:

```bash
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64   # from repo root
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 17' build
```

## Settings

- `IPHONEOS_DEPLOYMENT_TARGET` = `18.2`, `SWIFT_VERSION` = `5.0`,
  `SDKROOT` = `iphoneos`.
- Bundle id `com.tanvrit.accounting`, display name "Tanvrit Accounting"
  (`Configuration/Config.xcconfig` + `INFOPLIST_KEY_*` in the pbxproj —
  `GENERATE_INFOPLIST_FILE = YES`, so the checked-in `Info.plist` stays
  minimal; `ITSAppUsesNonExemptEncryption = NO` is declared there for export
  compliance).
- App icons: white "T" on ledger green `#14532D` (the brand primary in
  `theme/AccountingTheme.kt`), generated with the monorepo's
  `tools/icongen.py`.

## Signing

Signing is **local-only and never committed**. `Configuration/Config.xcconfig`
ships with `TEAM_ID=` blank on purpose; set your Apple Developer Team ID there
(or let Xcode's Automatically manage signing do it) — it feeds
`DEVELOPMENT_TEAM` and nothing else. Do not commit provisioning profiles,
certificates, or `.p8` API keys. App Store submissions need a build against
the current iOS SDK (Xcode 16+; see the release workflow template in
`platforms/artifactory` — `release-ios-template.yml`, scheme `iosApp`).
