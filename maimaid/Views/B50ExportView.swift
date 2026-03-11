import SwiftUI

// MARK: - B50 Export Image View
// Designed to be rendered offscreen via ImageRenderer into a UIImage.

struct B50ExportView: View {
    let b35: [RatingUtils.RatingEntry]
    let b15: [RatingUtils.RatingEntry]
    let totalRating: Int
    let userName: String?
    let currentVersion: String? // 当前设定的最新版本
    
    private let columns = 5
    private let cardWidth: CGFloat = 220
    private let cardSpacing: CGFloat = 8
    private let sectionPadding: CGFloat = 24
    
    private var totalWidth: CGFloat {
        CGFloat(columns) * cardWidth + CGFloat(columns - 1) * cardSpacing + sectionPadding * 2
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // MARK: - Header
            headerSection
            
            // MARK: - New Songs Section (B15)
            if !b15.isEmpty {
                sectionView(
                    title: "bestTable.section.new \(b15.count)",
                    subtitle: "Rating: \(b15.reduce(0) { $0 + $1.rating })",
                    entries: b15,
                    accentColor: Color(hex: "#FF6B6B")
                )
            }
            
            // MARK: - Old Songs Section (B35)
            if !b35.isEmpty {
                sectionView(
                    title: "bestTable.section.old \(b35.count)",
                    subtitle: "Rating: \(b35.reduce(0) { $0 + $1.rating })",
                    entries: b35,
                    accentColor: Color(hex: "#4ECDC4")
                )
            }
            
            // MARK: - Footer
            footerSection
        }
        .frame(width: totalWidth)
        .background(Color(hex: "#0F0F13"))
    }
    
    // MARK: - Header
    
    private var headerSection: some View {
        VStack(spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    if let name = userName, !name.isEmpty {
                        Text(name)
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(.white)
                    }
                    
                    Text(String(localized: "bestTable.rating"))
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(Color.white.opacity(0.5))
                }
                
                Spacer()
                
                Text("\(totalRating)")
                    .font(.system(size: 56, weight: .black, design: .rounded))
                    .foregroundStyle(ThemeUtils.ratingGradient(totalRating))
            }
            
            // Summary pills
            HStack(spacing: 12) {
                summaryPill(
                    label: String(localized: "bestTable.section.new \(b15.count)"),
                    value: "\(b15.reduce(0) { $0 + $1.rating })",
                    color: Color(hex: "#FF6B6B")
                )
                summaryPill(
                    label: String(localized: "bestTable.section.old \(b35.count)"),
                    value: "\(b35.reduce(0) { $0 + $1.rating })",
                    color: Color(hex: "#4ECDC4")
                )
                Spacer()
            }
        }
        .padding(sectionPadding)
        .padding(.top, 12)
    }
    
    private func summaryPill(label: String, value: String, color: Color) -> some View {
        HStack(spacing: 6) {
            Circle()
                .fill(color)
                .frame(width: 8, height: 8)
            Text(label)
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(Color.white.opacity(0.6))
            Text(value)
                .font(.system(size: 13, weight: .bold, design: .rounded))
                .foregroundColor(color)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(color.opacity(0.12), in: Capsule())
    }
    
    // MARK: - Section
    
    private func sectionView(title: LocalizedStringKey, subtitle: String, entries: [RatingUtils.RatingEntry], accentColor: Color) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            // Section header
            HStack(alignment: .firstTextBaseline) {
                Text(title)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)
                Text(subtitle)
                    .font(.system(size: 14, weight: .medium, design: .rounded))
                    .foregroundColor(accentColor.opacity(0.8))
                Spacer()
            }
            .padding(.horizontal, sectionPadding)
            
            // Grid of song cards
            let rows = stride(from: 0, to: entries.count, by: columns).map { startIndex in
                Array(entries[startIndex..<min(startIndex + columns, entries.count)])
            }
            
            VStack(spacing: cardSpacing) {
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    HStack(spacing: cardSpacing) {
                        ForEach(row) { entry in
                           songCard(entry: entry, accentColor: accentColor)
                        }
                        // Fill empty slots
                        if row.count < columns {
                            ForEach(0..<(columns - row.count), id: \.self) { _ in
                                Color.clear.frame(width: cardWidth)
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, sectionPadding)
        }
        .padding(.vertical, 16)
    }
    
    // MARK: - Song Card
    
    private func songCard(entry: RatingUtils.RatingEntry, accentColor: Color) -> some View {
        let diffColor = ThemeUtils.colorForDifficulty(entry.diff, entry.type)
        let rank = RatingUtils.calculateRank(achievement: entry.achievement)
        let jacketSize: CGFloat = 62
        
        let dxRatio = entry.maxDxScore > 0 ? (Double(entry.dxScore) / Double(entry.maxDxScore)) : 0
        let stars = starsForDxScore(ratio: dxRatio)
        
        return HStack(spacing: 8) {
            // Left: Jacket image (Small square)
            ZStack(alignment: .bottomTrailing) {
                if let imageName = entry.imageName,
                   let uiImage = ImageDownloader.shared.loadImage(imageName: imageName) {
                    Image(uiImage: uiImage)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: jacketSize, height: jacketSize)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                } else {
                    Rectangle()
                        .fill(Color.white.opacity(0.05))
                        .frame(width: jacketSize, height: jacketSize)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                        .overlay(
                            Image(systemName: "music.note")
                                .font(.system(size: 14))
                                .foregroundColor(.white.opacity(0.2))
                        )
                }
                
                // Achievement Rank Small Badge (Optional: if we want to show it on image)
                // ID overlay at bottom
                if entry.songId > 0 {
                    Text("#\(entry.songId)")
                        .font(.system(size: 8, weight: .bold, design: .monospaced))
                        .foregroundColor(.white)
                        .padding(.horizontal, 3)
                        .padding(.vertical, 0.5)
                        .background(Color.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 2))
                        .padding(2)
                }
            }
            
            // Right: Info
            VStack(alignment: .leading, spacing: 2) {
                // Row 1: Title
                Text(entry.songTitle)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .frame(maxWidth: .infinity, alignment: .leading)
                
                // Row 2: Rank + Achievement + Stars
                HStack(spacing: 4) {
                    Text(rank)
                        .font(.system(size: 12, weight: .black, design: .rounded))
                        .foregroundColor(RatingUtils.colorForRank(rank))
                    
                    Text(String(format: "%.4f%%", entry.achievement))
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundColor(Color.white.opacity(0.6))
                    
                    Spacer()
                    
                    if stars > 0 {
                        HStack(spacing: 2) {
                            Text("\(stars)")
                                .font(.system(size: 8, weight: .bold))
                            Image(systemName: "star.fill")
                                .font(.system(size: 8))
                        }
                        .foregroundColor(.yellow)
                    }
                }
                
                // Row 3: Level -> Rating + DX Score
                HStack(spacing: 4) {
                    HStack(spacing: 2) {
                        Text(String(format: "%.1f", entry.level))
                            .font(.system(size: 9, weight: .bold, design: .rounded))
                            .foregroundColor(diffColor)
                        
                        Image(systemName: "arrow.right")
                            .font(.system(size: 5))
                            .foregroundColor(Color.white.opacity(0.2))
                        
                        Text("\(entry.rating)")
                            .font(.system(size: 9, weight: .black, design: .rounded))
                            .foregroundColor(Color(hex: "#FFD700"))
                    }
                    
                    Spacer()
                    
                    if entry.maxDxScore > 0 {
                        Text("\(entry.dxScore)/\(entry.maxDxScore)")
                            .font(.system(size: 8, weight: .medium, design: .monospaced))
                            .foregroundColor(.white.opacity(0.4))
                    }
                }
                
                // Row 4: Badges
                HStack(spacing: 3) {
                    exportBadge(text: entry.type, color: entry.type == "DX" ? .orange : .blue)
                    
                    if let fc = entry.fc, !fc.isEmpty {
                        exportBadge(text: ThemeUtils.normalizeFC(fc), color: ThemeUtils.fcColor(fc))
                    }
                    
                    if let fs = entry.fs, !fs.isEmpty {
                        exportBadge(text: ThemeUtils.normalizeFS(fs), color: ThemeUtils.fsColor(fs))
                    }
                }
            }
            
            Spacer(minLength: 0)
        }
        .padding(6)
        .frame(width: cardWidth)
        .background(diffColor.opacity(0.15))
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .overlay(
            RoundedRectangle(cornerRadius: 6)
                .stroke(diffColor.opacity(0.2), lineWidth: 0.5)
        )
    }
    
    // MARK: - Export Badge
    
    private func starsForDxScore(ratio: Double) -> Int {
        if ratio >= 0.97 { return 5 }
        if ratio >= 0.95 { return 4 }
        if ratio >= 0.93 { return 3 }
        if ratio >= 0.90 { return 2 }
        if ratio >= 0.85 { return 1 }
        return 0
    }
    
    private func exportBadge(text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 7, weight: .heavy))
            .foregroundColor(.white)
            .padding(.horizontal, 4)
            .padding(.vertical, 1.5)
            .background(color, in: RoundedRectangle(cornerRadius: 2))
    }
    
    // MARK: - Footer
    
    private var footerSection: some View {
        VStack(spacing: 8) {
            // Version indicator
            if let version = currentVersion, !version.isEmpty {
                HStack(spacing: 4) {
                    Image(systemName: "gamecontroller.fill")
                        .font(.system(size: 10))
                    Text(ThemeUtils.versionAbbreviation(version))
                        .font(.system(size: 11, weight: .semibold))
                }
                .foregroundColor(Color.white.opacity(0.3))
            }
            
            HStack {
                Spacer()
                Text(String(localized: "bestTable.export.watermark"))
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Color.white.opacity(0.2))
                Spacer()
            }
        }
        .padding(.vertical, 24)
    }
}

// MARK: - Image Generation Helper

extension B50ExportView {
    @MainActor
    static func renderImage(
        b35: [RatingUtils.RatingEntry],
        b15: [RatingUtils.RatingEntry],
        totalRating: Int,
        userName: String?,
        currentVersion: String? = nil
    ) -> UIImage? {
        let view = B50ExportView(
            b35: b35,
            b15: b15,
            totalRating: totalRating,
            userName: userName,
            currentVersion: currentVersion
        )
        
        let renderer = ImageRenderer(content: view)
        renderer.scale = 2.0 // Retina quality
        
        return renderer.uiImage
    }
}
