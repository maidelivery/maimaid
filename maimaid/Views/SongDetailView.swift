import SwiftUI
import SwiftData

struct SongDetailView: View {
    let song: Song
    @Environment(\.modelContext) private var modelContext
    
    @State private var selectedSheet: Sheet? = nil
    @State private var selectedType: String = ""
    @State private var toastMessage: String? = nil
    @State private var statsService = ChartStatsService.shared
    
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
            .sorted { ThemeUtils.difficultyOrder($0.difficulty) > ThemeUtils.difficultyOrder($1.difficulty) }
    }
    
    private var availableTypes: [String] {
        Array(Set(song.sheets.map { $0.type.lowercased() })).sorted().reversed()
    }
    
    var body: some View {
        SongDetailContent(song: song, selectedType: $selectedType, selectedSheet: $selectedSheet, toastMessage: $toastMessage)
    }
}

struct SongDetailContent: View {
    let song: Song
    @Binding var selectedType: String
    @Binding var selectedSheet: Sheet?
    @Binding var toastMessage: String?
    @Environment(\.modelContext) private var modelContext
    @Query(filter: #Predicate<UserProfile> { $0.isActive }) private var activeProfiles: [UserProfile]
    @State private var statsService = ChartStatsService.shared
    
    private var filteredSheets: [Sheet] {
        song.sheets
            .filter { $0.type.lowercased() == selectedType }
            .sorted { ThemeUtils.difficultyOrder($0.difficulty) > ThemeUtils.difficultyOrder($1.difficulty) }
    }
    
    private var availableTypes: [String] {
        Array(Set(song.sheets.map { $0.type.lowercased() })).sorted().reversed()
    }
    
    private var currentTitle: String {
        let sheetId = filteredSheets.first?.songId ?? 0
        let displayId = sheetId > 0 ? sheetId : song.songId
        return displayId > 0 ? "#\(String(displayId))" : ""
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
                    
                    // Region & Lock status
                    availabilitySection
                    
                    // External search links
                    externalLinksSection
                    
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
        .navigationTitle(currentTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    song.isFavorite.toggle()
                    try? modelContext.save()
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                } label: {
                    Image(systemName: song.isFavorite ? "star.fill" : "star")
                        .foregroundColor(song.isFavorite ? .yellow : .primary)
                }
            }
        }
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
        .task {
            await statsService.fetchStats()
        }
    }
    
    
    private func getJacketImage() -> UIImage? {
        // Try local cache/bundle via ImageDownloader
        if let image = ImageDownloader.shared.loadImage(imageName: song.imageName) {
            return image
        }
        // Fallback or asset
        return UIImage(named: song.imageName)
    }
    
    private func shareImage(_ image: UIImage) {
        let activityVC = UIActivityViewController(activityItems: [image], applicationActivities: nil)
        
        // Find the top most view controller to present
        if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let rootVC = scene.windows.first?.rootViewController {
            
            var topVC = rootVC
            while let presented = topVC.presentedViewController {
                topVC = presented
            }
            
            // For iPad
            if let popover = activityVC.popoverPresentationController {
                popover.sourceView = topVC.view
                popover.sourceRect = CGRect(x: topVC.view.bounds.midX, y: topVC.view.bounds.midY, width: 0, height: 0)
                popover.permittedArrowDirections = []
            }
            
            topVC.present(activityVC, animated: true)
        }
    }
    
    private func showToast(message: String) {
        // Haptic feedback
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.impactOccurred()
        
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
            toastMessage = message
        }
        
        // Hide toast after delay
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            if toastMessage == message {
                withAnimation {
                    toastMessage = nil
                }
            }
        }
    }
    
    private func copyToClipboard(_ text: String, label: String) {
        UIPasteboard.general.string = text
        showToast(message: String(localized: "song.detail.copy.success \(label)"))
    }
    
    // MARK: - Ambient Background
    
    private var ambientBackground: some View {
        ZStack {
            Color(.systemBackground)
            
            GeometryReader { geo in
                SongJacketView(imageName: song.imageName, size: geo.size.width, cornerRadius: 0)
                    .blur(radius: 80)
                    .opacity(0.4)
                    .allowsHitTesting(false)
            }
            .ignoresSafeArea()
        }
        .ignoresSafeArea()
    }
    
    // MARK: - Hero Section
    
    private var heroSection: some View {
        VStack(spacing: 16) {
            SongJacketView(imageName: song.imageName, size: 220, cornerRadius: 28)
                .shadow(color: .black.opacity(0.3), radius: 24, x: 0, y: 12)
                .contextMenu {
                    Button {
                        if let image = getJacketImage() {
                            UIPasteboard.general.image = image
                            showToast(message: String(localized: "song.detail.copy.image"))
                        }
                    } label: {
                        Label("song.detail.copy.title", systemImage: "doc.on.doc")
                    }
                    
                    Button {
                        if let image = getJacketImage() {
                            UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil)
                            showToast(message: String(localized: "song.detail.save.image"))
                        }
                    } label: {
                        Label("song.detail.save.action", systemImage: "square.and.arrow.down")
                    }
                    
                    Button {
                        if let image = getJacketImage() {
                            shareImage(image)
                        }
                    } label: {
                        Label("song.detail.share.action", systemImage: "square.and.arrow.up")
                    }
                }
            
            VStack(spacing: 6) {
                MarqueeText(text: song.title, font: .title2, fontWeight: .bold, color: .primary, alignment: .center)
                    .frame(height: 32)
                    .onTapGesture { copyToClipboard(song.title, label: String(localized: "song.detail.label.title")) }
                
                MarqueeText(text: song.artist, font: .subheadline, color: .secondary, alignment: .center)
                    .frame(height: 20)
                    .onTapGesture { copyToClipboard(song.artist, label: String(localized: "song.detail.label.artist")) }
                
                if let keywords = song.searchKeywords, !keywords.isEmpty {
                    HStack(spacing: 6) {
                        Image(systemName: "tag.fill")
                            .font(.system(size: 8))
                            .foregroundColor(.secondary.opacity(0.4))
                        
                        Text(keywords.replacingOccurrences(of: ",", with: " · "))
                            .font(.system(size: 10))
                            .foregroundColor(.secondary.opacity(0.6))
                    }
                    .padding(.top, 2)
                }
                
                if !song.aliases.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(song.aliases, id: \.self) { alias in
                                Text(alias)
                                    .font(.system(size: 11, weight: .medium))
                                    .foregroundColor(.secondary)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(.secondary.opacity(0.1), in: Capsule())
                                    .onTapGesture {
                                        copyToClipboard(alias, label: String(localized: "profile.edit.titleName")) // Using TitleName or Alias if available, but I'll use common key
                                    }
                            }
                        }
                        .padding(.horizontal, 32)
                    }
                    .padding(.top, 4)
                }
            }
            .padding(.horizontal, song.aliases.isEmpty ? 32 : 0)
        }
        .padding(.top, 8)
    }
    
    // MARK: - Metadata Pills
    
    private var metadataPills: some View {
        ViewThatFits(in: .horizontal) {
            // Priority 1: All in one row (if they fit)
            HStack(spacing: 8) {
                pillsContent(isGrid: false)
            }
            
            // Priority 2: Two per row
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                pillsContent(isGrid: true)
            }
        }
    }
    
    @ViewBuilder
    private func pillsContent(isGrid: Bool) -> some View {
        if let bpm = song.bpm {
            metadataPill(icon: "metronome", value: "\(Int(bpm))", label: "song.detail.metadata.bpm", isGrid: isGrid)
                .onTapGesture { copyToClipboard("\(Int(bpm))", label: String(localized: "song.detail.metadata.bpm")) }
        }
        
        metadataPill(icon: "square.grid.2x2", value: song.category, label: nil, isGrid: isGrid)
            .onTapGesture { copyToClipboard(song.category, label: String(localized: "song.detail.metadata.category")) }
        
        if let version = song.version {
            metadataPill(icon: "clock", value: ThemeUtils.versionAbbreviation(version), label: nil, isGrid: isGrid)
                .onTapGesture { copyToClipboard(version, label: String(localized: "song.detail.metadata.version")) }
        }
        
        if let releaseDate = song.releaseDate {
            let displayDate = isGrid ? releaseDate : formatDate(releaseDate)
            metadataPill(icon: "calendar", value: displayDate, label: nil, isGrid: isGrid)
                .onTapGesture { copyToClipboard(releaseDate, label: String(localized: "song.detail.metadata.releaseDate")) }
        }
    }
    
    private func formatDate(_ date: String) -> String {
        let components = date.components(separatedBy: "-")
        if components.count == 3 {
            let year = String(components[0].suffix(2))
            return "\(year)/\(components[1])/\(components[2])"
        }
        return date
    }
    
    private func metadataPill(icon: String, value: String, label: LocalizedStringKey?, isGrid: Bool = false) -> some View {
        HStack(spacing: 5) {
            Image(systemName: icon)
                .font(.system(size: 10, weight: .semibold))
                .foregroundColor(.secondary)
            
            if let label = label {
                HStack(spacing: 2) {
                    Text(value)
                        .font(.system(size: 12, weight: .semibold, design: .rounded))
                        .foregroundColor(.primary)
                    Text(label)
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(.secondary)
                }
            } else {
                Text(value)
                    .font(.system(size: 12, weight: .semibold, design: .rounded))
                    .foregroundColor(.primary)
                    .lineLimit(1)
            }
        }
        .frame(maxWidth: isGrid ? .infinity : nil)
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(.ultraThinMaterial, in: Capsule())
    }
    
    // MARK: - Availability Section
    
    private var availabilitySection: some View {
        let allSheets = song.sheets
        // Aggregate: if ANY sheet is available in a region, the song is available there
        let jp = allSheets.contains { $0.regionJp }
        let intl = allSheets.contains { $0.regionIntl }
        let cn = allSheets.contains { $0.regionCn }
        
        return HStack(spacing: 0) {
            // Region flags
            HStack(spacing: 12) {
                regionFlag("🇯🇵", label: "song.detail.region.jp", available: jp)
                regionFlag("🌏", label: "song.detail.region.intl", available: intl)
                regionFlag("🇨🇳", label: "song.detail.region.cn", available: cn)
            }
            
            Spacer()
            
            // Lock status
            if song.isLocked {
                HStack(spacing: 4) {
                    Image(systemName: "lock.fill")
                        .font(.system(size: 10, weight: .semibold))
                    Text("song.detail.lock.required")
                        .font(.system(size: 11, weight: .semibold))
                }
                .foregroundColor(.orange)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(.orange.opacity(0.12), in: Capsule())
            } else {
                HStack(spacing: 4) {
                    Image(systemName: "lock.open.fill")
                        .font(.system(size: 10, weight: .semibold))
                    Text("song.detail.lock.notRequired")
                        .font(.system(size: 11, weight: .semibold))
                }
                .foregroundColor(.green)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(.green.opacity(0.12), in: Capsule())
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(Color.primary.opacity(0.06), lineWidth: 1)
        )
    }
    
    private func regionFlag(_ flag: String, label: LocalizedStringKey, available: Bool) -> some View {
        VStack(spacing: 3) {
            Text(flag)
                .font(.system(size: 22))
                .opacity(available ? 1.0 : 0.25)
                .saturation(available ? 1.0 : 0.0)
            
            Text(label)
                .font(.system(size: 9, weight: .medium))
                .foregroundColor(available ? .primary : .secondary.opacity(0.4))
        }
    }
    
    // MARK: - External Links
    
    private var externalLinksSection: some View {
        let query = song.title.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? song.title
        
        return HStack(spacing: 12) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(.secondary)
            
            externalLinkButton(
                icon: "play.rectangle.fill",
                label: "YouTube",
                color: .red,
                url: "https://www.youtube.com/results?search_query=maimai+\(query)"
            )
            
            externalLinkButton(
                icon: "video.fill",
                label: "Bilibili",
                color: Color(red: 0.0, green: 0.74, blue: 0.95),
                url: "https://search.bilibili.com/all?keyword=maimai+\(query)"
            )
            
            Spacer()
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(Color.primary.opacity(0.06), lineWidth: 1)
        )
    }
    
    private func externalLinkButton(icon: String, label: LocalizedStringKey, color: Color, url: String) -> some View {
        Link(destination: URL(string: url)!) {
            HStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 10, weight: .semibold))
                Text(label)
                    .font(.system(size: 11, weight: .bold))
            }
            .foregroundColor(color)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(color.opacity(0.1), in: Capsule())
            .overlay(Capsule().strokeBorder(color.opacity(0.2), lineWidth: 1))
        }
    }
    
    // MARK: - Type Picker
    
    private var typePicker: some View {
        Picker("Version", selection: $selectedType) {
            ForEach(availableTypes, id: \.self) { type in
                Text(type.uppercased() == "STD" ? String(localized: "scanner.chart.std") : type.uppercased()).tag(type)
            }
        }
        .pickerStyle(.segmented)
    }
    
    // MARK: - Sheet Cards
    
    private var sheetCards: some View {
        VStack(spacing: 12) {
            ForEach(filteredSheets) { sheet in
                let stat = statsService.getStat(for: sheet)
                SheetCardView(sheet: sheet, stat: stat) {
                    selectedSheet = sheet
                }
            }
        }
    }
}



