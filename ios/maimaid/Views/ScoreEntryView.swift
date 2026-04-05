import SwiftUI
import SwiftData
import PhotosUI

struct ScoreEntryView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.colorScheme) private var colorScheme
    
    let sheet: Sheet
    
    let initialRate: Double?
    let initialRank: String?
    let initialDxScore: Int?
    let initialFC: String?
    let initialFS: String?
    
    @State private var isProcessingPhoto = false
    @State private var recognizedRate: Double?
    @State private var selectedItem: PhotosPickerItem? = nil
    @State private var selectedImage: UIImage? = nil
    
    @State private var rateText = ""
    @State private var dxScoreText = ""
    @State private var isSaved = false
    @FocusState private var isRateFocused: Bool
    @FocusState private var isDxScoreFocused: Bool
    
    @State private var selectedFC: String? = nil
    @State private var selectedFS: String? = nil
    @State private var cachedCurrentScore: Score? = nil
    
    @ScaledMetric(relativeTo: .title3) private var headerAccentHeight = 50
    @ScaledMetric(relativeTo: .largeTitle) private var achievementFontSize = 48
    @ScaledMetric(relativeTo: .title3) private var achievementPercentFontSize = 24
    @ScaledMetric(relativeTo: .body) private var dxScoreFieldMinWidth = 88
    @ScaledMetric(relativeTo: .body) private var photoThumbnailSize = 60
    
    init(sheet: Sheet, initialRate: Double? = nil, initialRank: String? = nil, initialDxScore: Int? = nil, initialFC: String? = nil, initialFS: String? = nil) {
        self.sheet = sheet
        self.initialRate = initialRate
        self.initialRank = initialRank
        self.initialDxScore = initialDxScore
        self.initialFC = initialFC
        self.initialFS = initialFS
    }
    
    private var trimmedRateText: String {
        rateText.trimmingCharacters(in: .whitespacesAndNewlines)
    }
    
    private var trimmedDxScoreText: String {
        dxScoreText.trimmingCharacters(in: .whitespacesAndNewlines)
    }
    
    private var parsedRate: Double? {
        Double(trimmedRateText)
    }
    
    private var parsedDxScore: Int? {
        Int(trimmedDxScoreText)
    }
    
    private var maxDxScore: Int {
        (sheet.total ?? 0) * 3
    }
    
    private var diffColor: Color {
        ThemeUtils.colorForDifficulty(sheet.difficulty, sheet.type, colorScheme)
    }
    
    private var chartTypeLabel: String {
        sheet.type.uppercased() == "STD" ? String(localized: "scanner.chart.std") : sheet.type.uppercased()
    }
    
    private var difficultyLabel: String {
        sheet.difficulty.lowercased() == "remaster" ? "Re:MASTER" : sheet.difficulty.capitalized
    }
    
    private var levelLabel: String {
        "Lv.\(sheet.internalLevel ?? sheet.level)"
    }
    
    private var displayedRank: String {
        if let rate = parsedRate {
            return RatingUtils.calculateRank(achievement: rate)
        }
        if let initialRank, !initialRank.isEmpty {
            return initialRank
        }
        if let cachedCurrentScore {
            return cachedCurrentScore.rank
        }
        return "—"
    }
    
    private var displayedRankColor: Color {
        displayedRank == "—" ? .secondary : RatingUtils.colorForRank(displayedRank)
    }
    
    private var selectedFCDisplayText: String {
        guard let selectedFC, !selectedFC.isEmpty else {
            return String(localized: "score.entry.combo")
        }
        
        switch selectedFC {
        case "fcp":
            return "FC+"
        case "app":
            return "AP+"
        default:
            return selectedFC.uppercased()
        }
    }
    
    private var selectedFSDisplayText: String {
        guard let selectedFS, !selectedFS.isEmpty else {
            return String(localized: "score.entry.sync")
        }
        
        switch selectedFS {
        case "sync":
            return "S"
        case "fs":
            return "FS"
        case "fsp":
            return "FS+"
        case "fsd":
            return "FDX"
        case "fsdp":
            return "FDX+"
        default:
            return selectedFS.uppercased()
        }
    }
    
    private var rateValidationMessage: String? {
        guard !trimmedRateText.isEmpty else { return nil }
        guard let rate = parsedRate else {
            return String(localized: "score.entry.validation.rateFormat")
        }
        guard rate >= 0 && rate <= 101 else {
            return String(localized: "score.entry.validation.rateRange")
        }
        return nil
    }
    
    private var dxScoreValidationMessage: String? {
        guard !trimmedDxScoreText.isEmpty else { return nil }
        guard let dxScore = parsedDxScore, dxScore >= 0 else {
            return String(localized: "score.entry.validation.dxFormat")
        }
        guard maxDxScore == 0 || dxScore <= maxDxScore else {
            return String(format: String(localized: "score.entry.validation.dxRange %lld"), maxDxScore)
        }
        return nil
    }
    
    private var validationMessage: (text: String, color: Color, icon: String)? {
        if let rateValidationMessage {
            return (rateValidationMessage, .red, "exclamationmark.circle.fill")
        }
        if let dxScoreValidationMessage {
            return (dxScoreValidationMessage, .red, "exclamationmark.circle.fill")
        }
        return nil
    }
    
    private var savedMessage: (text: String, color: Color, icon: String)? {
        if isSaved {
            return (String(localized: "score.entry.savedHint"), .green, "checkmark.circle.fill")
        }
        return nil
    }
    
    private var isValid: Bool {
        guard let rate = parsedRate else { return false }
        let rateValid = rate >= 0 && rate <= 101.0
        
        if let dxScore = parsedDxScore {
            let dxValid = dxScore >= 0 && (maxDxScore == 0 || dxScore <= maxDxScore)
            return rateValid && dxValid
        }
        
        return rateValid
    }
    
    private var saveButtonBackground: Color {
        if isSaved {
            return .green
        } else if isValid {
            return diffColor
        } else {
            return .gray
        }
    }
    
    private var feedbackAnimation: Animation {
        reduceMotion ? .easeOut(duration: 0.15) : .spring(response: 0.3, dampingFraction: 0.82)
    }
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    headerCard
                    scoreInputSection
                    photoScanSection
                    
                    if let existingScore = cachedCurrentScore {
                        existingScoreCard(existingScore)
                    }
                    
                    if let savedMessage {
                        Label {
                            Text(savedMessage.text)
                                .font(.footnote)
                        } icon: {
                            Image(systemName: savedMessage.icon)
                        }
                        .foregroundStyle(savedMessage.color)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    
                    saveButton
                }
                .padding(20)
                .padding(.bottom, 32)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Color(.systemGroupedBackground))
            .navigationTitle("score.entry.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("profile.edit.cancel") { dismiss() }
                }
                
                if isSaved {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("filter.done") { dismiss() }
                    }
                }
                
                ToolbarItemGroup(placement: .keyboard) {
                    if isRateFocused {
                        Button("score.entry.keyboard.next") {
                            isRateFocused = false
                            isDxScoreFocused = true
                        }
                        
                        Spacer()
                    }
                    
                    Button("filter.done") {
                        dismissKeyboard()
                    }
                }
            }
        }
        .onAppear {
            loadInitialValues()
        }
        .onChange(of: rateText) { _, _ in
            resetSaveStateIfNeeded()
        }
        .onChange(of: dxScoreText) { _, _ in
            resetSaveStateIfNeeded()
        }
        .onChange(of: selectedFC) { _, _ in
            resetSaveStateIfNeeded()
        }
        .onChange(of: selectedFS) { _, _ in
            resetSaveStateIfNeeded()
        }
        .onChange(of: selectedItem) { _, newItem in
            Task {
                await processSelectedItem(newItem)
            }
        }
    }
    
    private var headerCard: some View {
        HStack(spacing: 16) {
            RoundedRectangle(cornerRadius: 3)
                .fill(diffColor)
                .frame(width: 5, height: headerAccentHeight)
            
            VStack(alignment: .leading, spacing: 4) {
                if let song = sheet.song {
                    Text(song.title)
                        .font(.headline.bold())
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                }
                
                HStack(spacing: 4) {
                    Text(chartTypeLabel)
                        .font(.caption.bold())
                        .foregroundStyle(.white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(ThemeUtils.badgeColorForChartType(sheet.type, colorScheme), in: Capsule())
                    
                    Text(difficultyLabel)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(diffColor)
                }
            }
            
            Spacer(minLength: 12)
            
            Text(levelLabel)
                .font(.title2.bold())
                .fontDesign(.rounded)
                .foregroundStyle(diffColor)
                .multilineTextAlignment(.trailing)
        }
        .fontDesign(.rounded)
        .padding(16)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
        .accessibilityElement(children: .combine)
    }
    
    private var scoreInputSection: some View {
        VStack(spacing: 20) {
            VStack(spacing: 12) {
                Text("song.detail.table.achievement")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    TextField("0.0000", text: $rateText)
                        .font(.system(size: achievementFontSize, weight: .black, design: .rounded))
                        .monospacedDigit()
                        .foregroundStyle(.primary)
                        .multilineTextAlignment(.center)
                        .keyboardType(.decimalPad)
                        .submitLabel(.next)
                        .focused($isRateFocused)
                        .accessibilityLabel(Text("song.detail.table.achievement"))
                    
                    Text("%")
                        .font(.system(size: achievementPercentFontSize, weight: .bold, design: .rounded))
                        .foregroundStyle(.secondary)
                        .accessibilityHidden(true)
                }
                .frame(maxWidth: .infinity)
            }
            
            HStack(spacing: 12) {
                HStack(spacing: 8) {
                    Image(systemName: "trophy.fill")
                        .foregroundStyle(displayedRankColor)
                        .accessibilityHidden(true)
                    
                    Text(displayedRank)
                        .font(.title3.bold())
                        .fontDesign(.rounded)
                        .foregroundStyle(displayedRankColor)
                        .monospacedDigit()
                }
                .frame(maxWidth: .infinity)
                .frame(minHeight: 44)
                .padding(.horizontal, 12)
                .background(displayedRank == "—" ? Color.secondary.opacity(0.08) : displayedRankColor.opacity(0.12), in: Capsule())
                .accessibilityElement(children: .combine)
                .accessibilityLabel(Text("score.entry.rank"))
                .accessibilityValue(Text(displayedRank == "—" ? String(localized: "score.entry.rank.pending") : displayedRank))
                
                HStack(spacing: 8) {
                    Image(systemName: "star.fill")
                        .foregroundStyle(dxScoreText.isEmpty ? AnyShapeStyle(.secondary) : AnyShapeStyle(.yellow))
                        .accessibilityHidden(true)
                    
                    TextField(String(localized: "score.entry.dxScore"), text: $dxScoreText)
                        .font(.body.bold())
                        .fontDesign(.rounded)
                        .monospacedDigit()
                        .foregroundStyle((parsedDxScore ?? 0) > maxDxScore && maxDxScore > 0 ? .red : .primary)
                        .frame(minWidth: dxScoreFieldMinWidth)
                        .keyboardType(.numberPad)
                        .submitLabel(.done)
                        .focused($isDxScoreFocused)
                        .accessibilityLabel(Text("score.entry.dxScore"))
                    
                    if maxDxScore > 0 {
                        Text("/ \(maxDxScore)")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .monospacedDigit()
                            .accessibilityHidden(true)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(minHeight: 44)
                .padding(.horizontal, 12)
                .background(dxScoreText.isEmpty ? Color.secondary.opacity(0.08) : Color.yellow.opacity(0.12), in: Capsule())
            }
            
            if let validationMessage {
                Label {
                    Text(validationMessage.text)
                        .font(.footnote)
                } icon: {
                    Image(systemName: validationMessage.icon)
                }
                .foregroundStyle(validationMessage.color)
                .frame(maxWidth: .infinity, alignment: .leading)
                .transition(.opacity)
            }
            
            HStack(spacing: 12) {
                Menu {
                    Picker(String(localized: "score.entry.combo"), selection: $selectedFC) {
                        Text("None").tag(String?.none)
                        Text("FC").tag(String?("fc"))
                        Text("FC+").tag(String?("fcp"))
                        Text("AP").tag(String?("ap"))
                        Text("AP+").tag(String?("app"))
                    }
                } label: {
                    Label {
                        Text(selectedFCDisplayText)
                            .font(.subheadline.bold())
                            .lineLimit(1)
                    } icon: {
                        Image(systemName: "target")
                    }
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 44)
                    .padding(.horizontal, 12)
                    .background((selectedFC?.isEmpty ?? true) ? Color.secondary.opacity(0.08) : Color.green.opacity(0.12), in: Capsule())
                    .foregroundStyle((selectedFC?.isEmpty ?? true) ? AnyShapeStyle(.secondary) : AnyShapeStyle(.green))
                }
                .accessibilityLabel(Text("score.entry.combo"))
                .accessibilityValue(Text(selectedFCDisplayText))
                
                Menu {
                    Picker(String(localized: "score.entry.sync"), selection: $selectedFS) {
                        Text("None").tag(String?.none)
                        Text("S").tag(String?("sync"))
                        Text("FS").tag(String?("fs"))
                        Text("FS+").tag(String?("fsp"))
                        Text("FDX").tag(String?("fsd"))
                        Text("FDX+").tag(String?("fsdp"))
                    }
                } label: {
                    Label {
                        Text(selectedFSDisplayText)
                            .font(.subheadline.bold())
                            .lineLimit(1)
                    } icon: {
                        Image(systemName: "person.2.fill")
                    }
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 44)
                    .padding(.horizontal, 12)
                    .background((selectedFS?.isEmpty ?? true) ? Color.secondary.opacity(0.08) : Color.blue.opacity(0.12), in: Capsule())
                    .foregroundStyle((selectedFS?.isEmpty ?? true) ? AnyShapeStyle(.secondary) : AnyShapeStyle(.blue))
                }
                .accessibilityLabel(Text("score.entry.sync"))
                .accessibilityValue(Text(selectedFSDisplayText))
            }
            .animation(feedbackAnimation, value: selectedFC)
            .animation(feedbackAnimation, value: selectedFS)
        }
        .padding(24)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20))
    }
    
    private var photoScanSection: some View {
        VStack(spacing: 12) {
            HStack {
                Image(systemName: "camera.viewfinder")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.blue)
                    .accessibilityHidden(true)
                Text("score.entry.selectPhoto")
                    .font(.headline)
                    .foregroundStyle(.primary)
                Spacer()
            }
            
            if isProcessingPhoto {
                HStack(spacing: 10) {
                    ProgressView()
                    Text("score.entry.recognizing")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 20)
            } else if let image = selectedImage {
                HStack(spacing: 12) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .frame(width: photoThumbnailSize, height: photoThumbnailSize)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .accessibilityHidden(true)
                    
                    VStack(alignment: .leading, spacing: 6) {
                        if let rate = recognizedRate {
                            Label("score.entry.success", systemImage: "checkmark.circle.fill")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.green)
                            
                            Text("\(rate, format: .number.precision(.fractionLength(4)))%")
                                .font(.headline.bold())
                                .monospacedDigit()
                                .foregroundStyle(.primary)
                        } else {
                            Label("score.entry.failed", systemImage: "exclamationmark.triangle.fill")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.orange)
                            
                            Text("score.entry.manualHint")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                    
                    Spacer()
                    
                    PhotosPicker(selection: $selectedItem, matching: .images) {
                        Image(systemName: "arrow.triangle.2.circlepath")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.blue)
                            .frame(width: 44, height: 44)
                            .background(Color.blue.opacity(0.1), in: Circle())
                    }
                    .accessibilityLabel(Text("score.entry.repickPhoto"))
                    .accessibilityHint(Text("score.entry.repickPhoto.hint"))
                }
            } else {
                PhotosPicker(selection: $selectedItem, matching: .images) {
                    Label("score.entry.selectPhoto", systemImage: "photo")
                        .font(.headline)
                        .foregroundStyle(.blue)
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 52)
                        .background(Color.blue.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .strokeBorder(Color.blue.opacity(0.2), style: StrokeStyle(lineWidth: 1, dash: [6, 4]))
                        )
                }
            }
        }
        .padding(16)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
    }
    
    private func existingScoreCard(_ score: Score) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "clock.arrow.circlepath")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)
            
            VStack(alignment: .leading, spacing: 4) {
                Text("score.entry.currentBest")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                
                Text("\(score.rate, format: .number.precision(.fractionLength(4)))% · \(score.rank)")
                    .font(.headline.bold())
                    .monospacedDigit()
                    .foregroundStyle(.primary)
            }
            
            Spacer()
            
            Text(score.achievementDate, style: .date)
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .padding(14)
        .background(Color.primary.opacity(0.03), in: RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .combine)
    }
    
    private var saveButton: some View {
        Button {
            if isSaved {
                dismiss()
            } else {
                dismissKeyboard()
                saveScore()
            }
        } label: {
            Label {
                Text(isSaved ? String(localized: "filter.done") : String(localized: "score.entry.save"))
                    .font(.headline.bold())
            } icon: {
                Image(systemName: isSaved ? "checkmark.circle.fill" : "square.and.arrow.down")
                    .font(.body.weight(.semibold))
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 52)
            .background(saveButtonBackground, in: RoundedRectangle(cornerRadius: 16))
        }
        .disabled(!isSaved && !isValid)
        .animation(feedbackAnimation, value: isSaved)
    }
    
    private func loadInitialValues() {
        cachedCurrentScore = ScoreService.shared.score(for: sheet, context: modelContext)
        
        if let initialRate {
            rateText = String(format: "%.4f", initialRate)
        } else if let cachedCurrentScore {
            rateText = String(format: "%.4f", cachedCurrentScore.rate)
        }
        
        if let initialDxScore {
            dxScoreText = "\(initialDxScore)"
        } else if let cachedCurrentScore {
            dxScoreText = cachedCurrentScore.dxScore > 0 ? "\(cachedCurrentScore.dxScore)" : ""
        }
        
        if let initialFC, !initialFC.isEmpty {
            selectedFC = initialFC
        } else if let cachedCurrentScore, let fc = cachedCurrentScore.fc, !fc.isEmpty {
            selectedFC = fc
        }
        
        if let initialFS, !initialFS.isEmpty {
            selectedFS = initialFS
        } else if let cachedCurrentScore, let fs = cachedCurrentScore.fs, !fs.isEmpty {
            selectedFS = fs
        }
    }
    
    private func processSelectedItem(_ item: PhotosPickerItem?) async {
        guard let item else { return }
        
        recognizedRate = nil
        isProcessingPhoto = true
        defer { isProcessingPhoto = false }
        
        guard let data = try? await item.loadTransferable(type: Data.self),
              let image = UIImage(data: data) else {
            selectedImage = nil
            return
        }
        
        selectedImage = image
        let result = await MLScoreProcessor.shared.process(image)
        
        guard let rate = result.rate else {
            recognizedRate = nil
            return
        }
        
        recognizedRate = rate
        withAnimation(feedbackAnimation) {
            rateText = String(format: "%.4f", rate)
        }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
    
    private func dismissKeyboard() {
        isRateFocused = false
        isDxScoreFocused = false
    }
    
    private func resetSaveStateIfNeeded() {
        guard isSaved else { return }
        
        withAnimation(feedbackAnimation) {
            isSaved = false
        }
    }
    
    private func saveScore() {
        guard let rate = parsedRate, isValid else { return }
        
        _ = ScoreService.shared.recordPlay(
            sheet: sheet,
            rate: rate,
            rank: displayedRank,
            dxScore: parsedDxScore ?? 0,
            fc: selectedFC,
            fs: selectedFS,
            context: modelContext
        )
        
        let savedScore = ScoreService.shared.saveScore(
            sheet: sheet,
            rate: rate,
            rank: displayedRank,
            dxScore: parsedDxScore ?? 0,
            fc: selectedFC,
            fs: selectedFS,
            context: modelContext
        )
        
        try? modelContext.save()
        cachedCurrentScore = savedScore

        Task {
            await SyncManager.shared.syncAfterScoreSave(
                sheet: sheet,
                score: savedScore,
                context: modelContext
            )
        }
        
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        withAnimation(feedbackAnimation) {
            isSaved = true
        }
    }
}
