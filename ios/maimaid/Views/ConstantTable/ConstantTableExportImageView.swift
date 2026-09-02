import SwiftUI
import UIKit

struct ConstantTableExportImageView: View {
    let baseLevel: Int
    let sections: [ConstantTableExportView.ExportSection]
    let mode: ConstantTableExportView.Mode
    let userName: String?

    @Environment(\.colorScheme) private var colorScheme

    private let canvasWidth: CGFloat = 1440
    private let horizontalPadding: CGFloat = 28
    private let labelWidth: CGFloat = 72

    private var chartWidth: CGFloat { mode == .withScores ? 58 : 52 }
    private var jacketSize: CGFloat { mode == .withScores ? 58 : 52 }
    private var chartSpacing: CGFloat { mode == .withScores ? 8 : 6 }
    private var maxColumns: Int {
        let usableWidth = canvasWidth - horizontalPadding * 2 - labelWidth - 20
        return max(Int((usableWidth + chartSpacing) / (chartWidth + chartSpacing)), 1)
    }

    private var backgroundColor: Color { colorScheme == .dark ? Color(hex: "#111216") : Color(hex: "#FFF5FB") }
    private var secondaryBackgroundColor: Color { colorScheme == .dark ? Color(hex: "#171922") : Color(hex: "#FAEEFF") }
    private var tertiaryBackgroundColor: Color { colorScheme == .dark ? Color(hex: "#20172B") : Color(hex: "#F8F0FF") }
    private var primaryColor: Color { colorScheme == .dark ? .white : Color(hex: "#8A245C") }
    private var secondaryColor: Color { colorScheme == .dark ? Color.white.opacity(0.66) : Color.black.opacity(0.58) }
    private var sectionFillA: Color { colorScheme == .dark ? Color.white.opacity(0.06) : Color.white.opacity(0.24) }
    private var sectionFillB: Color { colorScheme == .dark ? Color.white.opacity(0.03) : Color.white.opacity(0.14) }
    private var dividerColor: Color { colorScheme == .dark ? Color.white.opacity(0.12) : Color.white.opacity(0.85) }
    private var totalCharts: Int { sections.reduce(0) { $0 + $1.entries.count } }

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [backgroundColor, secondaryBackgroundColor, tertiaryBackgroundColor],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )

            VStack(alignment: .leading, spacing: 20) {
                headerSection

                ForEach(sections.indices, id: \.self) { index in
                    sectionView(sections[index], index: index)
                }

                footerSection
            }
            .padding(.horizontal, horizontalPadding)
            .padding(.vertical, 24)
        }
        .frame(width: canvasWidth)
    }

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(
                        String(
                            format: String(
                                localized: mode == .constantsOnly
                                    ? "scoreQuery.export.imageTitle.constants"
                                    : "scoreQuery.export.imageTitle.scores"
                            ),
                            displayBaseLevel
                        )
                    )
                    .font(.system(size: 36, weight: .black, design: .rounded))
                    .foregroundStyle(primaryColor)

                    Text(
                        String(
                            format: String(localized: "scoreQuery.export.imageSummary"),
                            sections.count,
                            totalCharts
                        )
                    )
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(secondaryColor)
                }

                Spacer()

                if let userName, mode == .withScores {
                    exportPill(text: userName, icon: "person.crop.circle.fill", tint: Color(hex: "#8E3DFF"))
                }
            }

            Divider()
                .overlay(dividerColor)
        }
    }

    private var displayBaseLevel: String {
        baseLevel == 14 ? "14~15" : baseLevel.formatted()
    }

    private func sectionView(_ section: ConstantTableExportView.ExportSection, index: Int) -> some View {
        let rows = section.entries.chunked(into: maxColumns)

        return HStack(alignment: .top, spacing: 16) {
            Text(section.levelLabel)
                .font(.system(size: 32, weight: .black, design: .rounded))
                .foregroundStyle(levelColor(for: section.levelLabel, index: index))
                .frame(width: labelWidth, alignment: .leading)
                .lineLimit(1)
                .minimumScaleFactor(0.75)

            VStack(alignment: .leading, spacing: chartSpacing) {
                ForEach(rows.indices, id: \.self) { rowIndex in
                    HStack(spacing: chartSpacing) {
                        ForEach(rows[rowIndex]) { entry in
                            exportChartCell(entry)
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 18)
                .fill(index.isMultiple(of: 2) ? sectionFillA : sectionFillB)
        )
    }

    private func exportChartCell(_ entry: ConstantTableExportView.Entry) -> some View {
        let borderColor = ThemeUtils.colorForDifficulty(entry.difficulty, entry.type, colorScheme)

        return ZStack(alignment: .bottomTrailing) {
            SongJacketView(
                imageName: entry.imageName,
                size: jacketSize,
                cornerRadius: 8,
                useThumbnail: true
            )
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(borderColor, lineWidth: 2)
            )

            if mode == .withScores {
                VStack(alignment: .trailing, spacing: 2) {
                    overlayLine(text: entry.rank, color: entry.rank.map(RatingUtils.colorForRank))
                    overlayLine(text: entry.fc.map(ThemeUtils.normalizeFC), color: entry.fc.map(ThemeUtils.fcColor))
                    overlayLine(text: entry.fs.map(ThemeUtils.normalizeFS), color: entry.fs.map(ThemeUtils.fsColor))
                }
                .padding(2)
            }
        }
        .frame(width: chartWidth, height: jacketSize, alignment: .bottomTrailing)
    }

    private func exportPill(text: String, icon: String, tint: Color) -> some View {
        Label(text, systemImage: icon)
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(tint)
            .lineLimit(1)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(tint.opacity(0.12), in: Capsule())
    }

    private var footerSection: some View {
        HStack {
            Text("scoreQuery.export.watermark")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(secondaryColor)
            Spacer()
        }
        .padding(.top, 4)
    }

    @ViewBuilder
    private func overlayLine(text: String?, color: Color?) -> some View {
        if let text, !text.isEmpty, let color {
            Text(text)
                .font(.system(size: 9, weight: .black, design: .rounded))
                .foregroundStyle(.white)
                .padding(.horizontal, 3)
                .padding(.vertical, 1)
                .background(color, in: RoundedRectangle(cornerRadius: 3))
                .lineLimit(1)
                .minimumScaleFactor(0.75)
        }
    }

    private func levelColor(for label: String, index: Int) -> Color {
        let tenths = Int(((Double(label) ?? 0) * 10).rounded()) % 10

        switch tenths {
        case 0, 5:
            return Color(hex: "#D34A63")
        case 1, 6:
            return Color(hex: "#4D78FF")
        case 2, 7:
            return Color(hex: "#3F9B74")
        case 3, 8:
            return Color(hex: "#B45BFF")
        default:
            return index.isMultiple(of: 2) ? Color(hex: "#C84A7B") : Color(hex: "#5489FF")
        }
    }

    @MainActor
    static func renderImage(
        baseLevel: Int,
        sections: [ConstantTableExportView.ExportSection],
        mode: ConstantTableExportView.Mode,
        userName: String?,
        colorScheme: ColorScheme
    ) -> UIImage? {
        let content = ConstantTableExportImageView(
            baseLevel: baseLevel,
            sections: sections,
            mode: mode,
            userName: userName
        )
        .environment(\.colorScheme, colorScheme)
        .preferredColorScheme(colorScheme)

        let renderer = ImageRenderer(content: content)
        renderer.scale = 2
        return renderer.uiImage
    }
}

private extension Array {
    func chunked(into size: Int) -> [[Element]] {
        guard size > 0 else { return [] }

        return stride(from: 0, to: count, by: size).map { index in
            Array(self[index..<Swift.min(index + size, count)])
        }
    }
}
