import ActivityKit
import Foundation
import UserNotifications

@MainActor
final class DownloadLiveActivityManager {
    private enum Event {
        static let update = Notification.Name("info.jukov.player.downloadActivity.update")
        static let end = Notification.Name("info.jukov.player.downloadActivity.end")
        static let percentKey = "percent"
        static let pendingCountKey = "pendingCount"
        static let legacyProgressKey = "downloadProgress"
    }

    private var observers: [NSObjectProtocol] = []
    private var pendingOperations = 0
    private var currentOperation: Task<Void, Never>?
    private var backgroundCompletionHandlers: [() -> Void] = []

    func start() {
        guard observers.isEmpty else {
            return
        }
        removeLegacyProgressNotifications()
        let center = NotificationCenter.default
        observers.append(
            center.addObserver(forName: Event.update, object: nil, queue: .main) { [weak self] event in
                guard let pendingCount = (event.userInfo?[Event.pendingCountKey] as? NSNumber)?.intValue else {
                    return
                }
                let percent = (event.userInfo?[Event.percentKey] as? NSNumber)?.intValue
                MainActor.assumeIsolated {
                    self?.perform {
                        await self?.show(progressPercent: percent, pendingCount: pendingCount)
                    }
                }
            }
        )
        observers.append(
            center.addObserver(forName: Event.end, object: nil, queue: .main) { [weak self] _ in
                MainActor.assumeIsolated {
                    self?.perform {
                        await self?.end()
                    }
                }
            }
        )
    }

    private func removeLegacyProgressNotifications() {
        let center = UNUserNotificationCenter.current()
        center.getPendingNotificationRequests { requests in
            let identifiers = requests.compactMap { request in
                request.content.userInfo[Event.legacyProgressKey] as? Bool == true
                    ? request.identifier
                    : nil
            }
            UNUserNotificationCenter.current()
                .removePendingNotificationRequests(withIdentifiers: identifiers)
        }
        center.getDeliveredNotifications { notifications in
            let identifiers = notifications.compactMap { notification in
                notification.request.content.userInfo[Event.legacyProgressKey] as? Bool == true
                    ? notification.request.identifier
                    : nil
            }
            UNUserNotificationCenter.current()
                .removeDeliveredNotifications(withIdentifiers: identifiers)
        }
    }

    func finishPendingOperations(then completionHandler: @escaping () -> Void) {
        if pendingOperations == 0 {
            completionHandler()
        } else {
            backgroundCompletionHandlers.append(completionHandler)
        }
    }

    private func perform(_ operation: @escaping @MainActor () async -> Void) {
        pendingOperations += 1
        let precedingOperation = currentOperation
        currentOperation = Task { @MainActor [weak self] in
            await precedingOperation?.value
            await operation()
            self?.operationFinished()
        }
    }

    private func operationFinished() {
        pendingOperations -= 1
        guard pendingOperations == 0 else {
            return
        }
        currentOperation = nil
        let handlers = backgroundCompletionHandlers
        backgroundCompletionHandlers.removeAll()
        handlers.forEach { $0() }
    }

    private func show(progressPercent: Int?, pendingCount: Int) async {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else {
            return
        }
        let state = DownloadActivityAttributes.ContentState(
            progress: progressPercent.map { Double(max(0, min($0, 100))) / 100.0 },
            pendingCount: max(1, pendingCount)
        )
        let content = ActivityContent(state: state, staleDate: nil)
        if let activity = Activity<DownloadActivityAttributes>.activities.first {
            await activity.update(content)
            await endDuplicateActivities(keeping: activity.id)
            return
        }
        do {
            _ = try Activity.request(
                attributes: DownloadActivityAttributes(),
                content: content,
                pushType: nil
            )
        } catch {
            // A disabled or unavailable Live Activity must not affect the download itself.
        }
    }

    private func end() async {
        for activity in Activity<DownloadActivityAttributes>.activities {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
    }

    private func endDuplicateActivities(keeping activityID: String) async {
        for activity in Activity<DownloadActivityAttributes>.activities where activity.id != activityID {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
    }
}
