import SwiftUI
import SwiftData
import PhotosUI

struct ScoreEntryView: View {
    @Environment(\.dismiss) var dismiss
    @Environment(\.modelContext) private var modelContext
    let sheet: Sheet
    
    @StateObject private var visionService = VisionService()
    @State private var selectedItem: PhotosPickerItem? = nil
    
    @State private var rateText = ""
    @State private var selectedRank = "SSS+"
    
    let ranks = ["SSS+", "SSS", "SS+", "SS", "S+", "S", "AAA", "AA", "A"]
    
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack {
                        Text("Achievement Rate (%)")
                            .foregroundColor(.white)
                        Spacer()
                        if visionService.isProcessing {
                            ProgressView()
                                .tint(.white)
                        } else {
                            PhotosPicker(selection: $selectedItem, matching: .images) {
                                Label("Scan Photo", systemImage: "camera.viewfinder")
                                    .font(.caption)
                                    .padding(6)
                                    .background(Color.white.opacity(0.1))
                                    .cornerRadius(8)
                                    .foregroundColor(.white)
                            }
                        }
                    }
                    TextField("100.0000", text: $rateText)
                        .keyboardType(.decimalPad)
                        .padding(.vertical, 8)
                }
                .onChange(of: selectedItem) { oldValue, newValue in
                    Task {
                        if let data = try? await newValue?.loadTransferable(type: Data.self),
                           let image = UIImage(data: data) {
                            visionService.recognizeScore(from: image)
                        }
                    }
                }
                .onChange(of: visionService.recognizedRate) { oldValue, newValue in
                    if let rate = newValue {
                        rateText = String(format: "%.4f", rate)
                    }
                }
                
                Section {
                    Picker("Rank", selection: $selectedRank) {
                        ForEach(ranks, id: \.self) { rank in
                            Text(rank).tag(rank)
                        }
                    }
                    .pickerStyle(.wheel)
                }
                
                Section {
                    Button {
                        saveScore()
                    } label: {
                        Text("Save Best Score")
                            .fontWeight(.bold)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.blue)
                            .foregroundColor(.white)
                            .cornerRadius(12)
                    }
                    .padding(.top)
                }
            }
            .navigationTitle("Enter Score")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { dismiss() }
                        .foregroundColor(.white)
                }
            }
        }
        .onAppear {
            if let score = sheet.score {
                rateText = String(format: "%.4f", score.rate)
                selectedRank = score.rank
            }
        }
    }
    
    private func saveScore() {
        guard let rate = Double(rateText) else { return }
        
        let existingScore = sheet.score
        
        if existingScore == nil || rate > existingScore!.rate {
            if let existing = existingScore {
                modelContext.delete(existing)
            }
            
            let newScore = Score(sheetId: "\(sheet.songId)-\(sheet.type)-\(sheet.difficulty)", rate: rate, rank: selectedRank)
            modelContext.insert(newScore)
            sheet.score = newScore
            
            try? modelContext.save()
        }
        
        dismiss()
    }
}
