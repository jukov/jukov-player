import ActivityKit
import Foundation

struct DownloadActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        let progress: Double?
        let pendingCount: Int
    }
}
