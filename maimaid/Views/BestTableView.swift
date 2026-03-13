import SwiftUI
import SwiftData

struct BestTableView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.colorScheme) private var colorScheme
    @Query private var songs: [Song]
    @Query private var configs: [SyncConfig]
    @Query(filter: #Predicate<UserProfile> { $0.isActive == true }) private var activeProfiles: [UserProfile]
    
    private var activeProfile: UserProfile? { activeProfiles.first }
    
    @State private var b50Result: (total: Int, b35: [RatingUtils.RatingEntry], b15: [RatingUtils.RatingEntry]) = (0, [], [])
    @State private var isLoading = true
    @State private var isExporting = false
    
    // MARK: - 临时版本覆盖 (退出页面即失效)
    @State private var overriddenVersion: String?
    @State private var showVersionPicker = false
    
    /// 可选的版本列表
    private var availableVersions: [String] {
        let sequence = UserDefaults.standard.stringArray(forKey: "MaimaiVersionSequence") ?? []
        // 从旧到新排序，用户可以选择任意版本作为"最新版本"
        return sequence
    }
    
    /// 当前实际使用的版本（覆盖或默认）
    private var effectiveVersion: String? {
        overriddenVersion ?? serverVersion
    }
    
    private var serverVersion: String? {
        guard let profile = activeProfile, let server = GameServer(rawValue: profile.server) else { return nil }
        return ServerVersionService.shared.latestVersion(for: server, songs: songs)
    }
    
    private var b35Sum: Int {
        b50Result.b35.reduce(0) { $0 + $1.rating }
    }
    
    private var b15Sum: Int {
        b50Result.b15.reduce(0) { $0 + $1.rating }
    }
    
    private var currentB35Count: Int {
        activeProfile?.b35Count ?? configs.first?.b35Count ?? 35
    }
    
    private var currentB15Count: Int {
        activeProfile?.b15Count ?? configs.first?.b15Count ?? 15
    }
    
    var body: some View {
        List {

            Section {
                HStack {
                    VStack(alignment: .leading) {
                        Text("bestTable.rating")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        Text("\(b50Result.total)")
                            .font(.system(size: 34, weight: .black, design: .rounded))
                            .foregroundStyle(ThemeUtils.ratingGradient(b50Result.total))
                            .opacity(isLoading ? 0.3 : 1.0)
                    }
                    Spacer()
                    VStack(alignment: .trailing) {
                        Text("bestTable.old.count \(b35Sum)")
                        Text("bestTable.new.count \(b15Sum)")
                    }
                    .font(.caption)
                    .foregroundColor(.secondary)
                }
                .padding(.vertical, 8)
            }

            // MARK: - 版本选择器
            Section("bestTable.settings.version") {
                Button {
                    showVersionPicker = true
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("bestTable.settings.version.current")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Text(versionDisplayName)
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(overriddenVersion != nil ? .orange : .primary)
                        }
                        
                        Spacer()
                        
                        if overriddenVersion != nil {
                            Button {
                                overriddenVersion = nil
                                Task { await calculateRating() }
                            } label: {
                                Text("bestTable.settings.version.reset")
                                    .font(.caption)
                                    .foregroundColor(.red)
                            }
                            .buttonStyle(.plain)
                        }
                        
                        Image(systemName: "chevron.right")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                .foregroundColor(.primary)
                
            }

            Section("bestTable.settings.capacity") {
                HStack(spacing: 20) {
                    capacityInput(title: "bestTable.settings.old", value: Binding(
                        get: { currentB35Count },
                        set: { if let p = activeProfile { p.b35Count = max(1, $0) } else { configs.first?.b35Count = max(1, $0) } }
                    ))
                    
                    VStack {
                        Image(systemName: "plus")
                            .font(.caption.bold())
                            .foregroundColor(.secondary)
                    }
                    
                    capacityInput(title: "bestTable.settings.new", value: Binding(
                        get: { currentB15Count },
                        set: { if let p = activeProfile { p.b15Count = max(1, $0) } else { configs.first?.b15Count = max(1, $0) } }
                    ))
                    
                    Divider()
                        .frame(height: 30)
                    
                    VStack(alignment: .center, spacing: 4) {
                        Text("bestTable.settings.total").font(.caption2).foregroundColor(.secondary)
                        Text("\(currentB35Count + currentB15Count)")
                            .font(.system(.body, design: .rounded).bold())
                            .foregroundColor(.orange)
                    }
                    .frame(maxWidth: .infinity)
                }
                .padding(.vertical, 4)
            }
            
            Section(String(localized: "bestTable.section.new \(currentB15Count)")) {
                if isLoading {
                    ProgressView().padding()
                } else if b50Result.b15.isEmpty {
                    Text("bestTable.empty")
                        .foregroundColor(.secondary)
                } else {
                    ForEach(b50Result.b15) { entry in
                        if let song = songs.first(where: { $0.songIdentifier == entry.songIdentifier }) {
                            NavigationLink(destination: SongDetailView(song: song)) {
                                ratingRow(entry: entry)
                            }
                        } else {
                            ratingRow(entry: entry)
                        }
                    }
                }
            }
            
            Section(String(localized: "bestTable.section.old \(currentB35Count)")) {
                if isLoading {
                    ProgressView().padding()
                } else if b50Result.b35.isEmpty {
                    Text("bestTable.empty")
                        .foregroundColor(.secondary)
                } else {
                    ForEach(b50Result.b35) { entry in
                        if let song = songs.first(where: { $0.songIdentifier == entry.songIdentifier }) {
                            NavigationLink(destination: SongDetailView(song: song)) {
                                ratingRow(entry: entry)
                            }
                        } else {
                            ratingRow(entry: entry)
                        }
                    }
                }
            }
        }
        .navigationTitle("bestTable.title")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    exportImage()
                } label: {
                    if isExporting {
                        ProgressView()
                            .controlSize(.small)
                    } else {
                        Label("bestTable.action.export", systemImage: "square.and.arrow.up")
                    }
                }
                .disabled(isLoading || isExporting || (b50Result.b35.isEmpty && b50Result.b15.isEmpty))
            }
        }
        .sheet(isPresented: $showVersionPicker) {
            VersionPickerSheet(
                versions: availableVersions,
                selectedVersion: $overriddenVersion,
                currentServerVersion: serverVersion
            )
        }
        .task(id: songs) {
            await calculateRating()
        }
        .task(id: activeProfile?.b35Count) {
            await calculateRating()
        }
        .task(id: activeProfile?.b15Count) {
            await calculateRating()
        }
        .onChange(of: overriddenVersion) { _, _ in
            Task { await calculateRating() }
        }
    }
    
    // MARK: - Version Display
    
    private var versionDisplayName: String {
        if let version = effectiveVersion {
            let abbr = ThemeUtils.versionAbbreviation(version)
            if overriddenVersion != nil {
                return String(abbr)
            }
            return abbr
        }
        return String(localized: "bestTable.settings.version.unknown")
    }
    
    // MARK: - Export
    
    private func exportImage() {
        isExporting = true
        
        Task {
            let image = await MainActor.run {
                B50ExportView.renderImage(
                    b35: b50Result.b35,
                    b15: b50Result.b15,
                    totalRating: b50Result.total,
                    userName: activeProfile?.name ?? configs.first?.userName,
                    currentVersion: effectiveVersion,
                    colorScheme: colorScheme
                )
            }
            
            isExporting = false
            
            guard let image = image else { return }
            
            if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
               let rootVC = scene.windows.first?.rootViewController {
                
                var topVC = rootVC
                while let presented = topVC.presentedViewController {
                    topVC = presented
                }
                
                let activityVC = UIActivityViewController(activityItems: [image], applicationActivities: nil)
                
                if let popover = activityVC.popoverPresentationController {
                    popover.sourceView = topVC.view
                    popover.sourceRect = CGRect(x: topVC.view.bounds.midX, y: 0, width: 0, height: 0)
                    popover.permittedArrowDirections = []
                }
                
                topVC.present(activityVC, animated: true)
            }
        }
    }
    
    private func capacityInput(title: LocalizedStringKey, value: Binding<Int>) -> some View {
        VStack(alignment: .center, spacing: 6) {
            Text(title).font(.caption2).foregroundColor(.secondary)
            
            TextField("", text: Binding(
                get: { String(value.wrappedValue) },
                set: { newValue in
                    if let intValue = Int(newValue.filter({ $0.isNumber })) {
                        value.wrappedValue = intValue
                    }
                }
            ))
            .keyboardType(.numberPad)
            .multilineTextAlignment(.center)
            .font(.system(.body, design: .monospaced).bold())
            .frame(width: 60)
            .padding(.vertical, 8)
            .background(Color.secondary.opacity(0.1), in: RoundedRectangle(cornerRadius: 8))
            .onSubmit {
                if value.wrappedValue < 1 { value.wrappedValue = 1 }
            }
        }
        .frame(maxWidth: .infinity)
    }
    
    private func calculateRating() async {
        isLoading = true
        
        let profileId = activeProfile?.id
        let server = activeProfile.flatMap { GameServer(rawValue: $0.server) }
        
        // 使用 ScoreService 获取当前用户的成绩映射
        let scoreMap = ScoreService.shared.scoreMap(context: modelContext)
        
        // 使用 RatingUtils 的扩展方法
        let input = songs.toCalculationInput(
            userProfileId: profileId,
            server: server,
            preloadedScores: scoreMap
        )
        
        let b35Limit = activeProfile?.b35Count ?? configs.first?.b35Count ?? 35
        let b15Limit = activeProfile?.b15Count ?? configs.first?.b15Count ?? 15
        
        // 使用覆盖的版本或服务器版本
        let latestVersion = effectiveVersion
        
        let result = await Task.detached(priority: .userInitiated) {
            await RatingUtils.calculateB50(input: input, b35Count: b35Limit, b15Count: b15Limit, latestVersion: latestVersion)
        }.value
        
        self.b50Result = result
        self.isLoading = false
    }
    
    private func ratingRow(entry: RatingUtils.RatingEntry) -> some View {
        HStack(spacing: 14) {
            SongJacketView(
                imageName: entry.imageName ?? "",
                size: 56,
                cornerRadius: 10
            )
            
            VStack(alignment: .leading, spacing: 4) {
                MarqueeText(text: entry.songTitle, font: .system(size: 15, weight: .bold), fontWeight: .bold, color: .primary)
                    .frame(height: 20)
                
                HStack(spacing: 6) {
                    let rank = RatingUtils.calculateRank(achievement: entry.achievement)
                    Text(rank)
                        .font(.system(size: 13, weight: .black, design: .rounded))
                        .foregroundColor(RatingUtils.colorForRank(rank))
                    
                    Text(String(format: "%.4f%%", entry.achievement))
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundColor(.secondary)
                    
                    if entry.dxScore > 0 {
                        HStack(spacing: 2) {
                            Image(systemName: "star.fill")
                                .font(.system(size: 8))
                            Text("\(entry.dxScore)")
                                .font(.system(size: 10, weight: .bold, design: .monospaced))
                        }
                        .fixedSize()
                        .foregroundColor(.yellow)
                    }
                }
                
                HStack(spacing: 4) {
                    BadgeView(text: entry.type.uppercased(), background: entry.type.uppercased() == "DX" ? .orange : .blue)
                    BadgeView(text: ThemeUtils.diffShort(entry.diff), background: ThemeUtils.colorForDifficulty(entry.diff, entry.type))
                    
                    if let fc = entry.fc, !fc.isEmpty {
                        BadgeView(text: fc.uppercased(), background: ThemeUtils.fcColor(fc))
                    }
                    
                    if let fs = entry.fs, !fs.isEmpty {
                        BadgeView(text: fs.uppercased(), background: ThemeUtils.fsColor(fs))
                    }
                }
            }
            .frame(minHeight: 56, alignment: .leading)
            
            Spacer()
            
            VStack(alignment: .trailing, spacing: 2) {
                Text("\(entry.rating)")
                    .font(.system(size: 18, weight: .black, design: .rounded))
                    .foregroundColor(.orange)
                Text("bestTable.base \(entry.level, specifier: "%.1f")")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
            }
            .fixedSize()
        }
        .padding(.vertical, 6)
    }
}

