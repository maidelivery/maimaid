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
}

struct DivingFishImportView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var songs: [Song]
    @Query private var configs: [SyncConfig]
    
    @State private var username: String = ""
    @State private var importToken: String = ""
    @State private var isImporting = false
    @State private var importStatus: String = ""
    @State private var progress: Double = 0
    @State private var totalRecords: Int = 0
    
    private var config: SyncConfig? { configs.first }
    
    var body: some View {
        Form {
            if let currentConfig = config, !currentConfig.dfUsername.isEmpty {
                Section(header: Text("已绑定账号")) {
                    HStack {
                        Text("当前账号")
                        Spacer()
                        Text(currentConfig.dfUsername)
                            .foregroundColor(.secondary)
                    }
                    
                    Button {
                        Task {
                            await importData(userName: currentConfig.dfUsername)
                        }
                    } label: {
                        HStack {
                            if isImporting {
                                ProgressView()
                                    .padding(.trailing, 8)
                            }
                            Text(isImporting ? "正在同步..." : "一键快速同步成绩")
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .disabled(isImporting)
                }
                
                Section(header: Text("重新绑定"), footer: Text("如果你更换了账号或 Token，请在此处更新。成绩上送功能需要填写正确。")) {
                    TextField("QQ号 / 用户名", text: $username)
                    SecureField("导入令牌 (可选，用于同步上送)", text: $importToken)
                    
                    Button("更新并绑定") {
                        updateConfig()
                    }
                    .disabled(username.isEmpty)
                }
            } else {
                Section(header: Text("账号设置"), footer: Text("请输入你的 Diving Fish (查分器) QQ号或用户名。")) {
                    TextField("QQ号 / 用户名", text: $username)
                    SecureField("导入令牌 (在查分器设置页获取，用于上送功能)", text: $importToken)
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
                            Text(isImporting ? "正在导入..." : "绑定并开始导入")
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .disabled(username.isEmpty || isImporting)
                }
            }
            
            if !importStatus.isEmpty {
                Section(header: Text("状态")) {
                    Text(importStatus)
                        .foregroundColor(importStatus.contains("失败") || importStatus.contains("错误") ? .red : .primary)
                    
                    if totalRecords > 0 {
                        ProgressView(value: progress, total: Double(totalRecords))
                    }
                }
            }
        }
        .navigationTitle("从 Diving Fish 导入")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if let currentConfig = config {
                username = currentConfig.dfUsername
                importToken = currentConfig.dfImportToken
            }
        }
    }
    
    private func updateConfig() {
        if let currentConfig = configs.first {
            currentConfig.dfUsername = username
            if !importToken.isEmpty {
                currentConfig.dfImportToken = importToken
            }
        } else {
            let newConfig = SyncConfig(dfUsername: username, dfImportToken: importToken)
            modelContext.insert(newConfig)
        }
    }
    
    @MainActor
    private func importData(userName: String? = nil) async {
        let targetUser = userName ?? username
        guard !targetUser.isEmpty else { return }
        
        isImporting = true
        importStatus = "正在连接到 Diving Fish..."
        progress = 0
        totalRecords = 0
        
        // Match sheet types and difficulties
        let difficultyMap = [
            0: "basic",
            1: "advanced",
            2: "expert",
            3: "master",
            4: "remaster"
        ]
        
        do {
            guard let url = URL(string: "https://www.diving-fish.com/api/maimaidxprober/query/player") else {
                throw URLError(.badURL)
            }
            
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            
            // Diving Fish API now requires b50 flag to get charts
            let isQQ = Int(targetUser) != nil && targetUser.count > 5
            var bodyDict: [String: Any] = isQQ ? ["qq": targetUser] : ["username": targetUser]
            bodyDict["b50"] = true
            request.httpBody = try JSONSerialization.data(withJSONObject: bodyDict)
            
            let (data, response) = try await URLSession.shared.data(for: request)
            
            guard let httpResponse = response as? HTTPURLResponse else {
                importStatus = "导入失败：无效的网络响应"
                isImporting = false
                return
            }
            
            if httpResponse.statusCode != 200 {
                if let dfResponse = try? JSONDecoder().decode(DivingFishResponse.self, from: data), let msg = dfResponse.message {
                    importStatus = "导入失败：\(msg)"
                } else {
                    importStatus = "导入失败：HTTP 错误 \(httpResponse.statusCode)"
                }
                isImporting = false
                return
            }
            
            let dfResponse = try JSONDecoder().decode(DivingFishResponse.self, from: data)
            
            guard let charts = dfResponse.charts else {
                importStatus = "导入失败：未找到成绩记录"
                isImporting = false
                return
            }
            
            // Merge dx and sd lists
            var allRecords: [DivingFishRecord] = []
            if let dx = charts.dx { allRecords.append(contentsOf: dx) }
            if let sd = charts.sd { allRecords.append(contentsOf: sd) }
            
            guard !allRecords.isEmpty else {
                importStatus = "导入失败：没有任何成绩"
                isImporting = false
                return
            }
            
            totalRecords = allRecords.count
            importStatus = "查找到 \(totalRecords) 条成绩，正在处理..."
            
            // Optimization map
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
                
                // Match exact title, type and difficulty
                if let sheets = titleSheetMap[record.title],
                   let targetSheet = sheets.first(where: { $0.type == recType && $0.diff == recDiff })?.sheet {
                    
                    // Insert or update score
                    let newRate = record.achievements
                    let newRank = getRank(from: newRate)
                    
                    if let existingScore = targetSheet.score {
                        if newRate > existingScore.rate {
                            existingScore.rate = newRate
                            existingScore.rank = newRank
                            existingScore.achievementDate = Date()
                        }
                    } else {
                        let score = Score(
                            sheetId: "\(targetSheet.songId)_\(targetSheet.type)_\(targetSheet.difficulty)",
                            rate: newRate,
                            rank: newRank,
                            achievementDate: Date()
                        )
                        modelContext.insert(score)
                        targetSheet.score = score
                        importedScores.append((targetSheet, score))
                    }
                    importedCount += 1
                }
                progress += 1
                
                if Int(progress) % 20 == 0 { // Yield occasionally
                    await Task.yield()
                }
            }
            
            try modelContext.save()
            
            // Update last sync date
            if let currentConfig = configs.first {
                currentConfig.lastImportDateDF = Date()
                
                // Trigger Auto-Upload for imported scores
                if currentConfig.isAutoUploadEnabled && !importedScores.isEmpty {
                    Task {
                        await SyncManager.shared.uploadScoresIfNeeded(scores: importedScores, config: currentConfig)
                    }
                }
            }
            
            importStatus = "导入成功！共写入/更新了 \(importedCount) 条成绩。"
        } catch {
            importStatus = "导入错误：\(error.localizedDescription)"
        }
        
        isImporting = false
    }
    
    private func getRank(from rate: Double) -> String {
        if rate >= 100.5 { return "SSS+" }
        if rate >= 100.0 { return "SSS" }
        if rate >= 99.5 { return "SS+" }
        if rate >= 99.0 { return "SS" }
        if rate >= 98.0 { return "S+" }
        if rate >= 97.0 { return "S" }
        if rate >= 94.0 { return "AAA" }
        if rate >= 90.0 { return "AA" }
        if rate >= 80.0 { return "A" }
        return "B" // Simplification
    }
}
