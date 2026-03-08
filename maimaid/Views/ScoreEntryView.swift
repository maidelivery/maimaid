import SwiftUI
import SwiftData
import PhotosUI

struct ScoreEntryView: View {
    @Environment(\.dismiss) var dismiss
    @Environment(\.modelContext) private var modelContext
    @Query private var configs: [SyncConfig]
    let sheet: Sheet
    
    // Optional initial pre-filled values from Scanner
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
    
    init(sheet: Sheet, initialRate: Double? = nil, initialRank: String? = nil, initialDxScore: Int? = nil, initialFC: String? = nil, initialFS: String? = nil) {
        self.sheet = sheet
        self.initialRate = initialRate
        self.initialRank = initialRank
        self.initialDxScore = initialDxScore
        self.initialFC = initialFC
        self.initialFS = initialFS
    }
    
    private var parsedRate: Double? {
        Double(rateText)
    }
    
    private var parsedDxScore: Int? {
        Int(dxScoreText)
    }
    
    private var maxDxScore: Int {
        (sheet.total ?? 0) * 3
    }
    
    private var diffColor: Color {
        ThemeUtils.colorForDifficulty(sheet.difficulty, sheet.type)
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
    
    /// 🔴 Rank 由达成率自动计算
    private var calculatedRank: String {
        if let rate = parsedRate {
            return RatingUtils.calculateRank(achievement: rate)
        }
        // 无有效输入时的默认值
        return "D"
    }
    
    /// 🔴 关键修复：使用 ScoreService 获取当前用户的成绩
    private var currentScore: Score? {
        ScoreService.shared.score(for: sheet, context: modelContext)
    }
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    // Header card — sheet info
                    headerCard
                    
                    // Score input area
                    scoreInputSection
                    
                    // Photo scan section
                    photoScanSection
                    
                    // Existing score info
                    if let existingScore = currentScore {
                        existingScoreCard(existingScore)
                    }
                    
                    // Save button
                    saveButton
                }
                .padding(20)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("score.entry.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("profile.edit.cancel") { dismiss() }
                }
            }
        }
        .onAppear {
            if let initRate = initialRate {
                rateText = String(format: "%.4f", initRate)
            } else if let score = currentScore {
                rateText = String(format: "%.4f", score.rate)
            }
            
            if let initDxScore = initialDxScore {
                dxScoreText = "\(initDxScore)"
            } else if let score = currentScore {
                dxScoreText = score.dxScore > 0 ? "\(score.dxScore)" : ""
            }
            
            if let initFC = initialFC {
                selectedFC = initFC
            } else if let score = currentScore {
                selectedFC = score.fc
            }
            
            if let initFS = initialFS {
                selectedFS = initFS
            } else if let score = currentScore {
                selectedFS = score.fs
            }
        }
        .onChange(of: selectedItem) { _, newItem in
            Task {
                guard let item = newItem else { return }
                if let data = try? await item.loadTransferable(type: Data.self),
                   let image = UIImage(data: data) {
                    selectedImage = image
                    isProcessingPhoto = true
                    
                    let result = await MLScoreProcessor.shared.process(image)
                    if let rate = result.rate {
                        self.recognizedRate = rate
                        withAnimation(.spring(response: 0.3)) {
                            rateText = String(format: "%.4f", rate)
                        }
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    }
                    
                    isProcessingPhoto = false
                }
            }
        }
    }
    
    // MARK: - Header Card
    
    private var headerCard: some View {
        HStack(spacing: 14) {
            // Difficulty accent
            RoundedRectangle(cornerRadius: 3)
                .fill(diffColor)
                .frame(width: 5, height: 50)
            
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(sheet.type.uppercased())
                        .font(.system(size: 10, weight: .black))
                        .foregroundColor(.white)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(sheet.type.lowercased() == "dx" ? Color.orange : Color.blue, in: RoundedRectangle(cornerRadius: 4))
                    
                    if let song = sheet.song {
                        Text(song.title)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.secondary)
                            .lineLimit(1)
                    }
                   
                }
                Text(sheet.difficulty.capitalized)
                    .font(.system(size: 12))
                    .foregroundColor(diffColor)
            }
            
            Spacer()
            
            Text("Lv.\(sheet.level)")
                .font(.system(size: 24, weight: .black, design: .rounded))
                .foregroundColor(diffColor.opacity(0.8))
        }
        .padding(16)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
    }
    
    // MARK: - Score Input Section
    
    private var scoreInputSection: some View {
        VStack(spacing: 16) {
            // Big rate display / input
            VStack(spacing: 8) {
                Text("Achievement")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.secondary)
                
                HStack(alignment: .firstTextBaseline, spacing: 2) {
                    TextField("0.0000", text: $rateText)
                        .font(.system(size: 48, weight: .black, design: .rounded))
                        .foregroundColor(.primary)
                        .multilineTextAlignment(.center)
                        .keyboardType(.decimalPad)
                        .focused($isRateFocused)
                    
                    Text("%")
                        .font(.system(size: 24, weight: .bold, design: .rounded))
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity)
            }
            
            // Auto-calculated rank
            if parsedRate != nil {
                HStack(spacing: 12) {
                    HStack(spacing: 6) {
                        Image(systemName: "trophy.fill")
                            .font(.system(size: 10))
                            .foregroundColor(RatingUtils.colorForRank(calculatedRank))
                        
                        Text(calculatedRank)
                            .font(.system(size: 16, weight: .black, design: .rounded))
                            .foregroundColor(RatingUtils.colorForRank(calculatedRank))
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(RatingUtils.colorForRank(calculatedRank).opacity(0.1), in: Capsule())
                    
                    // DX Score mini input
                    HStack(spacing: 6) {
                        Image(systemName: "star.fill")
                            .font(.system(size: 10))
                            .foregroundColor(dxScoreText.isEmpty ? .gray : .yellow)
                        
                        TextField("DX Score", text: $dxScoreText)
                            .font(.system(size: 14, weight: .bold, design: .rounded))
                            .foregroundColor((parsedDxScore ?? 0) > maxDxScore && maxDxScore > 0 ? .red : .primary)
                            .frame(width: 80)
                            .keyboardType(.numberPad)
                            .focused($isDxScoreFocused)
                        
                        if maxDxScore > 0 {
                            Text("/ \(maxDxScore)")
                                .font(.system(size: 10, weight: .medium, design: .rounded))
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(dxScoreText.isEmpty ? Color.gray.opacity(0.1) : Color.yellow.opacity(0.1), in: Capsule())
                }
                .transition(.scale.combined(with: .opacity))
                
                // FC / FS Selectors
                HStack(spacing: 12) {
                    Menu {
                        Picker("Combo", selection: $selectedFC) {
                            Text("None").tag(String?.none)
                            Text("FC").tag(String?("fc"))
                            Text("FC+").tag(String?("fcp"))
                            Text("AP").tag(String?("ap"))
                            Text("AP+").tag(String?("app"))
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "target")
                                .font(.system(size: 10))
                            ZStack {
                                Text("COMBO")
                                    .font(.system(size: 12, weight: .bold))
                                    .opacity(0)
                                Text(selectedFC?.replacingOccurrences(of: "fcp", with: "fc+").replacingOccurrences(of: "app", with: "ap+").uppercased() ?? "Combo")
                                    .font(.system(size: 12, weight: .bold))
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(selectedFC == nil ? Color.gray.opacity(0.1) : Color.green.opacity(0.1), in: Capsule())
                        .foregroundColor(selectedFC == nil ? .gray : .green)
                    }
                    
                    Menu {
                        Picker("Sync", selection: $selectedFS) {
                            Text("None").tag(String?.none)
                            Text("S").tag(String?("sync"))
                            Text("FS").tag(String?("fs"))
                            Text("FS+").tag(String?("fsp"))
                            Text("FDX").tag(String?("fsd"))
                            Text("FDX+").tag(String?("fsdp"))
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "person.2.fill")
                                .font(.system(size: 10))
                            
                            ZStack {
                                Text("FDX+")
                                    .font(.system(size: 12, weight: .bold))
                                    .opacity(0)
                                Text(selectedFS?.replacingOccurrences(of: "fsdp", with: "fdx+").replacingOccurrences(of: "fsd", with: "fdx").replacingOccurrences(of: "fsp", with: "fs+").uppercased() ?? "SYNC")
                                    .font(.system(size: 12, weight: .bold))
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(selectedFS == nil ? Color.gray.opacity(0.1) : Color.blue.opacity(0.1), in: Capsule())
                        .foregroundColor(selectedFS == nil ? .gray : .blue)
                    }
                }
                .animation(.spring(response: 0.35, dampingFraction: 0.8), value: parsedRate != nil)
            }
        }
        .padding(24)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20))
    }
    
    // MARK: - Photo Scan Section
    
    private var photoScanSection: some View {
        VStack(spacing: 12) {
            HStack {
                Image(systemName: "camera.viewfinder")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.blue)
                Text("score.entry.selectPhoto")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.primary)
                Spacer()
            }
            
            if isProcessingPhoto {
                HStack(spacing: 10) {
                    ProgressView()
                    Text("score.entry.recognizing")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 20)
            } else if let image = selectedImage {
                // Show recognized image thumbnail
                HStack(spacing: 12) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 60, height: 60)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    
                    VStack(alignment: .leading, spacing: 4) {
                        if let rate = recognizedRate {
                            HStack(spacing: 4) {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundColor(.green)
                                    .font(.system(size: 12))
                                Text("score.entry.success")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(.green)
                            }
                            Text("\(String(format: "%.4f", rate))%")
                                .font(.system(size: 14, weight: .bold, design: .monospaced))
                                .foregroundColor(.primary)
                        } else {
                            HStack(spacing: 4) {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .foregroundColor(.orange)
                                    .font(.system(size: 12))
                                Text("score.entry.failed")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(.orange)
                            }
                            Text("score.entry.manualHint")
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                        }
                    }
                    
                    Spacer()
                    
                    // Re-pick button
                    PhotosPicker(selection: $selectedItem, matching: .images) {
                        Image(systemName: "arrow.triangle.2.circlepath")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.blue)
                            .padding(8)
                            .background(Color.blue.opacity(0.1), in: Circle())
                    }
                }
            } else {
                // Initial pick state
                PhotosPicker(selection: $selectedItem, matching: .images) {
                    HStack(spacing: 8) {
                        Image(systemName: "photo")
                            .font(.system(size: 16))
                        Text("score.entry.selectPhoto")
                            .font(.system(size: 14, weight: .medium))
                    }
                    .foregroundColor(.blue)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
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
    
    // MARK: - Existing Score Card
    
    private func existingScoreCard(_ score: Score) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "clock.arrow.circlepath")
                .font(.system(size: 14))
                .foregroundColor(.secondary)
            
            VStack(alignment: .leading, spacing: 2) {
                Text("score.entry.currentBest")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.secondary)
                Text("\(String(format: "%.4f", score.rate))% · \(score.rank)")
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundColor(.primary)
            }
            
            Spacer()
            
            Text(score.achievementDate, style: .date)
                .font(.system(size: 11))
                .foregroundColor(.secondary)
        }
        .padding(14)
        .background(Color.primary.opacity(0.03), in: RoundedRectangle(cornerRadius: 12))
    }
    
    // MARK: - Save Button
    
    private var saveButton: some View {
        Button {
            saveScore()
        } label: {
            HStack(spacing: 8) {
                if isSaved {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 16, weight: .semibold))
                    Text("score.entry.saved")
                        .font(.system(size: 16, weight: .bold))
                } else {
                    Image(systemName: "square.and.arrow.down")
                        .font(.system(size: 16, weight: .semibold))
                    Text("score.entry.save")
                        .font(.system(size: 16, weight: .bold))
                }
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(
                saveButtonBackground,
                in: RoundedRectangle(cornerRadius: 16)
            )
        }
        .disabled(!isValid || isSaved)
        .animation(.spring(response: 0.3), value: isSaved)
    }
    
    // MARK: - Save Logic
    
    private func saveScore() {
        guard let rate = parsedRate, isValid else { return }
        
        // 🔴 关键修复：使用 ScoreService 记录游玩历史（自动关联用户）
        _ = ScoreService.shared.recordPlay(
            sheet: sheet,
            rate: rate,
            rank: calculatedRank,
            dxScore: parsedDxScore ?? 0,
            fc: selectedFC,
            fs: selectedFS,
            context: modelContext
        )
        
        // 🔴 关键修复：使用 ScoreService 保存成绩（自动关联用户）
        let savedScore = ScoreService.shared.saveScore(
            sheet: sheet,
            rate: rate,
            rank: calculatedRank,
            dxScore: parsedDxScore ?? 0,
            fc: selectedFC,
            fs: selectedFS,
            context: modelContext
        )
        
        // Trigger Auto-Sync for manual entry
        if let config = configs.first {
            Task { await SyncManager.shared.uploadScoreIfNeeded(sheet: sheet, score: savedScore, config: config) }
        }
        
        try? modelContext.save()
        
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        
        withAnimation(.spring(response: 0.3)) {
            isSaved = true
        }
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
            dismiss()
        }
    }
}
