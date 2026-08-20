import ActivityKit
import SwiftUI
import WidgetKit

struct DownloadActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: DownloadActivityAttributes.self) { context in
            DownloadActivityView(state: context.state)
                .widgetURL(DownloadActivityAttributes.downloadsURL)
                .downloadActivityBackgroundTint()
                .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: "arrow.down.circle.fill")
                        .foregroundStyle(DownloadActivityPalette.accent)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    ProgressLabel(state: context.state)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    DownloadProgressView(state: context.state)
                }
            } compactLeading: {
                Image(systemName: "arrow.down")
                    .foregroundStyle(DownloadActivityPalette.accent)
            } compactTrailing: {
                ProgressLabel(state: context.state)
            } minimal: {
                Image(systemName: "arrow.down")
                    .foregroundStyle(DownloadActivityPalette.accent)
            }
            .widgetURL(DownloadActivityAttributes.downloadsURL)
            .keylineTint(DownloadActivityPalette.accent)
        }
    }
}

private struct DownloadActivityView: View {
    let state: DownloadActivityAttributes.ContentState

    @ViewBuilder
    var body: some View {
        if #available(iOS 26.0, *) {
            content
                .glassEffect(
                    .regular.tint(DownloadActivityPalette.glassTint)
                )
                .containerBackground(.clear, for: .widget)
        } else {
            content
                .background(DownloadActivityPalette.fallbackBackground)
                .containerBackground(
                    DownloadActivityPalette.fallbackBackground,
                    for: .widget
                )
        }
    }

    private var content: some View {
        HStack(spacing: 12) {
            Image(systemName: "arrow.down.circle.fill")
                .font(.title2)
                .foregroundStyle(DownloadActivityPalette.accent)
            VStack(alignment: .leading, spacing: 6) {
                Text("Downloading music")
                    .font(.headline)
                DownloadProgressView(state: state)
            }
        }
        .foregroundStyle(.white)
        .padding()
        .frame(
            maxWidth: .infinity,
            maxHeight: .infinity,
            alignment: .leading
        )
    }
}

private enum DownloadActivityPalette {
    static let accent = Color(
        .sRGB,
        red: 38.0 / 255.0,
        green: 166.0 / 255.0,
        blue: 154.0 / 255.0,
        opacity: 1
    )
    static let glassTint = accent.opacity(0.28)
    static let fallbackBackground = Color(
        .sRGB,
        red: 0,
        green: 80.0 / 255.0,
        blue: 72.0 / 255.0,
        opacity: 1
    )
}

private extension View {
    @ViewBuilder
    func downloadActivityBackgroundTint() -> some View {
        if #available(iOS 26.0, *) {
            activityBackgroundTint(.clear)
        } else {
            activityBackgroundTint(DownloadActivityPalette.fallbackBackground)
        }
    }
}

private struct DownloadProgressView: View {
    let state: DownloadActivityAttributes.ContentState

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if let progress = state.progress {
                ProgressView(value: progress)
                    .tint(DownloadActivityPalette.accent)
            } else {
                ProgressView()
                    .tint(DownloadActivityPalette.accent)
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