// MARK: - Sheet Card View

struct SheetCardView: View {
    let sheet: Sheet
    let stat: ChartStat?
    let onRecord: () -> Void
    @State private var isExpanded = false
    @State private var isNotesExpanded = false
    @State private var isRatingExpanded = false
    @State private var isHistoryExpanded = false
    @State private var historySortByDate = true
    @State private var historyPage = 1
    @State private var recordToDelete: PlayRecord?
    @State private var showingDeleteConfirm = false
    @Environment(\.modelContext) private var modelContext
    @Query(filter: #Predicate<UserProfile> { $0.isActive }) private var activeProfiles: [UserProfile]
    
    private var diffColor: Color {
        ThemeUtils.colorForDifficulty(sheet.difficulty, sheet.type)
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
                            if sheet.difficulty.lowercased() == "remaster" {
                                Text("RE: MASTER")
                                    .font(.system(size: 13, weight: .bold, design: .rounded))
                                    .foregroundColor(diffColor)
                            } else {
                                Text(sheet.difficulty.uppercased())
                                    .font(.system(size: 13, weight: .bold, design: .rounded))
                                    .foregroundColor(diffColor)
                            }
                        
                        if let designer = sheet.noteDesigner, !designer.isEmpty {
                            Text(designer)
                                .font(.system(size: 11))
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                        }
                        
                    }
                    
                    Spacer()
                    
                    // Score badge (if exists)
                    if let score = ScoreService.shared.score(for: sheet, context: modelContext) {
                        VStack(alignment: .trailing, spacing: 1) {
                            Text(String(format: "%.4f%%", score.rate))
                                .font(.system(size: 12, weight: .bold, design: .monospaced))
                                .foregroundColor(.primary)
                                // add here
                            HStack(spacing: 4) {
                                Text(RatingUtils.calculateRank(achievement: score.rate))
                                    .font(.system(size: 10, weight: .black, design: .rounded))
                                    .foregroundColor(diffColor)
                                
                                if let fc = score.fc, !fc.isEmpty {
                                    Text(ThemeUtils.normalizeFC(fc))
                                        .font(.system(size: 8, weight: .bold, design: .rounded))
                                        .foregroundColor(.white)
                                        .padding(.horizontal, 4)
                                        .padding(.vertical, 1)
                                        .background(ThemeUtils.fcColor(fc), in: RoundedRectangle(cornerRadius: 3))
                                }
                                
                                if let fs = score.fs, !fs.isEmpty {
                                    Text(ThemeUtils.normalizeFS(fs))
                                        .font(.system(size: 8, weight: .bold, design: .rounded))
                                        .foregroundColor(.white)
                                        .padding(.horizontal, 4)
                                        .padding(.vertical, 1)
                                        .background(ThemeUtils.fsColor(fs), in: RoundedRectangle(cornerRadius: 3))
                                }
                            }
                        }
                    }
                    
                    // Level
                    Text(sheet.internalLevel ?? sheet.level)
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
        .alert("song.detail.history.delete.title", isPresented: $showingDeleteConfirm) {
            Button("song.detail.history.delete.confirm", role: .destructive) {
                if let record = recordToDelete {
                    deleteRecord(record)
                }
            }
            Button("song.detail.history.delete.cancel", role: .cancel) {
                recordToDelete = nil
            }
        } message: {
            Text("song.detail.history.delete.message")
        }
    }
    
