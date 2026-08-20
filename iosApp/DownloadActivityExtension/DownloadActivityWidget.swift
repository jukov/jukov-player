import ActivityKit
import SwiftUI
import WidgetKit

struct DownloadActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: DownloadActivityAttributes.self) { context in
            DownloadActivityView(state: context.state)
                .widgetURL(DownloadActivityAttributes.downloadsURL)
                .activityBackgroundTint(.black)
                .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: "arrow.down.circle.fill")
                        .foregroundStyle(.blue)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    ProgressLabel(state: context.state)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    DownloadProgressView(state: context.state)
                }
            } compactLeading: {
                Image(systemName: "arrow.down")
                    .foregroundStyle(.blue)
            } compactTrailing: {
                ProgressLabel(state: context.state)
            } minimal: {
                Image(systemName: "arrow.down")
                    .foregroundStyle(.blue)
            }
            .widgetURL(DownloadActivityAttributes.downloadsURL)
            .keylineTint(.blue)
        }
    }
}

private struct DownloadActivityView: View {
    let state: DownloadActivityAttributes.ContentState

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "arrow.down.circle.fill")
                .font(.title2)
                .foregroundStyle(.blue)
            VStack(alignment: .leading, spacing: 6) {
                Text("Downloading music")
                    .font(.headline)
                DownloadProgressView(state: state)
            }
        }
        .foregroundStyle(.white)
        .padding()
    }
}

private struct DownloadProgressView: View {
    let state: DownloadActivityAttributes.ContentState

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if let progress = state.progress {
                ProgressView(value: progress)
                    .tint(.blue)
            } else {
                ProgressView()
                    .tint(.blue)
            }
            Text(remainingText)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private var remainingText: String {
        if state.pendingCount == 1 {
            return "1 track remaining"
        }
        return "\(state.pendingCount) tracks remaining"
    }
}

private struct ProgressLabel: View {
    let state: DownloadActivityAttributes.ContentState

    var body: some View {
        if let progress = state.progress {
            Text(progress, format: .percent.precision(.fractionLength(0)))
                .monospacedDigit()
        } else {
            Image(systemName: "ellipsis")
        }
    }
}