// MARK: - Version Picker Sheet

struct VersionPickerSheet: View {
    @Environment(\.dismiss) var dismiss
    let versions: [String]
    @Binding var selectedVersion: String?
    let currentServerVersion: String?
    
    @State private var tempSelection: String?
    
    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button {
                        tempSelection = nil
                    } label: {
                        HStack {
                            Text("bestTable.settings.version.auto")
                                .foregroundColor(.primary)
                            Spacer()
                            if tempSelection == nil {
                                Image(systemName: "checkmark")
                                    .foregroundColor(.blue)
                            }
                        }
                    }
                } footer: {
                    if let serverVersion = currentServerVersion {
                        Text(ThemeUtils.versionAbbreviation(serverVersion))
                    }
                }
                
                Section("bestTable.settings.version.available") {
                    ForEach(versions.reversed(), id: \.self) { version in
                        Button {
                            tempSelection = version
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(ThemeUtils.versionAbbreviation(version))
                                        .foregroundColor(.primary)
                                    Text(version)
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                                if tempSelection == version {
                                    Image(systemName: "checkmark")
                                        .foregroundColor(.blue)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("bestTable.settings.version.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("bestTable.settings.version.cancel") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("bestTable.settings.version.confirm") {
                        selectedVersion = tempSelection
                        dismiss()
                    }
                    .bold()
                }
            }
            .onAppear {
                tempSelection = selectedVersion
            }
        }
        .presentationDetents([.medium, .large])
    }
}