    @ViewBuilder
    private var chartStatsGrid: some View {
        if let stat = stat {
            HStack(spacing: 0) {
                VStack(spacing: 4) {
                    Text("song.detail.stats.fitDiff")
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                    Text(stat.formattedFitDiff)
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .foregroundColor(.primary)
                }
                .frame(maxWidth: .infinity)
                
                VStack(spacing: 4) {
                    Text("song.detail.stats.avgRate")
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                    Text(stat.formattedAvg)
                        .font(.system(size: 14, weight: .bold, design: .monospaced))
                        .foregroundColor(.primary)
                }
                .frame(maxWidth: .infinity)
                
                VStack(spacing: 4) {
                    Text("song.detail.stats.sampleCount")
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                    Text("\(Int(stat.cnt ?? 0))")
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .foregroundColor(.primary)
                }
                .frame(maxWidth: .infinity)
            }
            .multilineTextAlignment(.center)
            .padding(.horizontal, 16)
            .padding(.top, 4)
            .padding(.bottom, 12)
        }
    }
    
    @ViewBuilder
    private var expandedContent: some View {
        VStack(spacing: 16) {
            // Divider with accent
            Rectangle()
                .fill(diffColor.opacity(0.12))
                .frame(height: 1)
                .padding(.horizontal, 16)
            
            // Chart Stats
            chartStatsGrid
            
            // Detailed Info Table (Notes)
            detailedInfoTable
            
            // Achievement → Rating table
            if let level = sheet.internalLevelValue ?? sheet.levelValue, level > 0 {
                ratingTable(level: level)
            }
            
            // Fault Tolerance Calculator
            if sheet.total != nil {
                FaultToleranceCalculatorView(sheet: sheet, diffColor: diffColor)
            }
            
            // Play History Table
            let records = ScoreService.shared.playHistory(for: sheet, context: modelContext)
            if !records.isEmpty {
                playHistoryTable(records: records, diffColor: diffColor)
            }
            
            // Record button
            Button(action: onRecord) {
                HStack(spacing: 6) {
                    Image(systemName: "pencil.line")
                        .font(.system(size: 12, weight: .semibold))
                    Text("song.detail.action.record")
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
    
    private var detailedInfoTable: some View {
        VStack(spacing: 0) {
            if sheet.total != nil {
                Button {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                        isNotesExpanded.toggle()
                    }
                } label: {
                    HStack {
                        Text("song.detail.section.notes")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.primary)
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.secondary.opacity(0.4))
                            .rotationEffect(.degrees(isNotesExpanded ? 90 : 0))
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, isNotesExpanded ? 8 : 0)
                }
                
                if isNotesExpanded {
                    noteBreakdown
                        .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }
        }
    }
    
    private func detailRow(label: String, value: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label)
                .font(.system(size: 11))
                .foregroundColor(.secondary)
                .frame(width: 80, alignment: .leading)
            
            Text(value)
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(.primary)
                .multilineTextAlignment(.trailing)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 8)
        .border(Color.primary.opacity(0.03), width: 0.5)
    }
    
