import SwiftUI
import SwiftData
import CoreData

struct BestTableView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.colorScheme) private var colorScheme
    @Query private var songs: [Song]
    @Query private var configs: [SyncConfig]
    @Query(filter: #Predicate<UserProfile> { $0.isActive == true }) private var activeProfiles: [UserProfile]
    
    @State private var cache = B50CacheService.shared
    
    private var activeProfile: UserProfile? { activeProfiles.first }
    
    @State private var isExporting = false
    
    // MARK: - 临时版本覆盖 (退出页面即失效)
    @State private var overriddenVersion: String?
    @State private var showVersionPicker = false
    @AppStorage(AppStorageKeys.useFitDiff) private var useFitDiff = false
    
    // MARK: - Performance / Lifecycle
    @State private var calculationTask: Task<Void, Never>?
    @State private var hasAppeared = false
    @State private var isVisible = false
    
    /// 可选的版本列表
    private var availableVersions: [String] {
        let sequence = UserDefaults.app.maimaiVersionSequence
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
        cache.b50Result.b35.reduce(0) { $0 + $1.rating }
    }
    
    private var b15Sum: Int {
        cache.b50Result.b15.reduce(0) { $0 + $1.rating }
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
                            .foregroundStyle(.secondary)
                        Text("\(cache.b50Result.total)")
                            .font(.system(size: 34, weight: .black, design: .rounded))
                            .foregroundStyle(ThemeUtils.ratingGradient(cache.b50Result.total))
                            .opacity(cache.isLoading ? 0.3 : 1.0)
                    }
                    Spacer()
                    VStack(alignment: .trailing) {
                        Text("bestTable.old.count \(b35Sum)")
                        Text("bestTable.new.count \(b15Sum)")
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
                .padding(.vertical, 8)
            }

            Section("bestTable.settings.version") {
                Button {
                    showVersionPicker = true
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("bestTable.settings.version.current")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text(versionDisplayName)
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(overriddenVersion != nil ? .orange : .primary)
                        }
                        
                        Spacer()
                        
                        if overriddenVersion != nil {
                            Button {
                                overriddenVersion = nil
                            } label: {
                                Text("bestTable.settings.version.reset")
                                    .font(.caption)
                                    .foregroundStyle(.red)
                            }
                            .buttonStyle(.plain)
                        }
                        
                        Image(systemName: "chevron.right")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .foregroundStyle(.primary)
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
                            .foregroundStyle(.secondary)
                    }
                    
                    capacityInput(title: "bestTable.settings.new", value: Binding(
                        get: { currentB15Count },
                        set: { if let p = activeProfile { p.b15Count = max(1, $0) } else { configs.first?.b15Count = max(1, $0) } }
                    ))
                    
                    Divider()
                        .frame(height: 30)
                    
                    VStack(alignment: .center, spacing: 4) {
                        Text("bestTable.settings.total").font(.caption2).foregroundStyle(.secondary)
                        Text("\(currentB35Count + currentB15Count)")
                            .font(.system(.body, design: .rounded).bold())
                            .foregroundStyle(.orange)
                    }
                    .frame(maxWidth: .infinity)
                }
                .padding(.vertical, 4)
                
                Toggle("bestTable.settings.useFitDiff", isOn: $useFitDiff)
                    .tint(.orange)
            }
            
            Section(String(localized: "bestTable.section.new \(currentB15Count)")) {
                if cache.isLoading && cache.isFirstLoad {
                    ForEach(0..<min(5, currentB15Count), id: \.self) { _ in
                        RatingRowSkeletonView()
                            .listRowSeparator(.hidden)
                    }
                } else if cache.b50Result.b15.isEmpty {
                    Text("bestTable.empty")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(cache.b50Result.b15) { entry in
                        if let song = cache.getSong(identifier: entry.songIdentifier) {
                            NavigationLink(destination: SongDetailView(song: song, preferredType: entry.type)) {
                                ratingRow(entry: entry)
                            }
                        } else {
                            ratingRow(entry: entry)
                        }
                    }
                }
            }
            
            Section(String(localized: "bestTable.section.old \(currentB35Count)")) {
                if cache.isLoading && cache.isFirstLoad {
                    ForEach(0..<min(8, currentB35Count), id: \.self) { _ in
                        RatingRowSkeletonView()
                            .listRowSeparator(.hidden)
                    }
                } else if cache.b50Result.b35.isEmpty {
                    Text("bestTable.empty")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(cache.b50Result.b35) { entry in
                        if let song = cache.getSong(identifier: entry.songIdentifier) {
                            NavigationLink(destination: SongDetailView(song: song, preferredType: entry.type)) {
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
                .disabled(cache.isLoading || isExporting || (cache.b50Result.b35.isEmpty && cache.b50Result.b15.isEmpty))
            }
        }
        .sheet(isPresented: $showVersionPicker) {
            VersionPickerSheet(
                versions: availableVersions,
                selectedVersion: $overriddenVersion,
                currentServerVersion: serverVersion
            )
        }
        .onAppear {
            isVisible = true
            cache.updateSongs(songs)
            
            guard !hasAppeared else { return }
            hasAppeared = true
            
            scheduleCalculation(delayNanoseconds: 180_000_000)
        }
        .onDisappear {
            isVisible = false
            calculationTask?.cancel()
            calculationTask = nil
        }
        .onChange(of: songs.count) { _, _ in
            cache.updateSongs(songs)
            scheduleCalculation()
        }
        .onChange(of: activeProfile?.id) { _, _ in
            scheduleCalculation()
        }
        .onChange(of: currentB35Count) { _, _ in
            scheduleCalculation()
        }
        .onChange(of: currentB15Count) { _, _ in
            scheduleCalculation()
        }
        .onChange(of: overriddenVersion) { _, _ in
            scheduleCalculation()
        }
        .onChange(of: useFitDiff) { _, _ in
            scheduleCalculation()
        }
        .onReceive(NotificationCenter.default.publisher(for: .NSManagedObjectContextDidSave)) { _ in
            guard isVisible else { return }
            scheduleCalculation(delayNanoseconds: 180_000_000)
        }
    }
    
    // MARK: - Scheduling
    
    private func scheduleCalculation(delayNanoseconds: UInt64 = 120_000_000) {
        calculationTask?.cancel()
        calculationTask = Task {
            try? await Task.sleep(for: .nanoseconds(delayNanoseconds))
            guard !Task.isCancelled, isVisible else { return }
            await performCalculation()
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
                    b35: cache.b50Result.b35,
                    b15: cache.b50Result.b15,
                    totalRating: cache.b50Result.total,
                    userName: activeProfile?.name ?? configs.first?.userName,
                    currentVersion: effectiveVersion,
                    useFitDiff: useFitDiff,
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
            Text(title).font(.caption2).foregroundStyle(.secondary)
            
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
    
    private func performCalculation() async {
        if useFitDiff {
            await ChartStatsService.shared.fetchStats()
        }
        
        guard isVisible else { return }
        
        _ = await cache.calculateIfNeeded(
            modelContext: modelContext,
            activeProfile: activeProfile,
            configs: configs,
            overriddenVersion: overriddenVersion,
            useFitDiff: useFitDiff
        )
    }
    
    private func ratingRow(entry: RatingUtils.RatingEntry) -> some View {
        HStack(spacing: 14) {
            SongJacketView(
                imageName: entry.imageName ?? "",
                size: 56,
                cornerRadius: 10,
                useThumbnail: true
            )
            
            VStack(alignment: .leading, spacing: 4) {
                MarqueeText(text: entry.songTitle, font: .system(size: 15, weight: .bold), fontWeight: .bold, color: .primary)
                    .frame(height: 20)
                
                HStack(spacing: 6) {
                    let rank = RatingUtils.calculateRank(achievement: entry.achievement)
                    Text(rank)
                        .font(.system(size: 13, weight: .black, design: .rounded))
                        .foregroundStyle(RatingUtils.colorForRank(rank))
                    
                    Text("\(entry.achievement, format: .number.precision(.fractionLength(4)))%")
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundStyle(.secondary)
                    
                    if entry.dxScore > 0 {
                        HStack(spacing: 2) {
                            Image(systemName: "star.fill")
                                .font(.system(size: 8))
                            Text("\(entry.dxScore)")
                                .font(.system(size: 10, weight: .bold, design: .monospaced))
                        }
                        .fixedSize()
                        .foregroundStyle(.yellow)
                    }
                }
                
                HStack(spacing: 4) {
                    BadgeView(text: entry.type.uppercased(), background: entry.type.uppercased() == "DX" ? .orange : .blue)
                    BadgeView(text: ThemeUtils.diffShort(entry.diff), background: ThemeUtils.colorForDifficulty(entry.diff, entry.type, colorScheme))
                    
                    if let fc = entry.fc, !fc.isEmpty {
                        BadgeView(text: ThemeUtils.normalizeFC(fc), background: ThemeUtils.fcColor(fc))
                    }
                    
                    if let fs = entry.fs, !fs.isEmpty {
                        BadgeView(text: ThemeUtils.normalizeFS(fs), background: ThemeUtils.fsColor(fs))
                    }
                }
            }
            .frame(minHeight: 56, alignment: .leading)
            
            Spacer()
            
            VStack(alignment: .trailing, spacing: 2) {
                Text("\(entry.rating)")
                    .font(.system(size: 18, weight: .black, design: .rounded))
                    .foregroundStyle(.orange)
                Text("bestTable.base \(entry.level, specifier: "%.1f")")
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
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
                                .foregroundStyle(.primary)
                            Spacer()
                            if tempSelection == nil {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(.primary)
                            }
                        }
                    }
                    .buttonStyle(.plain)
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
                                        .foregroundStyle(.primary)
                                    Text(version)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                if tempSelection == version {
                                    Image(systemName: "checkmark")
                                        .foregroundStyle(.primary)
                                }
                            }
                        }
                        .buttonStyle(.plain)
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
                    .foregroundStyle(.primary)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("bestTable.settings.version.confirm") {
                        selectedVersion = tempSelection
                        dismiss()
                    }
                    .foregroundStyle(.primary)
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

// MARK: - Skeleton View

struct RatingRowSkeletonView: View {
    var body: some View {
        HStack(spacing: 14) {
            RoundedRectangle(cornerRadius: 10)
                .fill(Color.gray.opacity(0.15))
                .frame(width: 56, height: 56)
                .skeleton()
            
            VStack(alignment: .leading, spacing: 8) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.gray.opacity(0.15))
                    .frame(height: 16)
                    .frame(maxWidth: 160)
                    .skeleton()
                
                HStack(spacing: 6) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color.gray.opacity(0.15))
                        .frame(width: 30, height: 14)
                        .skeleton()
                    
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color.gray.opacity(0.15))
                        .frame(width: 50, height: 14)
                        .skeleton()
                }
                
                HStack(spacing: 4) {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(Color.gray.opacity(0.15))
                        .frame(width: 24, height: 12)
                        .skeleton()
                    
                    RoundedRectangle(cornerRadius: 2)
                        .fill(Color.gray.opacity(0.15))
                        .frame(width: 36, height: 12)
                        .skeleton()
                }
            }
            .frame(minHeight: 56, alignment: .leading)
            
            Spacer()
            
            VStack(alignment: .trailing, spacing: 6) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.gray.opacity(0.15))
                    .frame(width: 40, height: 20)
                    .skeleton()
                
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.gray.opacity(0.15))
                    .frame(width: 48, height: 12)
                    .skeleton()
            }
        }
        .padding(.vertical, 6)
    }
}
