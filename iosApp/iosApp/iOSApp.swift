import Shared
import SwiftUI

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    IosAppRuntime.shared.handleDeepLink(url: url.absoluteString)
                }
        }
    }
}