    // MARK: - Rating Table
    
    private func ratingTable(level: Double) -> some View {
        let ratings = RatingUtils.rankThresholds.filter { $0.rank != "AP+" }.map { item in
            (item.rank, item.threshold, RatingUtils.calculateRating(internalLevel: level, achievements: item.threshold))
        }.reversed() // Reverse to SSS+ -> D
        return VStack(spacing: 0) {
            // Header
            Button {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                    isRatingExpanded.toggle()
                }
            } label: {
                HStack {
                    Text("song.detail.section.rating")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.primary)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.secondary.opacity(0.4))
                        .rotationEffect(.degrees(isRatingExpanded ? 90 : 0))
                }
                .padding(.horizontal, 20)
                .padding(.bottom, isRatingExpanded ? 8 : 0)
            }
            
            if isRatingExpanded {
                VStack(spacing: 0) {
                    // Table header row
                    HStack {
                        Text("song.detail.table.achievement")
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text("song.detail.table.rating")
                            .frame(width: 50, alignment: .trailing)
                        Text("song.detail.table.delta")
                            .frame(width: 40, alignment: .trailing)
                    }
                    .font(.system(size: 9, weight: .bold))
                    .foregroundColor(.secondary)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 4)
                    
                    // Table rows
                    let ratingArray = Array(ratings)
                    ForEach(Array(ratingArray.enumerated()), id: \.offset) { index, item in
                        let (rank, ach, rating) = item
                        // Delta compared to the next rank (lower rank) since we're now SSS+ -> D
                        let nextRating = index < ratingArray.count - 1 ? ratingArray[index + 1].2 : 0
                        let delta = index < ratingArray.count - 1 ? rating - nextRating : 0
                        
                        HStack {
                            HStack(spacing: 6) {
                                Text(rank)
                                    .font(.system(size: 11, weight: .black, design: .rounded))
                                    .foregroundColor(RatingUtils.colorForRank(rank))
                                    .frame(width: 36, alignment: .leading)
                                
                                Text(String(format: "%.4f%%", ach))
                                    .font(.system(size: 11, design: .monospaced))
                                    .foregroundColor(.primary)
                            }
                            
                            Spacer()
                            
                            Text("\(rating)")
                                .font(.system(size: 12, weight: .bold, design: .monospaced))
                                .foregroundColor(.primary)
                                .frame(width: 50, alignment: .trailing)
                            
                            if delta > 0 {
                                Text("↑\(delta)")
                                    .font(.system(size: 9, weight: .medium, design: .monospaced))
                                    .foregroundColor(.secondary)
                                    .frame(width: 40, alignment: .trailing)
                            } else {
                                Text("")
                                    .frame(width: 40)
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 5)
                        .background(index % 2 == 0 ? Color.primary.opacity(0.02) : Color.clear)
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
    }
    
    
    // MARK: - Play History Table
    
    private func playHistoryTable(records: [PlayRecord], diffColor: Color) -> some View {
        let sortedRecords = records.sorted { a, b in
            if historySortByDate {
                return a.playDate > b.playDate
            } else {
                return a.rate > b.rate
            }
        }
        
        let bestRecordId = records.max(by: { $0.rate < $1.rate })?.id
        
        let itemsPerPage = 5
        let totalPages = max(1, Int(ceil(Double(sortedRecords.count) / Double(itemsPerPage))))
        let validPage = max(1, min(historyPage, totalPages))
        let startIndex = (validPage - 1) * itemsPerPage
        let endIndex = min(startIndex + itemsPerPage, sortedRecords.count)
        let displayRecords = Array(sortedRecords[startIndex..<endIndex])
        
        return VStack(spacing: 0) {
            // Header
            Button {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                    isHistoryExpanded.toggle()
                }
            } label: {
                HStack {
                    Text("song.detail.section.history")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.primary)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.secondary.opacity(0.4))
                        .rotationEffect(.degrees(isHistoryExpanded ? 90 : 0))
                }
                .padding(.horizontal, 20)
                .padding(.bottom, isHistoryExpanded ? 8 : 0)
            }
            
            if isHistoryExpanded {
                VStack(spacing: 0) {
                    // Controls
                    HStack {
                        Spacer()
                        Picker("Sort by", selection: $historySortByDate) {
                            Text(String(localized: "song.detail.sort.time")).tag(true)
                            Text(String(localized: "song.detail.sort.rate")).tag(false)
                        }
                        .pickerStyle(.segmented)
                        .frame(width: 140)
                        .scaleEffect(0.8)
                        .onChange(of: historySortByDate) { _, _ in
                            historyPage = 1
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 4)
                    
                    ForEach(Array(displayRecords.enumerated()), id: \.offset) { index, record in
                        HStack(spacing: 12) {
                            // Left: Date
                            VStack(alignment: .leading, spacing: 2) {
                                Text(record.playDate.formatted(.dateTime.year(.twoDigits).month(.defaultDigits).day(.defaultDigits)))
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(.primary)
                                Text(record.playDate, style: .time)
                                    .font(.system(size: 9))
                                    .foregroundColor(.secondary)
                            }
                            .frame(width: 70, alignment: .leading)
                            
                            // Middle: Rate & Rating
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 4) {
                                    Text(record.rank)
                                        .font(.system(size: 11, weight: .black, design: .rounded))
                                        .foregroundColor(RatingUtils.colorForRank(record.rank))
                                    Text(String(format: "%.4f%%", record.rate))
                                        .font(.system(size: 12, weight: .bold, design: .monospaced))
                                        .foregroundColor(.primary)
                                }
                                
                                if record.dxScore > 0 {
                                    HStack(spacing: 2) {
                                        Image(systemName: "star.fill")
                                            .font(.system(size: 8))
                                            .foregroundColor(.yellow)
                                        Text("\(record.dxScore)")
                                            .font(.system(size: 10, weight: .medium, design: .monospaced))
                                            .foregroundColor(.secondary)
                                    }
                                }
                            }
                            
                            Spacer()
                            
                            // Right: Badges
                            VStack(alignment: .trailing, spacing: 4) {
                                HStack(spacing: 4) {
                                    if let fc = record.fc, !fc.isEmpty {
                                        Text(ThemeUtils.normalizeFC(fc))
                                            .font(.system(size: 8, weight: .bold, design: .rounded))
                                            .foregroundColor(.white)
                                            .padding(.horizontal, 4)
                                            .padding(.vertical, 1)
                                            .background(ThemeUtils.fcColor(fc), in: RoundedRectangle(cornerRadius: 3))
                                    }
                                    
                                    if let fs = record.fs, !fs.isEmpty {
                                        Text(ThemeUtils.normalizeFS(fs))
                                            .font(.system(size: 8, weight: .bold, design: .rounded))
                                            .foregroundColor(.white)
                                            .padding(.horizontal, 4)
                                            .padding(.vertical, 1)
                                            .background(ThemeUtils.fsColor(fs), in: RoundedRectangle(cornerRadius: 3))
                                    }
                                    
                                    // Delete button
                                    Button {
                                        recordToDelete = record
                                        showingDeleteConfirm = true
                                    } label: {
                                        Image(systemName: "trash")
                                            .font(.system(size: 10))
                                            .foregroundColor(.red.opacity(0.6))
                                    }
                                }
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 8)
                        .background(
                            Group {
                                if record.id == bestRecordId {
                                    RoundedRectangle(cornerRadius: 8)
                                        .fill(diffColor.opacity(0.1))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 8)
                                                .strokeBorder(diffColor, lineWidth: 1.5)
                                        )
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 2)
                                } else if index % 2 == 0 {
                                    Color.primary.opacity(0.02)
                                }
                            }
                        )
                    }
                    
                    if totalPages > 1 {
                        HStack(spacing: 12) {
                            Button {
                                if historyPage > 1 { historyPage -= 1 }
                            } label: {
                                Image(systemName: "chevron.left")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(historyPage > 1 ? diffColor : .secondary.opacity(0.3))
                                    .padding(8)
                            }
                            .disabled(historyPage <= 1)
                            
                            Menu {
                                Picker("Page", selection: $historyPage) {
                                    ForEach(1...totalPages, id: \.self) { page in
                                        Text("Page \(page)").tag(page)
                                    }
                                }
                            } label: {
                                Text("\(validPage) / \(totalPages)")
                                    .font(.system(size: 12, weight: .bold, design: .monospaced))
                                    .foregroundColor(.primary)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 6)
                                    .background(Color.primary.opacity(0.05), in: Capsule())
                            }
                            
                            Button {
                                if historyPage < totalPages { historyPage += 1 }
                            } label: {
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(historyPage < totalPages ? diffColor : .secondary.opacity(0.3))
                                    .padding(8)
                            }
                            .disabled(historyPage >= totalPages)
                        }
                        .padding(.vertical, 12)
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
    }
    
    private func deleteRecord(_ record: PlayRecord) {
        let profileId = record.userProfileId
        let rate = record.rate
//        let date = record.playDate
        
        // Remove from sheet's playRecords array
        if let index = sheet.playRecords?.firstIndex(where: { $0.id == record.id }) {
            sheet.playRecords?.remove(at: index)
        }
        
        // Delete from model context
        modelContext.delete(record)
        
        // Handle Score fallback if we deleted the best record
        let remainingRecords = sheet.playRecords?.filter { $0.userProfileId == profileId && $0.id != record.id } ?? []
        if let score = ScoreService.shared.score(for: sheet, context: modelContext) {
            if abs(score.rate - rate) < 0.0001 {
                if let nextBest = remainingRecords.max(by: { $0.rate < $1.rate }) {
                    score.rate = nextBest.rate
                    score.rank = nextBest.rank
                    score.dxScore = nextBest.dxScore
                    score.fc = nextBest.fc
                    score.fs = nextBest.fs
                    score.achievementDate = nextBest.playDate
                } else {
                    ScoreService.shared.deleteScore(for: sheet, context: modelContext)
                }
            }
        }
        
        try? modelContext.save()
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
        
        VStack(spacing: 0) {
            ForEach(Array(items.filter { $0.1 != nil && $0.1! > 0 }.enumerated()), id: \.offset) { index, item in
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
                .padding(.horizontal, 20)
                .padding(.vertical, 8)
                .background(index % 2 == 0 ? Color.primary.opacity(0.02) : Color.clear)
            }
        }
    }
    
    private func calculateTotalWeight(_ sheet: Sheet) -> Double {
        (Double(sheet.tap ?? 0) * 1.0) + (Double(sheet.hold ?? 0) * 2.0) +
        (Double(sheet.slide ?? 0) * 3.0) + (Double(sheet.touch ?? 0) * 1.0) +
        (Double(sheet.breakCount ?? 0) * 5.0)
    }
    

}

