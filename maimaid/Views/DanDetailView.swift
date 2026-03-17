import SwiftUI
import SwiftData

struct DanDetailView: View {
    let category: DanCategory
    @Query private var allSongs: [Song]
    @Environment(\.modelContext) private var modelContext
    
    @State private var scoreCache: [String: Score] = [:]
    
    private var songMap: [String: Song] {
        var map: [String: Song] = [:]
        for song in allSongs {
            // Dan songs should not resolve to songs that only contain Utage charts
            let hasStandardCharts = song.sheets.contains { !$0.type.lowercased().contains("utage") }
            guard hasStandardCharts else { continue }
            
            if map[song.title] == nil {
                map[song.title] = song
            }
        }
        return map
    }
    
    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                ForEach(category.sections) { section in
                    DanSectionCard(
                        categoryTitle: category.title,
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
        .background(Color(.systemGroupedBackground))
        .navigationTitle(category.title)
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await loadScoreCache()
        }
    }
    
    private func loadScoreCache() async {
        try? await Task.sleep(nanoseconds: 50_000_000)
        scoreCache = ScoreService.shared.scoreMap(context: modelContext)
    }
}

// MARK: - Section Card

struct DanSectionCard: View {
    let categoryTitle: String
    let section: DanSection
    let songMap: [String: Song]
    let scoreCache: [String: Score]
    
    private var refs: [DanSheetRef] {
        section.sheets.map { DanSheetRef(raw: $0) }
    }
    
    private var titleTheme: (colors: [Color], border: Color, icon: Color) {
        let title = section.title ?? categoryTitle
        let lower = title.lowercased()
        
        if lower.contains("expert") {
            return (
                colors: [Color.orange.opacity(0.14), Color.red.opacity(0.06)],
                border: Color.orange.opacity(0.18),
                icon: .orange
            )
        }
        
        if lower.contains("master") {
            return (
                colors: [Color.purple.opacity(0.14), Color.indigo.opacity(0.06)],
                border: Color.purple.opacity(0.18),
                icon: .purple
            )
        }
        
        if isShinToUraKaiden(title) {
            return (
                colors: [Color.purple.opacity(0.14), Color.indigo.opacity(0.06)],
                border: Color.purple.opacity(0.18),
                icon: .purple
            )
        }
        
        return (
            colors: [Color.orange.opacity(0.14), Color.red.opacity(0.06)],
            border: Color.orange.opacity(0.18),
            icon: .orange
        )
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let title = section.title, !title.isEmpty {
                HStack(spacing: 8) {
                    Text(title)
                        .font(.system(size: 22, weight: .black, design: .rounded))
                        .foregroundColor(.primary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(
                    LinearGradient(
                        colors: titleTheme.colors,
                        startPoint: .leading,
                        endPoint: .trailing
                    ),
                    in: RoundedRectangle(cornerRadius: 14)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .strokeBorder(titleTheme.border, lineWidth: 1)
                )
            }
            
            if let desc = section.description, !desc.isEmpty {
                DanRequirementPills(raw: desc)
            }
            
            VStack(spacing: 8) {
                ForEach(Array(refs.enumerated()), id: \.offset) { index, ref in
                    if let song = songMap[ref.title] {
                        DanSongRowEnhanced(
                            song: song,
                            ref: ref,
                            description: section.sheetDescriptions?[safe: index],
                            scoreCache: scoreCache
                        )
                    } else {
                        DanSongPlaceholder(
                            ref: ref,
                            description: section.sheetDescriptions?[safe: index]
                        )
                    }
                }
            }
        }
        .padding(14)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 18))
    }
    
    private func isShinToUraKaiden(_ title: String) -> Bool {
        let advancedTitles = [
            "真初段", "真二段", "真三段", "真四段", "真五段", "真六段", "真七段", "真八段", "真九段", "真十段",
            "真皆伝", "真皆传",
            "裏皆伝", "裏皆传", "里皆伝", "里皆传",
            "超初段", "超二段", "超三段", "超四段", "超五段", "超六段", "超七段", "超八段", "超九段", "超十段",
            "檄", "橙", "暁", "晓", "桃", "櫻", "樱", "紫", "菫", "白", "雪", "輝", "辉", "熊", "華", "华", "爽", "煌", "舞", "霸"
        ]
        return advancedTitles.contains { title.contains($0) }
    }
}

