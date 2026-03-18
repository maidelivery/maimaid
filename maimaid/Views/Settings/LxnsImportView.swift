import SwiftUI
import SwiftData

struct LxnsResponse: Decodable {
    let success: Bool
    let data: [LxnsRecord]?
    let message: String?
}

struct LxnsRecord: Decodable {
    let id: Int
    let song_name: String
    let level_index: Int
    let type: String
    let achievements: Double
    let rate: String
    let fc: String?
    let fs: String?
    let dx_score: Int
}

struct LxnsPlayerResponse: Decodable {
    let success: Bool
    let data: LxnsPlayerData?
    let message: String?
}

struct LxnsPlayerData: Decodable {
    let name: String
    let rating: Int
    let trophy: LxnsTrophy?
    let icon: LxnsIcon?
}

struct LxnsTrophy: Decodable {
    let name: String
}

struct LxnsIcon: Decodable {
    let url: String
}

struct LxnsTokenResponse: Decodable {
    let success: Bool?
    let data: LxnsTokenData?
    let message: String?
}

struct LxnsTokenData: Decodable {
    let access_token: String
    let refresh_token: String
}

struct LxnsImportView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var songs: [Song]
    @Query private var configs: [SyncConfig]
    @Query(filter: #Predicate<UserProfile> { $0.isActive == true }) private var activeProfiles: [UserProfile]
    
    private var config: SyncConfig? { configs.first }
    private var activeProfile: UserProfile? { activeProfiles.first }
    
    private let clientId = "cfb7ef40-bc0f-4e3a-8258-9e5f52cd7338"
    private let redirectUri = "urn:ietf:wg:oauth:2.0:oob"
    private let scope = "read_user_profile+read_player+write_player+read_user_token"
    
    @State private var generatedCodeVerifier: String = ""
    @State private var authCode: String = ""
    
    @State private var isImporting = false
    @State private var importStatus: String = ""
    @State private var progress: Double = 0
    @State private var totalRecords: Int = 0
    @State private var currentStep: String = ""
    
    @Environment(\.openURL) var openURL
    
    private var hasBoundAccount: Bool { !(activeProfile?.lxnsRefreshToken.isEmpty ?? true) }
    
    private var statusTint: Color {
        let failedText = String(localized: "import.status.failed")
        let errorText = String(localized: "import.status.error")
        return importStatus.contains(failedText) || importStatus.contains(errorText) ? .red : .cyan
    }
    
    var body: some View {
        ZStack(alignment: .bottom) {
            Color(uiColor: .systemGroupedBackground)
                .ignoresSafeArea()
            
            List {
                summarySection
                contentSection
                
                if isImporting || !importStatus.isEmpty {
                    statusSection
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .scrollDismissesKeyboard(.interactively)
        }
        .navigationTitle("import.lxns.title")
        .navigationBarTitleDisplayMode(.inline)
    }
    
    @ViewBuilder
    private var summarySection: some View {
        Section {
            accountSummaryCard(
                icon: hasBoundAccount ? "snowflake.circle.fill" : "link.badge.plus",
                iconTint: hasBoundAccount ? .cyan : .indigo,
                title: String(localized: hasBoundAccount ? "import.lxns.bound.header" : "import.lxns.step1.header"),
                subtitle: hasBoundAccount
                    ? String(localized: "import.lxns.status.connected")
                    : String(localized: "import.lxns.step1.footer")
            )
        }
        .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
        .listRowBackground(Color.clear)
        .listSectionSeparator(.hidden)
    }
    
    @ViewBuilder
    private var contentSection: some View {
        if let profile = activeProfile, !profile.lxnsRefreshToken.isEmpty {
            Section("import.lxns.bound.header") {
                HStack(spacing: 12) {
                    settingsIcon(icon: "checkmark.shield.fill", color: .green)
                    Text("import.lxns.status")
                    Spacer()
                    Text("import.lxns.status.connected")
                        .foregroundStyle(.green)
                }
                
                actionRow(
                    title: isImporting ? "import.status.syncing" : "import.lxns.action.quickSync",
                    icon: "arrow.triangle.2.circlepath.circle.fill",
                    tint: .cyan
                ) {
                    Task {
                        await startQuickImport(profile: profile)
                    }
                }
                .disabled(isImporting)
                .opacity(isImporting ? 0.6 : 1.0)
            }
            
            Section("import.lxns.manage.header") {
                Button("import.lxns.action.relogin", role: .destructive) {
                    activeProfile?.lxnsRefreshToken = ""
                }
            }
        } else {
            Section {
                actionRow(
                    title: "import.lxns.action.openBrowser",
                    icon: "safari.fill",
                    tint: .indigo
                ) {
                    openAuthPage()
                }
                .disabled(isImporting)
                .opacity(isImporting ? 0.6 : 1.0)
            } header: {
                Text("import.lxns.step1.header")
            } footer: {
                Text("import.lxns.step1.footer")
            }
            
            Section {
                credentialField(
                    title: "import.lxns.code.placeholder",
                    text: $authCode,
                    icon: "key.fill"
                )
                
                Button {
                    Task {
                        await exchangeCodeAndImport()
                    }
                } label: {
                    HStack {
                        Spacer()
                        if isImporting {
                            ProgressView()
                                .tint(.white)
                        } else {
                            Text("import.lxns.action.startImport")
                                .fontWeight(.semibold)
                        }
                        Spacer()
                    }
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(authCode.isEmpty || isImporting)
                .listRowBackground(Color.clear)
            } header: {
                Text("import.lxns.step2.header")
            } footer: {
                Text("import.lxns.step2.footer")
            }
            .listSectionSeparator(.hidden)
        }
    }
    
    @ViewBuilder
    private var statusSection: some View {
        Section("import.status.header") {
            VStack(alignment: .leading, spacing: 14) {
                if isImporting && !currentStep.isEmpty {
                    HStack(spacing: 10) {
                        ProgressView()
                        Text(currentStep)
                            .foregroundStyle(.secondary)
                    }
                }
                
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
                }
            }
            .padding(.vertical, 4)
        }
    }
    
    @MainActor
    private func openAuthPage() {
        if clientId == "YOUR_CLIENT_ID_HERE" {
            importStatus = String(localized: "import.lxns.error.clientId")
            return
        }
        
        let codeVerifier = AuthUtils.generateCodeVerifier()
        let codeChallenge = AuthUtils.generateCodeChallenge(verifier: codeVerifier)
        
        self.generatedCodeVerifier = codeVerifier
        
        var components = URLComponents(string: "https://maimai.lxns.net/oauth/authorize")!
        components.queryItems = [
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "client_id", value: clientId),
            URLQueryItem(name: "redirect_uri", value: redirectUri),
            URLQueryItem(name: "scope", value: scope.replacingOccurrences(of: "+", with: " ")),
            URLQueryItem(name: "code_challenge", value: codeChallenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "state", value: UUID().uuidString)
        ]
        
        if let authURL = components.url {
            openURL(authURL)
        }
    }
    
    @MainActor
    private func exchangeCodeAndImport() async {
        if generatedCodeVerifier.isEmpty {
            importStatus = String(localized: "import.lxns.error.security")
            return
        }
        
        isImporting = true
        importStatus = ""
        currentStep = String(localized: "import.lxns.status.exchanging")
        progress = 0
        totalRecords = 0
        
        do {
            guard let tokenURL = URL(string: "https://maimai.lxns.net/api/v0/oauth/token") else { throw URLError(.badURL) }
            
            var request = URLRequest(url: tokenURL)
            request.httpMethod = "POST"
            
            let parameters = [
                "grant_type": "authorization_code",
                "client_id": clientId,
                "redirect_uri": redirectUri,
                "code": authCode.trimmingCharacters(in: .whitespacesAndNewlines),
                "code_verifier": generatedCodeVerifier
            ]
            
            let bodyString = parameters.compactMap { key, value in
                let encodedValue = value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
                return "\(key)=\(encodedValue)"
            }.joined(separator: "&")
            
            request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
            request.httpBody = bodyString.data(using: .utf8)
            
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
            
            let tokenResponse = try JSONDecoder().decode(LxnsTokenResponse.self, from: data)
            
            if httpResponse.statusCode != 200 || tokenResponse.data?.access_token == nil {
                importStatus = String(localized: "import.lxns.status.failed.token \(tokenResponse.message ?? String(localized: "import.status.error.unknown"))")
                isImporting = false
                return
            }
            
            let accessToken = tokenResponse.data!.access_token
            let refreshToken = tokenResponse.data!.refresh_token
            
            if let profile = activeProfile {
                profile.lxnsRefreshToken = refreshToken
            }
            
            if configs.isEmpty {
                let newConfig = SyncConfig()
                modelContext.insert(newConfig)
            }
            
            await importData(accessToken: accessToken)
        } catch {
            importStatus = String(localized: "import.lxns.status.failed.networkToken")
            isImporting = false
        }
    }
    
    @MainActor
    private func startQuickImport(profile: UserProfile) async {
        isImporting = true
        importStatus = ""
        currentStep = String(localized: "import.lxns.status.refreshing")
        progress = 0
        totalRecords = 0
        
        if let token = await SyncManager.shared.refreshLxnsToken(profile: profile) {
            await importData(accessToken: token)
        } else {
            importStatus = String(localized: "import.lxns.status.failed.expired")
            isImporting = false
        }
    }
    
    @MainActor
    private func importData(accessToken: String) async {
        currentStep = String(localized: "import.lxns.status.player")
        
        do {
            if let playerUrl = URL(string: "https://maimai.lxns.net/api/v0/user/maimai/player") {
                var playerReq = URLRequest(url: playerUrl)
                playerReq.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
                let (pData, _) = try await URLSession.shared.data(for: playerReq)
                let pRes = try JSONDecoder().decode(LxnsPlayerResponse.self, from: pData)
                if pRes.success, let p = pRes.data {
                    if let profile = activeProfile {
                        profile.playerRating = p.rating
                    }
                }
            }
        } catch {
            print("Failed to fetch player info: \(error)")
        }
        
        currentStep = String(localized: "import.lxns.status.fetching")
        
        let difficultyMap = [
            0: "basic",
            1: "advanced",
            2: "expert",
            3: "master",
            4: "remaster"
        ]
        
        let profileId = activeProfile?.id
        
        do {
            guard let url = URL(string: "https://maimai.lxns.net/api/v0/user/maimai/player/scores") else {
                throw URLError(.badURL)
            }
            
            var request = URLRequest(url: url)
            request.httpMethod = "GET"
            request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
            
            let (data, response) = try await URLSession.shared.data(for: request)
            
            guard let httpResponse = response as? HTTPURLResponse else {
                importStatus = String(localized: "import.status.failed.network")
                isImporting = false
                return
            }
            
            let lxnsResponse = try JSONDecoder().decode(LxnsResponse.self, from: data)
            
            if !lxnsResponse.success || httpResponse.statusCode != 200 {
                if let msg = lxnsResponse.message {
                    importStatus = String(localized: "import.status.failed.message \(msg)")
                } else {
                    importStatus = String(localized: "import.status.failed.code \(httpResponse.statusCode)")
                }
                isImporting = false
                return
            }
            
            guard let records = lxnsResponse.data else {
                importStatus = String(localized: "import.status.failed.noRecords")
                isImporting = false
                return
            }
            
            totalRecords = records.count
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
            
            for record in records {
                let recType = record.type == "dx" ? "dx" : "std"
                let recDiff = difficultyMap[record.level_index] ?? ""
                
                if let sheets = titleSheetMap[record.song_name],
                   let targetSheet = sheets.first(where: { $0.type == recType && $0.diff == recDiff })?.sheet {
                    
                    let newRank = RatingUtils.calculateRank(achievement: record.achievements)
                    
                    if let existingScore = ScoreService.shared.score(for: targetSheet, context: modelContext) {
                        let shouldUpdateMetadata = existingScore.fc == nil || existingScore.fs == nil || existingScore.dxScore == 0
                        let fcValue = (record.fc?.isEmpty ?? true) ? nil : record.fc
                        let fsValue = (record.fs?.isEmpty ?? true) ? nil : record.fs
                        
                        if record.achievements > existingScore.rate || shouldUpdateMetadata {
                            existingScore.rate = max(existingScore.rate, record.achievements)
                            existingScore.rank = RatingUtils.calculateRank(achievement: existingScore.rate)
                            existingScore.fc = fcValue
                            existingScore.fs = fsValue
                            existingScore.dxScore = record.dx_score
                            existingScore.achievementDate = Date()
                        }
                    } else {
                        let fcValue = (record.fc?.isEmpty ?? true) ? nil : record.fc
                        let fsValue = (record.fs?.isEmpty ?? true) ? nil : record.fs
                        
                        let score = Score(
                            sheetId: "\(targetSheet.songIdentifier)_\(targetSheet.type)_\(targetSheet.difficulty)",
                            rate: record.achievements,
                            rank: newRank,
                            dxScore: record.dx_score,
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
                profile.lastImportDateLXNS = Date()
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

private extension LxnsImportView {
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
    
    func credentialField(title: String, text: Binding<String>, icon: String) -> some View {
        HStack(spacing: 12) {
            settingsIcon(icon: icon, color: .gray)
            TextField(LocalizedStringKey(title), text: text)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
        }
        .padding(.vertical, 2)
    }
}
