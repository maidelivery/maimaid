import SwiftUI

struct ScannerResultCardView: View, Equatable {
    let song: Song
    let recognizedClass: MaimaiImageType
    let recognizedType: String?
    let recognizedDifficulty: String?
    let recognizedRate: Double?
    let resolvedSheet: Sheet?
    let onScoreEntryTap: () -> Void
    let onResetTap: () -> Void
    
    static func == (lhs: ScannerResultCardView, rhs: ScannerResultCardView) -> Bool {
        lhs.song.songIdentifier == rhs.song.songIdentifier &&
        lhs.recognizedClass == rhs.recognizedClass &&
        lhs.recognizedType == rhs.recognizedType &&
        lhs.recognizedDifficulty == rhs.recognizedDifficulty &&
        lhs.recognizedRate == rhs.recognizedRate &&
        lhs.resolvedSheet?.difficulty == rhs.resolvedSheet?.difficulty &&
        lhs.resolvedSheet?.type == rhs.resolvedSheet?.type &&
        (lhs.resolvedSheet?.internalLevel ?? lhs.resolvedSheet?.level) == (rhs.resolvedSheet?.internalLevel ?? rhs.resolvedSheet?.level)
    }
    
    var body: some View {
        if recognizedClass == .choose {
            NavigationLink(destination: {
                SongDetailView(song: song).onDisappear { onResetTap() }
            }) {
                VStack(spacing: 0) {
                    HStack(spacing: 12) {
                        SongJacketView(imageName: song.imageName, size: 40, cornerRadius: 8)
                            .shadow(color: .black.opacity(0.2), radius: 4, x: 0, y: 2)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(song.title).font(.system(size: 14, weight: .bold)).foregroundColor(.primary).lineLimit(1)
                            Text(song.artist).font(.system(size: 11, weight: .regular)).foregroundColor(.secondary).lineLimit(1)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").font(.system(size: 12, weight: .bold)).foregroundColor(.secondary.opacity(0.4))
                    }
                    .padding(.vertical, 14)
                    .padding(.horizontal, 16)
                }
                .fixedSize(horizontal: false, vertical: true)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.primary.opacity(0.1), lineWidth: 1))
                .padding(.horizontal, 20)
                .padding(.bottom, 40)
            }
            .buttonStyle(.plain)
            .transition(.asymmetric(insertion: .move(edge: .bottom).combined(with: .opacity).combined(with: .scale(scale: 0.9)), removal: .opacity.combined(with: .scale(scale: 0.95))))
        } else {
            Button { onScoreEntryTap() } label: {
                let sheet = resolvedSheet ?? song.sheets.first(where: {
                    $0.difficulty.lowercased() == (recognizedDifficulty ?? "master").lowercased() &&
                    $0.type.lowercased() == (recognizedType ?? "dx").lowercased()
                })
                let chartType = recognizedType ?? sheet?.type ?? "dx"
                let diff = recognizedDifficulty ?? sheet?.difficulty ?? "master"
                let diffColor = ThemeUtils.colorForDifficulty(diff, chartType)
                VStack(spacing: 0) {
                    HStack(spacing: 0) {
                        RoundedRectangle(cornerRadius: 2).fill(diffColor).frame(width: 4).padding(.vertical, 4)
                        HStack(spacing: 12) {
                            SongJacketView(imageName: song.imageName, size: 40, cornerRadius: 8)
                                .shadow(color: .black.opacity(0.2), radius: 4, x: 0, y: 2)
                            VStack(alignment: .leading, spacing: 3) {
                                HStack(spacing: 4) {
                                    Text(chartType.uppercased() == "STD" ? String(localized: "scanner.chart.std") : chartType.uppercased())
                                        .font(.system(size: 8, weight: .black))
                                        .padding(.horizontal, 4).padding(.vertical, 1)
                                        .background(ThemeUtils.badgeColorForChartType(chartType))
                                        .foregroundColor(.white).cornerRadius(3)
                                    Text(song.title).font(.system(size: 12, weight: .bold)).foregroundColor(.primary).lineLimit(1)
                                }
                                if diff.lowercased() == "remaster" {
                                    Text("RE: MASTER").font(.system(size: 13, weight: .bold, design: .rounded)).foregroundColor(diffColor)
                                } else {
                                    Text(diff.uppercased()).font(.system(size: 13, weight: .bold, design: .rounded)).foregroundColor(diffColor)
                                }
                            }
                            Spacer()
                            if let rate = recognizedRate {
                                VStack(alignment: .trailing, spacing: 1) {
                                    Text(String(format: "%.4f%%", rate)).font(.system(size: 12, weight: .bold, design: .monospaced)).foregroundColor(.primary)
                                    Text(RatingUtils.calculateRank(achievement: rate)).font(.system(size: 10, weight: .black, design: .rounded)).foregroundColor(diffColor)
                                }
                            }
                            if let levelStr = sheet?.internalLevel ?? sheet?.level {
                                Text(levelStr).font(.system(size: 28, weight: .black, design: .rounded)).foregroundColor(diffColor.opacity(0.85)).frame(minWidth: 44)
                            }
                            Image(systemName: "chevron.right").font(.system(size: 12, weight: .bold)).foregroundColor(.secondary.opacity(0.4))
                        }
                        .padding(.leading, 12).padding(.trailing, 16)
                    }
                    .padding(.vertical, 14)
                }
                .fixedSize(horizontal: false, vertical: true)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(diffColor.opacity(0.2), lineWidth: 1))
                .padding(.horizontal, 20)
                .padding(.bottom, 40)
            }
            .buttonStyle(.plain)
            .transition(.asymmetric(insertion: .move(edge: .bottom).combined(with: .opacity).combined(with: .scale(scale: 0.9)), removal: .opacity.combined(with: .scale(scale: 0.95))))
        }
    }
}
