import SwiftUI

struct RecommendationPageView: View {
    let results: [RecommendationResult]
    let visibleCount: Int
    let onLoadMore: @MainActor () -> Void
    let onRefresh: @MainActor () async -> Void

    var body: some View {
        List {
            if results.isEmpty {
                ContentUnavailableView(
                    "rec.empty.title",
                    systemImage: "sparkles",
                    description: Text("rec.empty.desc")
                )
                .frame(maxWidth: .infinity, minHeight: 320)
                .listRowBackground(Color.clear)
            } else {
                ForEach(results.prefix(visibleCount)) { result in
                    NavigationLink(destination: SongDetailView(song: result.song, preferredType: result.sheet.type)) {
                        RecommendationRow(result: result)
                    }
                }

                if visibleCount < results.count {
                    HStack {
                        Spacer()
                        ProgressView()
                        Spacer()
                    }
                    .listRowSeparator(.hidden)
                    .id(visibleCount)
                    .onAppear(perform: onLoadMore)
                }
            }
        }
        .listStyle(.insetGrouped)
        .refreshable {
            await onRefresh()
        }
    }
}
