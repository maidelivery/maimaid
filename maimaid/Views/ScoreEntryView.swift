import SwiftUI
import SwiftData
import PhotosUI

struct ScoreEntryView: View {
    @Environment(\.dismiss) var dismiss
    @Environment(\.modelContext) private var modelContext
    let sheet: Sheet
    
    @StateObject private var visionService = VisionService()
    @State private var selectedItem: PhotosPickerItem? = nil
    @State private var selectedImage: UIImage? = nil
    
    @State private var rateText = ""
    @State private var isSaved = false
    @FocusState private var isRateFocused: Bool
    
    private var parsedRate: Double? {
        Double(rateText)
    }
    
    private var calculatedRank: String {
        guard let rate = parsedRate else { return "-" }
        if rate >= 100.5 { return "SSS+" }
        if rate >= 100.0 { return "SSS" }
        if rate >= 99.5 { return "SS+" }
        if rate >= 99.0 { return "SS" }
        if rate >= 98.0 { return "S+" }
        if rate >= 97.0 { return "S" }
        if rate >= 94.0 { return "AAA" }
        if rate >= 90.0 { return "AA" }
        if rate >= 80.0 { return "A" }
        return "B"
    }
    
    private var diffColor: Color {
        let low = sheet.difficulty.lowercased()
        if low.contains("basic") { return Color(.systemGreen) }
        if low.contains("advanced") { return Color(.systemOrange) }
        if low.contains("expert") { return Color(.systemRed) }
        if low.contains("master") { return Color(.systemPurple) }
        if low.contains("remaster") { return Color(red: 0.85, green: 0.65, blue: 1.0) }
        return .pink
    }
    
    private var isValid: Bool {
        guard let rate = parsedRate else { return false }
        return rate >= 0 && rate <= 101.0
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
                    if let existingScore = sheet.score {
                        existingScoreCard(existingScore)
                    }
                    
                    // Save button
                    saveButton
                }
                .padding(20)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("记录成绩")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("取消") { dismiss() }
                }
            }
        }
        .onAppear {
            if let score = sheet.score {
                rateText = String(format: "%.4f", score.rate)
            }
        }
        .onChange(of: selectedItem) { _, newValue in
            Task {
                if let data = try? await newValue?.loadTransferable(type: Data.self),
                   let image = UIImage(data: data) {
                    selectedImage = image
                    visionService.recognizeScore(from: image)
                }
            }
        }
        .onChange(of: visionService.recognizedRate) { _, newValue in
            if let rate = newValue {
                withAnimation(.spring(response: 0.3)) {
                    rateText = String(format: "%.4f", rate)
                }
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
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
                    Text(sheet.type.uppercased() == "STD" ? "标准" : sheet.type.uppercased())
                        .font(.system(size: 10, weight: .black))
                        .foregroundColor(.white)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(sheet.type.lowercased() == "dx" ? Color.orange : Color.blue, in: RoundedRectangle(cornerRadius: 4))
                    
                    Text(sheet.difficulty.capitalized)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(diffColor)
                }
                
                if let song = sheet.song {
                    Text(song.title)
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
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
                Text("达成率")
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
                HStack(spacing: 8) {
                    Image(systemName: "trophy.fill")
                        .font(.system(size: 12))
                        .foregroundColor(.orange)
                    
                    Text(calculatedRank)
                        .font(.system(size: 18, weight: .black, design: .rounded))
                        .foregroundColor(.primary)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(Color.orange.opacity(0.1), in: Capsule())
                .transition(.scale.combined(with: .opacity))
            }
        }
        .padding(24)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20))
        .animation(.spring(response: 0.3), value: parsedRate != nil)
    }
    
    // MARK: - Photo Scan Section
    
    private var photoScanSection: some View {
        VStack(spacing: 12) {
            HStack {
                Image(systemName: "camera.viewfinder")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.blue)
                Text("从照片识别")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.primary)
                Spacer()
            }
            
            if visionService.isProcessing {
                HStack(spacing: 10) {
                    ProgressView()
                    Text("正在识别...")
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
                        if let rate = visionService.recognizedRate {
                            HStack(spacing: 4) {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundColor(.green)
                                    .font(.system(size: 12))
                                Text("识别成功")
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
                                Text("未能识别成绩")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(.orange)
                            }
                            Text("请手动输入达成率")
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
                        Text("选择结算截图")
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
                Text("当前最佳")
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
                    Text("已保存")
                        .font(.system(size: 16, weight: .bold))
                } else {
                    Image(systemName: "square.and.arrow.down")
                        .font(.system(size: 16, weight: .semibold))
                    Text("保存成绩")
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
        
        let existingScore = sheet.score
        
        if existingScore == nil || rate > existingScore!.rate {
            if let existing = existingScore {
                modelContext.delete(existing)
            }
            
            let newScore = Score(sheetId: "\(sheet.songId)-\(sheet.type)-\(sheet.difficulty)", rate: rate, rank: calculatedRank)
            modelContext.insert(newScore)
            sheet.score = newScore
            
            try? modelContext.save()
        }
        
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        
        withAnimation(.spring(response: 0.3)) {
            isSaved = true
        }
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
            dismiss()
        }
    }
}
