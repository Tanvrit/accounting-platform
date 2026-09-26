import SwiftUI
import ComposeApp

/// SwiftUI entry point.
///
/// The KMP `mainViewController()` (composeApp/src/iosMain/.../MainViewController.kt)
/// initialises the Tanvrit SDK via `initTanvritAccounting(...)` and returns the
/// Compose root view controller, so nothing Tanvrit-specific lives here.
@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