// MARK: - Requirement Pills

struct DanRequirementPills: View {
    let raw: String
    
    private var segments: [String] {
        raw.components(separatedBy: "｜")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }
    
    var body: some View {
        let damage = segments[safe: 1].map(parseDamage)
        
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .center) {
                HStack(spacing: 5) {
                    if let first = segments[safe: 0] {
                        compactPill(
                            icon: "heart.fill",
                            text: "Life \(normalizedLife(first))",
                            tint: .pink
                        )
                    }
                    
                    if let third = segments[safe: 2] {
                        compactPill(
                            icon: "plus.circle.fill",
                            text: "Heal \(normalizedHeal(third))",
                            tint: .green
                        )
                    }
                }
                
                Spacer(minLength: 10)
                
                if let damage {
                    HStack(spacing: 5) {
                        compactPill(text: "Great \(damage.great)", tint: .pink)
                        compactPill(text: "Good \(damage.good)", tint: .green)
                        compactPill(text: "Miss \(damage.miss)", tint: .gray)
                    }
                }
            }
            
            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 5) {
                    if let first = segments[safe: 0] {
                        compactPill(
                            icon: "heart.fill",
                            text: "Life \(normalizedLife(first))",
                            tint: .pink
                        )
                    }
                    
                    if let third = segments[safe: 2] {
                        compactPill(
                            icon: "plus.circle.fill",
                            text: "Heal \(normalizedHeal(third))",
                            tint: .green
                        )
                    }
                }
                
                if let damage {
                    HStack(spacing: 5) {
                        compactPill(text: "Great \(damage.great)", tint: .pink)
                        compactPill(text: "Good \(damage.good)", tint: .green)
                        compactPill(text: "Miss \(damage.miss)", tint: .gray)
                    }
                }
            }
        }
    }
    
    private func normalizedLife(_ text: String) -> String {
        text
            .replacingOccurrences(of: "❤", with: "")
            .replacingOccurrences(of: "♥", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
    
    private func normalizedHeal(_ text: String) -> String {
        text.trimmingCharacters(in: .whitespacesAndNewlines)
    }
    
    private func parseDamage(_ text: String) -> (great: String, good: String, miss: String) {
        let parts = text
            .split(separator: "/")
            .map { String($0).trimmingCharacters(in: .whitespacesAndNewlines) }
        
        return (
            great: parts[safe: 0] ?? "-0",
            good: parts[safe: 1] ?? "-0",
            miss: parts[safe: 2] ?? "-0"
        )
    }
    
    private func compactPill(icon: String? = nil, text: String, tint: Color) -> some View {
        HStack(spacing: 4) {
            if let icon {
                Image(systemName: icon)
                    .font(.system(size: 8, weight: .bold))
                    .foregroundColor(tint)
            }
            
            Text(text)
                .font(.system(size: 9, weight: .bold, design: .rounded))
                .foregroundColor(tint)
        }
        .lineLimit(1)
        .padding(.horizontal, 7)
        .padding(.vertical, 4)
        .background(tint.opacity(0.10), in: Capsule())
        .overlay(
            Capsule()
                .strokeBorder(tint.opacity(0.16), lineWidth: 1)
        )
    }
}

// MARK: - Enhanced Song Row

struct DanSongRowEnhanced: View {
    let song: Song
    let ref: DanSheetRef
    let description: String?
    let scoreCache: [String: Score]
    
    private var matchingSheet: Sheet? {
        song.sheets.first {
            !$0.type.lowercased().contains("utage") &&
            $0.type.lowercased() == ref.type.lowercased() &&
            $0.difficulty.lowercased() == ref.difficulty.lowercased()
        }
    }
    
    private var diffColor: Color {
        ThemeUtils.colorForDifficulty(ref.difficulty, ref.type)
    }
    
    private func score(for sheet: Sheet) -> Score? {
        let sheetId = "\(sheet.songIdentifier)_\(sheet.type)_\(sheet.difficulty)"
        return scoreCache[sheetId]
    }
    
    var body: some View {
        NavigationLink(destination: SongDetailView(song: song)) {
            HStack(spacing: 10) {
                RoundedRectangle(cornerRadius: 3)
                    .fill(diffColor)
                    .frame(width: 4)
                    .padding(.vertical, 6)
                
                SongJacketView(
                    imageName: song.imageName,
                    size: 50,
                    cornerRadius: 12,
                    useThumbnail: true
                )
                
                VStack(alignment: .leading, spacing: 6) {
                    Text(song.title)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.primary)
                        .lineLimit(1)
                    
                    HStack(spacing: 5) {
                        Text(ref.type.uppercased() == "STD" ? "STD" : ref.type.uppercased())
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(
                                ref.type.lowercased() == "dx" ? Color.orange : Color.blue,
                                in: RoundedRectangle(cornerRadius: 5)
                            )
                        
                        Text(difficultyDisplayName(ref.difficulty))
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(diffColor)
                        
                        if let sheet = matchingSheet {
                            Text("Lv.\(sheet.internalLevel ?? sheet.level)")
                                .font(.system(size: 12, weight: .semibold, design: .rounded))
                                .foregroundColor(.secondary)
                        }
                        
                        if let desc = description, !desc.isEmpty {
                            Text(desc)
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(.blue)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(Color.blue.opacity(0.12), in: Capsule())
                        }
                    }
                }
                
                Spacer(minLength: 6)
                
                VStack(alignment: .trailing, spacing: 6) {
                    if let sheet = matchingSheet, let score = score(for: sheet) {
                        Text(String(format: "%.4f%%", score.rate))
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundColor(.primary)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 5)
                            .background(diffColor.opacity(0.12), in: Capsule())
                    } else {
                        Text("dan.detail.noRecord")
                            .font(.system(size: 9, weight: .medium))
                            .foregroundColor(.secondary)
                    }
                    
                    Image(systemName: "chevron.right")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.secondary.opacity(0.5))
                }
            }
            .padding(11)
            .background(Color(.tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 15))
            .overlay(
                RoundedRectangle(cornerRadius: 15)
                    .strokeBorder(diffColor.opacity(0.10), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
    
    private func difficultyDisplayName(_ diff: String) -> String {
        switch diff.lowercased() {
        case "basic": return "Basic"
        case "advanced": return "Advanced"
        case "expert": return "Expert"
        case "master": return "Master"
        case "remaster": return "Re:M"
        default: return diff.capitalized
        }
    }
}

// MARK: - Placeholder

struct DanSongPlaceholder: View {
    let ref: DanSheetRef
    let description: String?
    
    private var diffColor: Color {
        ThemeUtils.colorForDifficulty(ref.difficulty, ref.type)
    }
    
    var body: some View {
        HStack(spacing: 10) {
            RoundedRectangle(cornerRadius: 3)
                .fill(diffColor.opacity(0.5))
                .frame(width: 4)
                .padding(.vertical, 6)
            
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.primary.opacity(0.05))
                    .frame(width: 50, height: 50)
                
                Image(systemName: "questionmark")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.secondary.opacity(0.5))
            }
            
            VStack(alignment: .leading, spacing: 6) {
                Text(ref.title)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.secondary)
                    .lineLimit(1)
                
                HStack(spacing: 5) {
                    Text(ref.type.uppercased())
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(
                            ref.type.lowercased() == "dx" ? Color.orange : Color.blue,
                            in: RoundedRectangle(cornerRadius: 7)
                        )
                    
                    Text(displayDifficultyName(ref.difficulty))
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(diffColor.opacity(0.8))
                    
                    if let desc = description, !desc.isEmpty {
                        Text(desc)
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(.blue)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(Color.blue.opacity(0.12), in: Capsule())
                    }
                }
            }
            
            Spacer()
            
            Text("dan.detail.missing")
                .font(.system(size: 9, weight: .medium))
                .foregroundColor(.secondary)
        }
        .padding(11)
        .background(Color(.tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 15))
        .overlay(
            RoundedRectangle(cornerRadius: 15)
                .strokeBorder(Color.primary.opacity(0.06), lineWidth: 1)
        )
    }
    
    private func displayDifficultyName(_ diff: String) -> String {
        switch diff.lowercased() {
        case "basic": return "Basic"
        case "advanced": return "Advanced"
        case "expert": return "Expert"
        case "master": return "Master"
        case "remaster": return "Re:M"
        default: return diff.capitalized
        }
    }
}

// MARK: - Collection Extension

extension Collection {
    subscript(safe index: Index) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
