import ActivityKit
import Foundation

struct DownloadActivityAttributes: ActivityAttributes {
    static let downloadsURL = URL(string: "jukovplayer://downloads")

    struct ContentState: Codable, Hashable {
        let progress: Double?
        let pendingCount: Int
    }
}
