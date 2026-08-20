import FirebaseCore
import Shared
import UIKit
import UserNotifications

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        configureFirebaseIfRegistered()
        IosAppRuntime.shared.start()
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    private func configureFirebaseIfRegistered() {
        guard let options = FirebaseOptions.defaultOptions(),
              options.bundleID == Bundle.main.bundleIdentifier else {
            return
        }
        FirebaseApp.configure(options: options)
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        IosAppRuntime.shared.startRecovery()
    }

    func application(
        _ application: UIApplication,
        handleEventsForBackgroundURLSession identifier: String,
        completionHandler: @escaping () -> Void
    ) {
        IosAppRuntime.shared.handleEventsForBackgroundSession(
            identifier: identifier,
            completionHandler: completionHandler
        )
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        if notification.request.content.userInfo["downloadProgress"] as? Bool == true {
            return [.list]
        }
        return [.banner, .list, .sound]
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if response.notification.request.content.userInfo["openDownloads"] as? Bool == true {
            IosAppRuntime.shared.requestOpenDownloads()
        }
        completionHandler()
    }
}
