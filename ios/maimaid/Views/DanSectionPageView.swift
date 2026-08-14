import SwiftData
import SwiftUI

struct DanSectionPageView: View {
    let categoryTitle: String
    let sections: [DanSection]
    let songMap: [String: Song]
    let scoreCache: [String: Score]
    @Binding var scrollPosition: ScrollPosition

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                ForEach(sections) { section in
                    DanSectionCard(
                        categoryTitle: categoryTitle,
                        section: section,
                        songMap: songMap,
                        scoreCache: scoreCache
                    )
                }
            }
            .padding(.horizontal, 14)
            .padding(.top, 10)
            .padding(.bottom, 24)
        }
        .scrollPosition($scrollPosition)
    }
}
