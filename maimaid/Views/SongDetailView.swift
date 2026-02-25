import SwiftUI
import SwiftData

struct SongDetailView: View {
    let song: Song
    @Environment(\.modelContext) private var modelContext
    
    @State private var selectedSheet: Sheet? = nil
    @State private var selectedType: String = ""
    @State private var toastMessage: String? = nil
    
    init(song: Song) {
        self.song = song
        let types = Set(song.sheets.map { $0.type.lowercased() })
        if types.contains("dx") {
            _selectedType = State(initialValue: "dx")
        } else if types.contains("std") {
            _selectedType = State(initialValue: "std")
        } else {
            _selectedType = State(initialValue: types.first ?? "")
        }
    }
    
    private var filteredSheets: [Sheet] {
        song.sheets
            .filter { $0.type.lowercased() == selectedType }
            .sorted { difficultyOrder($0.difficulty) < difficultyOrder($1.difficulty) }
    }
    
    private var availableTypes: [String] {
        Array(Set(song.sheets.map { $0.type.lowercased() })).sorted().reversed()
    }
    
    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                // MARK: - Hero Section
                heroSection
                
                // MARK: - Content
                VStack(spacing: 20) {
                    // Metadata pills
                    metadataPills
                    
                    // Type picker
                    if availableTypes.count > 1 {
                        typePicker
                    }
                    
                    // Sheet cards
                    sheetCards
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 40)
            }
        }
        .background(ambientBackground)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $selectedSheet) { sheet in
            ScoreEntryView(sheet: sheet)
        }
        .overlay(alignment: .bottom) {
            if let message = toastMessage {
                Text(message)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.black.opacity(0.8), in: Capsule())
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .padding(.bottom, 24)
                    .zIndex(100)
            }
        }
    }
    
    private func copyToClipboard(_ text: String, label: String) {
        UIPasteboard.general.string = text
        
        // Haptic feedback
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.impactOccurred()
        
        // Show toast
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
            toastMessage = "已复制\(label)"
        }
        
        // Hide toast after delay
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            withAnimation {
                if toastMessage == "已复制\(label)" {
                    toastMessage = nil
                }
            }
        }
    }
    
    // MARK: - Ambient Background
    
    private var ambientBackground: some View {
        ZStack {
            Color(.systemBackground)
            
            SongJacketView(imageName: song.imageName, remoteUrl: song.imageUrl, size: UIScreen.main.bounds.width, cornerRadius: 0)
                .blur(radius: 80)
                .opacity(0.4)
                .ignoresSafeArea()
                .allowsHitTesting(false)
        }
        .ignoresSafeArea()
    }
    
    // MARK: - Hero Section
    
    private var heroSection: some View {
        VStack(spacing: 16) {
            SongJacketView(imageName: song.imageName, remoteUrl: song.imageUrl, size: 220, cornerRadius: 28)
                .shadow(color: .black.opacity(0.3), radius: 24, x: 0, y: 12)
            
            VStack(spacing: 6) {
                Text(song.title)
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.primary)
                    .multilineTextAlignment(.center)
                    .lineLimit(3)
                    .onTapGesture {
                        copyToClipboard(song.title, label: "曲名")
                    }
                
                Text(song.artist)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .onTapGesture {
                        copyToClipboard(song.artist, label: "曲师")
                    }
                
                if let keywords = song.searchKeywords, !keywords.isEmpty {
                    Text(keywords.replacingOccurrences(of: ",", with: " · "))
                        .font(.caption)
                        .foregroundColor(.secondary.opacity(0.6))
                        .multilineTextAlignment(.center)
                        .lineLimit(1)
                }
            }
            .padding(.horizontal, 32)
        }
        .padding(.top, 8)
    }
    
    // MARK: - Metadata Pills
    
    private var metadataPills: some View {
        HStack(spacing: 10) {
            if let bpm = song.bpm {
                metadataPill(icon: "metronome", value: "\(Int(bpm))", label: "BPM")
                    .onTapGesture { copyToClipboard("\(Int(bpm))", label: "BPM") }
            }
            
            metadataPill(icon: "square.grid.2x2", value: song.category, label: nil)
                .onTapGesture { copyToClipboard(song.category, label: "分类") }
            
            if let version = song.version {
                metadataPill(icon: "clock", value: version, label: nil)
                    .onTapGesture { copyToClipboard(version, label: "版本") }
            }
        }
    }
    
    private func metadataPill(icon: String, value: String, label: String?) -> some View {
        HStack(spacing: 5) {
            Image(systemName: icon)
                .font(.system(size: 10, weight: .semibold))
                .foregroundColor(.secondary)
            
            if let label = label {
                Text("\(value)")
                    .font(.system(size: 12, weight: .semibold, design: .rounded))
                    .foregroundColor(.primary)
                +
                Text(" \(label)")
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(.secondary)
            } else {
                Text(value)
                    .font(.system(size: 12, weight: .semibold, design: .rounded))
                    .foregroundColor(.primary)
                    .lineLimit(1)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(.ultraThinMaterial, in: Capsule())
    }
    
    // MARK: - Type Picker
    
    private var typePicker: some View {
        Picker("Version", selection: $selectedType) {
            ForEach(availableTypes, id: \.self) { type in
                Text(type.uppercased() == "STD" ? "标准" : type.uppercased()).tag(type)
            }
        }
        .pickerStyle(.segmented)
    }
    
    // MARK: - Sheet Cards
    
    private var sheetCards: some View {
        VStack(spacing: 12) {
            ForEach(filteredSheets) { sheet in
                SheetCardView(sheet: sheet) {
                    selectedSheet = sheet
                }
            }
        }
    }
    
    private func difficultyOrder(_ difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "basic": return 4
        case "advanced": return 3
        case "expert": return 2
        case "master": return 1
        case "remaster": return 0
        default: return -1
        }
    }
}

