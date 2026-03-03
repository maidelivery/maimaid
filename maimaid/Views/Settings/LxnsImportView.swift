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
    
    private var config: SyncConfig? { configs.first }
    
    // Replace this with your actual LXNS Developer Client ID
    private let clientId = "cfb7ef40-bc0f-4e3a-8258-9e5f52cd7338"
    private let redirectUri = "urn:ietf:wg:oauth:2.0:oob"
    private let scope = "read_user_profile+read_player+write_player+read_user_token"
    
    // Auth State
    @State private var generatedCodeVerifier: String = ""
    @State private var authCode: String = ""
    
    @State private var isImporting = false
    @State private var importStatus: String = ""
    @State private var progress: Double = 0
    @State private var totalRecords: Int = 0
    @State private var currentStep: String = ""
    
    @Environment(\.openURL) var openURL
    
    var body: some View {
        Form {
            if let currentConfig = config, !currentConfig.lxnsRefreshToken.isEmpty {
                Section(header: Text("import.lxns.bound.header")) {
                    HStack {
                        Text("import.lxns.status")
                        Spacer()
                        Text("import.lxns.status.connected")
                            .foregroundColor(.green)
                    }
                    
                    Button {
                        Task {
                            await startQuickImport(config: currentConfig)
                        }
                    } label: {
                        HStack {
                            if isImporting {
                                ProgressView()
                                    .padding(.trailing, 8)
                            }
                            Text(isImporting ? "import.status.syncing" : "import.lxns.action.quickSync")
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .disabled(isImporting)
                }
                
                Section(header: Text("import.lxns.manage.header")) {
                    Button("import.lxns.action.relogin", role: .destructive) {
                        currentConfig.lxnsRefreshToken = ""
                    }
                }
            } else {
                Section(header: Text("import.lxns.step1.header"), footer: Text("import.lxns.step1.footer")) {
                    Button {
                        openAuthPage()
                    } label: {
                        HStack {
                            Image(systemName: "safari")
                            Text("import.lxns.action.openBrowser")
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
                
                Section(header: Text("import.lxns.step2.header"), footer: Text("import.lxns.step2.footer")) {
                    TextField("import.lxns.code.placeholder", text: $authCode)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                    
                    Button {
                        Task {
                            await exchangeCodeAndImport()
                        }
                    } label: {
                        HStack {
                            if isImporting {
                                ProgressView()
                                    .padding(.trailing, 8)
                            }
                            Text(isImporting ? "import.status.importing" : "import.lxns.action.startImport")
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .disabled(authCode.isEmpty || isImporting)
                }
            }
            
            if isImporting || !importStatus.isEmpty {
                Section(header: Text("import.status.header")) {
                    if isImporting {
                        HStack {
                            ProgressView()
                                .padding(.trailing, 8)
                            Text(currentStep)
                                .foregroundColor(.secondary)
                        }
                    }
                    
                    if !importStatus.isEmpty {
                        Text(importStatus)
                            .foregroundColor(importStatus.contains(String(localized: "import.status.failed")) || importStatus.contains(String(localized: "import.status.error")) ? .red : .primary)
                    }
                    
                    if totalRecords > 0 {
                        ProgressView(value: progress, total: Double(totalRecords))
                    }
                }
            }
        }
        .navigationTitle("import.lxns.title")
        .navigationBarTitleDisplayMode(.inline)
    }
    
    @MainActor
    private func openAuthPage() {
        if clientId == "YOUR_CLIENT_ID_HERE" {
            importStatus = String(localized: "import.lxns.error.clientId")
            return
        }
        
        let codeVerifier = AuthUtils.generateCodeVerifier()
        let codeChallenge = AuthUtils.generateCodeChallenge(verifier: codeVerifier)
        
        // Save the verifier to state so we can use it when the user pastes the code back
        self.generatedCodeVerifier = codeVerifier
        
        // For Out-Of-Band, the redirect_uri must be urn:ietf:wg:oauth:2.0:oob
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
            
            // Standard OAuth application/x-www-form-urlencoded
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
            
            // Persist the refresh token
            if let currentConfig = configs.first {
                currentConfig.lxnsRefreshToken = refreshToken
            } else {
                let newConfig = SyncConfig(lxnsRefreshToken: refreshToken)
                modelContext.insert(newConfig)
            }
            
            await importData(accessToken: accessToken)
        } catch {
            importStatus = String(localized: "import.lxns.status.failed.networkToken")
            isImporting = false
        }
    }
    
    @MainActor
    private func startQuickImport(config: SyncConfig) async {
        isImporting = true
        importStatus = ""
        currentStep = String(localized: "import.lxns.status.refreshing")
        progress = 0
        totalRecords = 0
        
        if let token = await SyncManager.shared.refreshLxnsToken(config: config) {
            await importData(accessToken: token)
        } else {
            importStatus = String(localized: "import.lxns.status.failed.expired")
            isImporting = false
        }
    }
    
    @MainActor
    private func importData(accessToken: String) async {
        currentStep = String(localized: "import.lxns.status.player")
        
        // 1. Fetch Player Info
        do {
            if let playerUrl = URL(string: "https://maimai.lxns.net/api/v0/user/maimai/player") {
                var playerReq = URLRequest(url: playerUrl)
                playerReq.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
                let (pData, _) = try await URLSession.shared.data(for: playerReq)
                let pRes = try JSONDecoder().decode(LxnsPlayerResponse.self, from: pData)
                if pRes.success, let p = pRes.data {
                    if let currentConfig = configs.first {
                        // Only update rating from LXNS as a data reference
                        currentConfig.playerRating = p.rating
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
            
            // Optimization map using song titles (Local mapping by title)
            var titleSheetMap: [String: [(type: String, diff: String, sheet: Sheet)]] = [:]
            for song in songs {
                var sheetInfos: [(String, String, Sheet)] = []
                for sheet in song.sheets {
                    sheetInfos.append((sheet.type.lowercased(), sheet.difficulty.lowercased(), sheet))
                }
                // Using localizedCaseInsensitiveContains or exact map
                titleSheetMap[song.title] = sheetInfos
            }
            
            var importedCount = 0
            var importedScores: [(Sheet, Score)] = []
            
            for record in records {
                let recType = record.type == "dx" ? "dx" : "std"
                let recDiff = difficultyMap[record.level_index] ?? ""
                
                // LXNS usually provides song_name exactly matching internal DB titles.
                if let sheets = titleSheetMap[record.song_name],
                   let targetSheet = sheets.first(where: { $0.type == recType && $0.diff == recDiff })?.sheet {
                    
                    let newRank = RatingUtils.calculateRank(achievement: record.achievements)
                    
                    if let existingScore = targetSheet.score {
                        // Update if achievement improves OR if metadata is currently missing
                        let shouldUpdateMetadata = existingScore.fc == nil || existingScore.fs == nil || existingScore.dxScore == 0
                        if record.achievements > existingScore.rate || shouldUpdateMetadata {
                            existingScore.rate = max(existingScore.rate, record.achievements)
                            existingScore.rank = RatingUtils.calculateRank(achievement: existingScore.rate)
                            existingScore.fc = record.fc
                            existingScore.fs = record.fs
                            existingScore.dxScore = record.dx_score
                            existingScore.achievementDate = Date()
                        }
                    } else {
                        let score = Score(
                            sheetId: "\(targetSheet.songId)_\(targetSheet.type)_\(targetSheet.difficulty)",
                            rate: record.achievements,
                            rank: newRank,
                            dxScore: record.dx_score,
                            fc: record.fc,
                            fs: record.fs,
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
            
            // Trigger Auto-Upload for imported scores
            if let currentConfig = configs.first, currentConfig.isAutoUploadEnabled && !importedScores.isEmpty {
                Task {
                    await SyncManager.shared.uploadScoresIfNeeded(scores: importedScores, config: currentConfig)
                }
            }
            
            importStatus = String(localized: "import.status.success \(importedCount)")
        } catch {
            importStatus = String(localized: "import.status.error.message \(error.localizedDescription)")
        }
        
        isImporting = false
    }
    
}