// MARK: - Fault Tolerance Calculator

struct FaultToleranceCalculatorView: View {
    let sheet: Sheet
    let diffColor: Color
    
    @State private var targetAchievement: Double = 100.5
    
    private let targetRanks = RatingUtils.rankThresholds.filter { $0.rank != "AP+" }
    
    var body: some View {
        VStack(spacing: 12) {
            // Header
            HStack {
                Text("song.detail.calculator.title")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.primary)
                Spacer()
                Text("song.detail.calculator.hint")
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(diffColor)
            }
            .padding(.horizontal, 20)
            
            // Target Picker (Rank Based)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(targetRanks.reversed()) { target in
                        Button {
                            targetAchievement = target.threshold
                        } label: {
                            Text(target.rank)
                                .font(.system(size: 10, weight: .bold))
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(
                                    targetAchievement == target.threshold ? diffColor : Color.primary.opacity(0.05),
                                    in: Capsule()
                                )
                                .foregroundColor(targetAchievement == target.threshold ? .white : .primary)
                        }
                    }
                }
                .padding(.horizontal, 20)
            }
            
            // Results Grid
            let results = calculateTolerance()
            HStack(spacing: 12) {
                toleranceInfoBox(title: "GREAT", value: results.great, color: .pink)
                toleranceInfoBox(title: "GOOD", value: results.good, color: .green)
                toleranceInfoBox(title: "MISS", value: results.miss, color: .red)
            }
            .padding(.horizontal, 20)

        }
    }
    
    private func calculateTolerance() -> (great: Int, good: Int, miss: Int) {
        let totalBaseWeight = (Double(sheet.tap ?? 0) * 1.0) +
                              (Double(sheet.hold ?? 0) * 2.0) +
                              (Double(sheet.slide ?? 0) * 3.0) +
                              (Double(sheet.touch ?? 0) * 1.0) +
                              (Double(sheet.breakCount ?? 0) * 5.0)
        
        guard totalBaseWeight > 0 else { return (0, 0, 0) }
        
        let maxAllowedLoss = 101.0 - targetAchievement
        if maxAllowedLoss <= 0 { return (0, 0, 0) }
        
        // Loss for 1 judgement on a TAP (the smallest unit)
        let tapGreatLoss = (0.2 * 1.0 / totalBaseWeight) * 100.0
        let tapGoodLoss = (0.5 * 1.0 / totalBaseWeight) * 100.0
        let tapMissLoss = (1.0 * 1.0 / totalBaseWeight) * 100.0
        
        // Note: For simplicity and following community standard calculators,
        // we show the tolerance assuming the errors occur on TAPs (the most lenient case).
        // If the user wants precise break/hold loss, it's usually too complex for a quick UI.
        
        let allowedGreat = Int(floor(maxAllowedLoss / tapGreatLoss))
        let allowedGood = Int(floor(maxAllowedLoss / tapGoodLoss))
        let allowedMiss = Int(floor(maxAllowedLoss / tapMissLoss))
        
        let tapCount = sheet.tap ?? 0
        return (min(allowedGreat, tapCount), min(allowedGood, tapCount), min(allowedMiss, tapCount))
    }
    
    private func toleranceInfoBox(title: String, value: Int, color: Color) -> some View {
        VStack(spacing: 4) {
            Text(title)
                .font(.system(size: 8, weight: .black))
                .foregroundColor(color.opacity(0.8))
            
            Text("\(value)")
                .font(.system(size: 16, weight: .bold, design: .monospaced))
                .foregroundColor(.primary)
            
            Text("上限")
                .font(.system(size: 8))
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(color.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .strokeBorder(color.opacity(0.15), lineWidth: 1)
        )
    }
}