// MARK: - Sheet Card View

struct SheetCardView: View {
    let sheet: Sheet
    let onRecord: () -> Void
    @State private var isExpanded = false
    
    private var diffColor: Color {
        colorForDifficulty(sheet.difficulty)
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack(spacing: 0) {
                // Difficulty accent bar
                RoundedRectangle(cornerRadius: 2)
                    .fill(diffColor)
                    .frame(width: 4)
                    .padding(.vertical, 4)
                
                HStack(spacing: 12) {
                    // Difficulty info
                    VStack(alignment: .leading, spacing: 3) {
                        Text(sheet.difficulty.uppercased())
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                            .foregroundColor(diffColor)
                        
                        if let designer = sheet.noteDesigner, !designer.isEmpty {
                            Text(designer)
                                .font(.system(size: 11))
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                        }
                    }
                    
                    Spacer()
                    
                    // Score badge (if exists)
                    if let score = sheet.score {
                        VStack(alignment: .trailing, spacing: 1) {
                            Text(String(format: "%.4f%%", score.rate))
                                .font(.system(size: 12, weight: .bold, design: .monospaced))
                                .foregroundColor(.primary)
                            Text(score.rank)
                                .font(.system(size: 10, weight: .black, design: .rounded))
                                .foregroundColor(diffColor)
                        }
                    }
                    
                    // Level
                    Text(sheet.level)
                        .font(.system(size: 28, weight: .black, design: .rounded))
                        .foregroundColor(diffColor.opacity(0.85))
                        .frame(minWidth: 44)
                    
                    // Expand chevron
                    Image(systemName: "chevron.down")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.secondary.opacity(0.4))
                        .rotationEffect(.degrees(isExpanded ? 180 : 0))
                }
                .padding(.leading, 12)
                .padding(.trailing, 16)
            }
            .padding(.vertical, 14)
            .contentShape(Rectangle())
            .onTapGesture {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                    isExpanded.toggle()
                }
            }
            
            // Expanded content
            if isExpanded {
                expandedContent
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(diffColor.opacity(0.15), lineWidth: 1)
        )
        .animation(.spring(response: 0.35, dampingFraction: 0.8), value: isExpanded)
    }
    
    @ViewBuilder
    private var expandedContent: some View {
        VStack(spacing: 14) {
            // Divider with accent
            Rectangle()
                .fill(diffColor.opacity(0.12))
                .frame(height: 1)
                .padding(.horizontal, 16)
            
            // Internal level
            if let internalLevel = sheet.internalLevel {
                HStack {
                    Text("定数")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.secondary)
                    Spacer()
                    Text(internalLevel)
                        .font(.system(size: 13, weight: .bold, design: .monospaced))
                        .foregroundColor(.primary)
                }
                .padding(.horizontal, 20)
            }
            
            // Note breakdown
            if sheet.total != nil {
                noteBreakdown
            }
            
            // Record button
            Button(action: onRecord) {
                HStack(spacing: 6) {
                    Image(systemName: "pencil.line")
                        .font(.system(size: 12, weight: .semibold))
                    Text("记录成绩")
                        .font(.system(size: 13, weight: .semibold))
                }
                .foregroundColor(diffColor)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(diffColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 10))
            }
            .padding(.horizontal, 16)
        }
        .padding(.bottom, 14)
    }
    
    @ViewBuilder
    private var noteBreakdown: some View {
        let totalWeight = calculateTotalWeight(sheet)
        let items: [(String, Int?, Double, Color)] = [
            ("TAP", sheet.tap, 1.0, .pink),
            ("HOLD", sheet.hold, 2.0, .pink),
            ("SLIDE", sheet.slide, 3.0, .blue),
            ("TOUCH", sheet.touch, 1.0, .blue),
            ("BREAK", sheet.breakCount, 5.0, .orange),
        ]
        
        VStack(spacing: 6) {
            ForEach(items.filter { $0.1 != nil && $0.1! > 0 }, id: \.0) { item in
                let count = item.1!
                let weight = Double(count) * item.2
                let percent = totalWeight > 0 ? weight / totalWeight : 0
                
                HStack(spacing: 8) {
                    Text(item.0)
                        .font(.system(size: 9, weight: .black))
                        .foregroundColor(.secondary)
                        .frame(width: 40, alignment: .leading)
                    
                    // Progress bar
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule()
                                .fill(Color.primary.opacity(0.06))
                            
                            Capsule()
                                .fill(item.3.opacity(0.5))
                                .frame(width: max(4, geo.size.width * percent))
                        }
                    }
                    .frame(height: 6)
                    
                    Text("\(count)")
                        .font(.system(size: 11, weight: .semibold, design: .monospaced))
                        .foregroundColor(.primary)
                        .frame(width: 40, alignment: .trailing)
                    
                    Text("\(Int(percent * 100))%")
                        .font(.system(size: 9, weight: .medium, design: .monospaced))
                        .foregroundColor(.secondary)
                        .frame(width: 30, alignment: .trailing)
                }
            }
        }
        .padding(.horizontal, 20)
    }
    
    private func calculateTotalWeight(_ sheet: Sheet) -> Double {
        (Double(sheet.tap ?? 0) * 1.0) + (Double(sheet.hold ?? 0) * 2.0) +
        (Double(sheet.slide ?? 0) * 3.0) + (Double(sheet.touch ?? 0) * 1.0) +
        (Double(sheet.breakCount ?? 0) * 5.0)
    }
    
    private func colorForDifficulty(_ difficulty: String) -> Color {
        let low = difficulty.lowercased()
        if low.contains("basic") { return Color(.systemGreen) }
        if low.contains("advanced") { return Color(.systemOrange) }
        if low.contains("expert") { return Color(.systemRed) }
        if low.contains("master") { return Color(.systemPurple) }
        if low.contains("remaster") { return Color(red: 0.85, green: 0.65, blue: 1.0) }
        return .pink
    }
}
