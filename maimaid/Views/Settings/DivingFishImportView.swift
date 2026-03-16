import SwiftUI
import SwiftData

struct DivingFishResponse: Decodable {
    let charts: DivingFishCharts?
    let message: String?
}

struct DivingFishCharts: Decodable {
    let dx: [DivingFishRecord]?
    let sd: [DivingFishRecord]?
}

struct DivingFishRecord: Decodable {
    let achievements: Double
    let title: String
    let type: String
    let level_index: Int
    let fc: String?
    let fs: String?
    let dx_score: Int?
    
    enum CodingKeys: String, CodingKey {
        case achievements, title, type, fc, fs
        case level_index = "level_index"
        case dx_score = "dx_score"
    }
}

struct DivingFishImportView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var songs: [Song]
    @Query private var configs: [SyncConfig]
    @Query(filter: #Predicate<UserProfile> { $0.isActive == true }) private var activeProfiles: [UserProfile]
    
    @State private var username: String = ""
    @State private var importToken: String = ""
    @State private var isImporting = false
    @State private var importStatus: String = ""
    @State private var progress: Double = 0
    @State private var totalRecords: Int = 0
    
    private var config: SyncConfig? { configs.first }
    private var activeProfile: UserProfile? { activeProfiles.first }
    
    var body: some View {
        Form {
            if let profile = activeProfile, !profile.dfUsername.isEmpty {
                Section(header: Text("import.df.bound.header")) {
                    HStack {
                        Text("import.df.username")
                        Spacer()
                        Text(profile.dfUsername)
                            .foregroundColor(.secondary)
                    }
                    
                    Button {
                        Task {
                            await importData(userName: profile.dfUsername)
                        }
                    } label: {
                        HStack {
                            if isImporting {
                                ProgressView()
                                    .padding(.trailing, 8)
                            }
                            Text(isImporting ? "import.status.syncing" : "import.df.action.quickSync")
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .disabled(isImporting)
                }
                
                Section(header: Text("import.df.rebind.header"), footer: Text("import.df.rebind.footer")) {
                    TextField("import.df.username.placeholder", text: $username)
                    SecureField("import.df.token.placeholder", text: $importToken)
                    
                    Button("import.df.action.update") {
                        updateConfig()
                    }
                    .disabled(username.isEmpty)
                }
            } else {
                Section(header: Text("import.df.setup.header"), footer: Text("import.df.setup.footer")) {
                    TextField("import.df.username.placeholder", text: $username)
                    SecureField("import.df.token.setup.placeholder", text: $importToken)
                }
                
                Section {
                    Button {
                        updateConfig()
                        Task {
                            await importData(userName: username)
                        }
                    } label: {
                        HStack {
                            if isImporting {
                                ProgressView()
                                    .padding(.trailing, 8)
                            }
                            Text(isImporting ? "import.status.importing" : "import.df.action.bindImport")
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .disabled(username.isEmpty || isImporting)
                }
            }
            
            if !importStatus.isEmpty {
                Section(header: Text("import.status.header")) {
                    Text(importStatus)
                        .foregroundColor(importStatus.contains(String(localized: "import.status.failed")) || importStatus.contains(String(localized: "import.status.error")) ? .red : .primary)
                    
                    if totalRecords > 0 {
                        ProgressView(value: progress, total: Double(totalRecords))
                    }
                }
            }
        }
        .navigationTitle("import.df.title")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if let profile = activeProfile {
                username = profile.dfUsername
                importToken = profile.dfImportToken
            }
        }
    }
    
    private func updateConfig() {
        if let profile = activeProfile {
            profile.dfUsername = username
            if !importToken.isEmpty {
                profile.dfImportToken = importToken
            }
        }
        
        if configs.isEmpty {
            let newConfig = SyncConfig()
            modelContext.insert(newConfig)
        }
    }
    
    @MainActor
    private func importData(userName: String? = nil) async {
        let targetUser = userName ?? username
        guard !targetUser.isEmpty else { return }
        
        isImporting = true
        importStatus = String(localized: "import.df.status.connecting")
        progress = 0
        totalRecords = 0
        
        let difficultyMap = [
            0: "basic",
            1: "advanced",
            2: "expert",
            3: "master",
            4: "remaster"
        ]
        
        // 🔴 获取当前用户 ID，用于成绩关联
        let profileId = activeProfile?.id
        
        do {
            guard let url = URL(string: "https://www.diving-fish.com/api/maimaidxprober/query/player") else {
                throw URLError(.badURL)
            }
            
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            
            let isQQ = Int(targetUser) != nil && targetUser.count > 5
            var bodyDict: [String: Any] = isQQ ? ["qq": targetUser] : ["username": targetUser]
            bodyDict["b50"] = true
            request.httpBody = try JSONSerialization.data(withJSONObject: bodyDict)
            
            let (data, response) = try await URLSession.shared.data(for: request)
            
            guard let httpResponse = response as? HTTPURLResponse else {
                importStatus = String(localized: "import.status.failed.network")
                isImporting = false
                return
            }
            
            if httpResponse.statusCode != 200 {
                if let dfResponse = try? JSONDecoder().decode(DivingFishResponse.self, from: data), let msg = dfResponse.message {
                    importStatus = String(localized: "import.status.failed.message \(msg)")
                } else {
                    importStatus = String(localized: "import.status.failed.code \(httpResponse.statusCode)")
                }
                isImporting = false
                return
            }
            
            let dfResponse = try JSONDecoder().decode(DivingFishResponse.self, from: data)
            
            guard let charts = dfResponse.charts else {
                importStatus = String(localized: "import.status.failed.noRecords")
                isImporting = false
                return
            }
            
            var allRecords: [DivingFishRecord] = []
            if let dx = charts.dx { allRecords.append(contentsOf: dx) }
            if let sd = charts.sd { allRecords.append(contentsOf: sd) }
            
            guard !allRecords.isEmpty else {
                importStatus = String(localized: "import.status.failed.empty")
                isImporting = false
                return
            }
            
            totalRecords = allRecords.count
            importStatus = "查找到 \(totalRecords) 条成绩，正在处理..."
            
            var titleSheetMap: [String: [(type: String, diff: String, sheet: Sheet)]] = [:]
            for song in songs {
                var sheetInfos: [(String, String, Sheet)] = []
                for sheet in song.sheets {
                    sheetInfos.append((sheet.type.lowercased(), sheet.difficulty.lowercased(), sheet))
                }
                titleSheetMap[song.title] = sheetInfos
            }
            
            var importedCount = 0
            var importedScores: [(Sheet, Score)] = []
            
            for record in allRecords {
                let recType = record.type.lowercased()
                let recDiff = difficultyMap[record.level_index] ?? ""
                
                if let sheets = titleSheetMap[record.title],
                   let targetSheet = sheets.first(where: { $0.type == recType && $0.diff == recDiff })?.sheet {
                    
                    let newRate = record.achievements
                    let newRank = RatingUtils.calculateRank(achievement: record.achievements)
                    
                    // 🔴 修复：使用 ScoreService 获取当前用户的成绩
                    if let existingScore = ScoreService.shared.score(for: targetSheet, context: modelContext) {
                        let shouldUpdateMetadata = existingScore.fc == nil || existingScore.fs == nil || existingScore.dxScore == 0
                        let fcValue = (record.fc?.isEmpty ?? true) ? nil : record.fc
                        let fsValue = (record.fs?.isEmpty ?? true) ? nil : record.fs
                        
                        if newRate > existingScore.rate || shouldUpdateMetadata {
                            existingScore.rate = max(existingScore.rate, newRate)
                            existingScore.rank = RatingUtils.calculateRank(achievement: existingScore.rate)
                            existingScore.fc = fcValue
                            existingScore.fs = fsValue
                            existingScore.dxScore = record.dx_score ?? 0
                            existingScore.achievementDate = Date()
                        }
                    } else {
                        let fcValue = (record.fc?.isEmpty ?? true) ? nil : record.fc
                        let fsValue = (record.fs?.isEmpty ?? true) ? nil : record.fs
                        
                        // 🔴 修复：使用 ScoreService 保存成绩（自动关联用户）
                        let score = Score(
                            sheetId: "\(targetSheet.songIdentifier)_\(targetSheet.type)_\(targetSheet.difficulty)",
                            rate: newRate,
                            rank: newRank,
                            dxScore: record.dx_score ?? 0,
                            fc: fcValue,
                            fs: fsValue,
                            achievementDate: Date(),
                            userProfileId: profileId  // 关键：关联当前用户
                        )
                        modelContext.insert(score)
                        targetSheet.scores.append(score)
                        importedScores.append((targetSheet, score))
                    }
                    importedCount += 1
                }
                progress += 1
                
                if Int(progress) % 20 == 0 {
                    await Task.yield()
                }
            }
            
            try modelContext.save()
            
            if let profile = activeProfile {
                profile.lastImportDateDF = Date()
                
                if let currentConfig = config, currentConfig.isAutoUploadEnabled && !importedScores.isEmpty {
                    Task {
                        await SyncManager.shared.uploadScoresIfNeeded(scores: importedScores, config: currentConfig)
                    }
                }
            }
            
            importStatus = String(localized: "import.status.success \(importedCount)")
        } catch {
            importStatus = String(localized: "import.status.error.message \(error.localizedDescription)")
        }
        
        isImporting = false
    }
}
