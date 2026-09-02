import SwiftUI

struct SongDetailChartVersionRow: View {
    let version: String
    let tint: Color

    var body: some View {
        HStack {
            Label("song.detail.chart.addedVersion", systemImage: "clock.badge.plus")
                .foregroundStyle(.secondary)
            Spacer()
            Text(ThemeUtils.versionAbbreviation(version))
                .bold()
                .foregroundStyle(tint)
        }
        .font(.subheadline)
        .padding(.horizontal)
    }
}
