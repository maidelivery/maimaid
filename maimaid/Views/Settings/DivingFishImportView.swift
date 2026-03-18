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
    private var hasBoundAccount: Bool { !(activeProfile?.dfUsername.isEmpty ?? true) }
    
    private var statusTint: Color {
        let failedText = String(localized: "import.status.failed")
        let errorText = String(localized: "import.status.error")
        return importStatus.contains(failedText) || importStatus.contains(errorText) ? .red : .blue
    }
    
    var body: some View {
        ZStack(alignment: .bottom) {
            Color(uiColor: .systemGroupedBackground)
                .ignoresSafeArea()
            
            List {
                summarySection
                formSection
                
                if !importStatus.isEmpty || isImporting {
                    statusSection
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .scrollDismissesKeyboard(.interactively)
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
    
    @ViewBuilder
    private var summarySection: some View {
        Section {
            accountSummaryCard(
                icon: hasBoundAccount ? "fish.circle.fill" : "person.crop.circle.badge.plus",
                iconTint: hasBoundAccount ? .blue : .orange,
                title: String(localized: hasBoundAccount ? "import.df.bound.header" : "import.df.setup.header"),
                subtitle: hasBoundAccount
                    ? (activeProfile?.dfUsername ?? String(localized: "common.unknown"))
                    : String(localized: "import.df.setup.footer")
            )
        }
        .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
        .listRowBackground(Color.clear)
        .listSectionSeparator(.hidden)
    }
    
    @ViewBuilder
    private var formSection: some View {
        if hasBoundAccount {
            Section("import.df.bound.header") {
                HStack(spacing: 12) {
                    settingsIcon(icon: "person.text.rectangle.fill", color: .blue)
                    Text("import.df.username")
                    Spacer()
                    Text(activeProfile?.dfUsername ?? String(localized: "common.unknown"))
                        .foregroundStyle(.secondary)
                }
                
                actionRow(
                    title: isImporting ? "import.status.syncing" : "import.df.action.quickSync",
                    icon: "arrow.triangle.2.circlepath.circle.fill",
                    tint: .blue
                ) {
                    Task {
                        if let name = activeProfile?.dfUsername {
                            await importData(userName: name)
                        }
                    }
                }
                .disabled(isImporting)
                .opacity(isImporting ? 0.6 : 1.0)
            }
            
            Section {
                credentialField(
                    title: "import.df.username.placeholder",
                    text: $username,
                    icon: "person.fill"
                )
                
                credentialField(
                    title: "import.df.token.placeholder",
                    text: $importToken,
                    icon: "key.fill",
                    isSecure: true
                )
                
                Button("import.df.action.update") {
                    updateConfig()
                }
                .disabled(username.isEmpty)
            } header: {
                Text("import.df.rebind.header")
            } footer: {
                Text("import.df.rebind.footer")
            }
        } else {
            Section {
                credentialField(
                    title: "import.df.username.placeholder",
                    text: $username,
                    icon: "person.fill"
                )
                
                credentialField(
                    title: "import.df.token.setup.placeholder",
                    text: $importToken,
                    icon: "key.fill",
                    isSecure: true
                )
            } footer: {
                Text("import.df.setup.footer")
            }
            
            Section {
                Button {
                    updateConfig()
                    Task {
                        await importData(userName: username)
                    }
                } label: {
                    HStack {
                        Spacer()
                        if isImporting {
                            ProgressView()
                                .tint(.white)
                        } else {
                            Text("import.df.action.bindImport")
                                .fontWeight(.semibold)
                        }
                        Spacer()
                    }
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(username.isEmpty || isImporting)
                .listRowBackground(Color.clear)
            }
            .listSectionSeparator(.hidden)
        }
    }
    
    @ViewBuilder
    private var statusSection: some View {
        Section("import.status.header") {
            VStack(alignment: .leading, spacing: 14) {
                if !importStatus.isEmpty {
                    Label {
                        Text(importStatus)
                            .foregroundStyle(statusTint)
                            .fixedSize(horizontal: false, vertical: true)
                    } icon: {
                        Image(systemName: statusTint == .red ? "exclamationmark.circle.fill" : "checkmark.circle.fill")
                            .foregroundStyle(statusTint)
                    }
                }
                
                if totalRecords > 0 {
                    VStack(alignment: .leading, spacing: 8) {
                        ProgressView(value: progress, total: Double(totalRecords))
                            .tint(statusTint)
                        Text("\(Int(progress)) / \(totalRecords)")
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(.secondary)
                    }
                } else if isImporting {
                    HStack(spacing: 10) {
                        ProgressView()
                        Text("import.status.importing")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .padding(.vertical, 4)
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
            newConfig.dfUsername = username
            if !importToken.isEmpty {
                newConfig.dfImportToken = importToken
            }
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
            importStatus = String(localized: "import.status.processing \(totalRecords)")
            
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
                        
                        let score = Score(
                            sheetId: "\(targetSheet.songIdentifier)_\(targetSheet.type)_\(targetSheet.difficulty)",
                            rate: newRate,
                            rank: newRank,
                            dxScore: record.dx_score ?? 0,
                            fc: fcValue,
                            fs: fsValue,
                            achievementDate: Date(),
                            userProfileId: profileId
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
            ScoreService.shared.notifyScoresChanged(for: profileId)
            
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

private extension DivingFishImportView {
    func settingsIcon(icon: String, color: Color) -> some View {
        Image(systemName: icon)
            .font(.system(size: 15, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 32, height: 32)
            .background(color.gradient, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
    
    func accountSummaryCard(icon: String, iconTint: Color, title: String, subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 14) {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(iconTint.gradient)
                    .frame(width: 72, height: 72)
                    .overlay {
                        Image(systemName: icon)
                            .font(.system(size: 30, weight: .semibold))
                            .foregroundStyle(.white)
                    }
                
                VStack(alignment: .leading, spacing: 6) {
                    Text(title)
                        .font(.system(size: 18, weight: .bold, design: .rounded))
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                
                Spacer(minLength: 0)
            }
            
            Divider()
            
            Label("settings.sync.footer", systemImage: "lock.shield.fill")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .padding(20)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 28, style: .continuous))
    }
    
    func actionRow(title: String, icon: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                settingsIcon(icon: icon, color: tint)
                Text(LocalizedStringKey(title))
                    .foregroundStyle(.primary)
                Spacer()
                Image(systemName: "arrow.up.forward.app")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(tint)
            }
        }
        .buttonStyle(.plain)
    }
    
    func credentialField(title: String, text: Binding<String>, icon: String, isSecure: Bool = false) -> some View {
        HStack(spacing: 12) {
            settingsIcon(icon: icon, color: .gray)
            Group {
                if isSecure {
                    SecureField(LocalizedStringKey(title), text: text)
                        .textInputAutocapitalization(.never)
                } else {
                    TextField(LocalizedStringKey(title), text: text)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
            }
        }
        .padding(.vertical, 2)
    }
}
