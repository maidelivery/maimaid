import SwiftUI
import SwiftData
import PhotosUI

struct HomeView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var configs: [SyncConfig]
    @Query private var songs: [Song]
    
    @State private var showingEditProfile = false
    @State private var computedB50Total: Int = 0
    @State private var standardB50Total: Int = 0
    
    private var config: SyncConfig? { configs.first }
    
    let columns = [
        GridItem(.flexible(), spacing: 16),
        GridItem(.flexible(), spacing: 16)
    ]
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // Profile Section
                    profileHeader
                    
                    // Main "Best Table" Button
                    NavigationLink(destination: BestTableView()) {
                        HStack {
                            Image(systemName: "trophy.fill")
                                .font(.system(size: 20))
                                .foregroundColor(.orange)
                            
                            VStack(alignment: .leading, spacing: 2) {
                                let totalCount = (config?.b35Count ?? 35) + (config?.b15Count ?? 15)
                                Text("查看 Best \(totalCount) 成绩表")
                                    .font(.system(size: 16, weight: .bold))
                                Text("基于 B\(config?.b35Count ?? 35) + N\(config?.b15Count ?? 15) 计算的 DX Rating")
                                    .font(.system(size: 11))
                                    .foregroundColor(.secondary)
                            }
                            
                            Spacer()
                            
                            Image(systemName: "chevron.right")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.secondary.opacity(0.5))
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 14)
                        .background(Color.orange.opacity(0.1), in: RoundedRectangle(cornerRadius: 16))
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(Color.orange.opacity(0.2), lineWidth: 1)
                        )
                    }
                    .buttonStyle(.plain)
                    
                    LazyVGrid(columns: columns, spacing: 16) {
                        functionCard(
                            icon: "chart.bar.fill",
                            title: "成绩分析",
                            subtitle: "查看你的成绩趋势",
                            gradient: [Color.blue, Color.cyan]
                        )
                        
                        NavigationLink(destination: RandomSongView()) {
                            functionCard(
                                icon: "dice.fill",
                                title: "随机抽选",
                                subtitle: "老虎机式随机挑曲",
                                gradient: [Color.purple, Color.pink]
                            )
                        }
                        .buttonStyle(.plain)
                        
                        functionCard(
                            icon: "person.2.fill",
                            title: "排行榜",
                            subtitle: "查看好友排名",
                            gradient: [Color.green, Color.mint]
                        )
                        
                        functionCard(
                            icon: "target",
                            title: "目标管理",
                            subtitle: "设定分数目标",
                            gradient: [Color.red, Color.orange]
                        )
                        
                        functionCard(
                            icon: "square.and.arrow.up.fill",
                            title: "成绩导出",
                            subtitle: "导出为图片或文件",
                            gradient: [Color.indigo, Color.blue]
                        )
                        
                        functionCard(
                            icon: "star.fill",
                            title: "Rating计算器",
                            subtitle: "手动模拟计算",
                            gradient: [Color.orange, Color.yellow]
                        )
                    }
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("主页")
            .sheet(isPresented: $showingEditProfile) {
                if let config = config {
                    ProfileEditSheet(config: config)
                }
            }
            .task(id: config?.b15Count) {
                await updateB50()
            }
            .task(id: config?.b35Count) {
                await updateB50()
            }
            // Standard B50 for profile badge
            .task(id: songs) {
                await updateStandardB50()
            }
        }
    }
    
    private func updateStandardB50() async {
        let input = prepareCalculationInput()
        let result = await Task.detached(priority: .userInitiated) {
            await RatingUtils.calculateB50(input: input, b35Count: 35, b15Count: 15)
        }.value
        self.standardB50Total = result.total
    }
    
    private func updateB50() async {
        let input = prepareCalculationInput()
        
        let b35Limit = config?.b35Count ?? 35
        let b15Limit = config?.b15Count ?? 15
        
        // Background calculation with sendable input
        let result = await Task.detached(priority: .userInitiated) {
            await RatingUtils.calculateB50(input: input, b35Count: b35Limit, b15Count: b15Limit)
        }.value
        
        self.computedB50Total = result.total
    }
    
    private func prepareCalculationInput() -> [RatingUtils.RatingCalculationInput] {
        songs.map { song in
            RatingUtils.RatingCalculationInput(
                songId: song.songId,
                title: song.title,
                version: song.version,
                releaseDate: song.releaseDate,
                imageUrl: song.imageUrl,
                imageName: song.imageName,
                sheets: song.sheets.compactMap { sheet in
                    guard let score = sheet.score else { return nil }
                    return RatingUtils.SheetCalculationInput(
                        difficulty: sheet.difficulty,
                        type: sheet.type,
                        internalLevel: sheet.internalLevelValue,
                        level: sheet.levelValue,
                        rate: score.rate,
                        fc: score.fc,
                        fs: score.fs,
                        dxScore: score.dxScore
                    )
                }
            )
        }
    }
    
    private var profileHeader: some View {
        Button {
            showingEditProfile = true
        } label: {
            HStack(spacing: 16) {
                // Avatar
                ZStack {
                    if let data = config?.avatarData, let uiImage = UIImage(data: data) {
                        Image(uiImage: uiImage)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 60, height: 60)
                            .clipShape(Circle())
                    } else if let urlString = config?.avatarUrl, let url = URL(string: urlString) {
                        AsyncImage(url: url) { image in
                            image.resizable()
                                .aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Color.gray.opacity(0.2)
                        }
                        .frame(width: 60, height: 60)
                        .clipShape(Circle())
                    } else {
                        Image(systemName: "person.circle.fill")
                            .resizable()
                            .frame(width: 60, height: 60)
                            .foregroundColor(.blue.opacity(0.6))
                    }
                    
                    // Rating badge
                    VStack {
                        Spacer()
                        HStack {
                            Spacer()
                            Text("\(max(standardB50Total, config?.playerRating ?? 0))")
                                .font(.system(size: 10, weight: .black, design: .rounded))
                                .foregroundColor(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.orange, in: Capsule())
                                .overlay(Capsule().stroke(Color.white, lineWidth: 1))
                                .offset(x: 4, y: 4)
                        }
                    }
                }
                .frame(width: 60, height: 60)
                
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 4) {
                        Text(config?.userName ?? "未绑定用户")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.primary)
                    }
                    
                    if let plate = config?.plate {
                        Text(plate)
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    } else {
                        Text("点击编辑或绑定 LXNS")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    }
                }
                
                Spacer()
                
                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.secondary.opacity(0.3))
            }
            .padding(16)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20))
        }
        .buttonStyle(.plain)
    }
    
    private func functionCard(icon: String, title: String, subtitle: String, gradient: [Color]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 28))
                .foregroundStyle(
                    LinearGradient(colors: gradient, startPoint: .topLeading, endPoint: .bottomTrailing)
                )
            
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(.primary)
                
                Text(subtitle)
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                    .lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
    }
}

struct ProfileEditSheet: View {
    @Environment(\.dismiss) var dismiss
    @Bindable var config: SyncConfig
    
    @State private var userName: String = ""
    @State private var plate: String = ""
    @State private var selectedItem: PhotosPickerItem?
    @State private var selectedImageData: Data?
    
    var body: some View {
        NavigationStack {
            Form {
                // Centered Avatar Section
                Section {
                    HStack {
                        Spacer()
                        VStack(spacing: 12) {
                            PhotosPicker(selection: $selectedItem, matching: .images) {
                                ZStack {
                                    if let data = selectedImageData ?? config.avatarData, let uiImage = UIImage(data: data) {
                                        Image(uiImage: uiImage)
                                            .resizable()
                                            .aspectRatio(contentMode: .fill)
                                            .frame(width: 100, height: 100)
                                            .clipShape(Circle())
                                    } else {
                                        Circle()
                                            .fill(Color.secondary.opacity(0.1))
                                            .frame(width: 100, height: 100)
                                        
                                        Image(systemName: "person.fill")
                                            .font(.system(size: 40))
                                            .foregroundColor(.secondary)
                                    }
                                    
                                    // Overlay hint
                                    VStack {
                                        Spacer()
                                        Text("更换头像")
                                            .font(.system(size: 10, weight: .medium))
                                            .foregroundColor(.white)
                                            .padding(.vertical, 4)
                                            .frame(maxWidth: .infinity)
                                            .background(Color.black.opacity(0.4))
                                    }
                                    .clipShape(Circle())
                                }
                                .frame(width: 100, height: 100)
                                .overlay(Circle().stroke(Color.primary.opacity(0.1), lineWidth: 1))
                                .shadow(color: .black.opacity(0.05), radius: 5, y: 2)
                            }
                            
                            if selectedImageData != nil || config.avatarData != nil {
                                Button("清除头像", role: .destructive) {
                                    selectedImageData = nil
                                    config.avatarData = nil
                                }
                                .font(.subheadline)
                            }
                        }
                        Spacer()
                    }
                    .listRowBackground(Color.clear)
                    .padding(.vertical, 10)
                }
                
                Section("基本信息") {
                    LabeledContent("名字") {
                        TextField("输入名称", text: $userName)
                            .multilineTextAlignment(.trailing)
                    }
                    LabeledContent("称号 (Plate)") {
                        TextField("输入称号", text: $plate)
                            .multilineTextAlignment(.trailing)
                    }
                }
                
                Section {
                    // Empty for now, or could keep Clear Avatar here
                } footer: {
                    Text("所有资料将保存在本地，不会与服务器同步。")
                }
            }
            .navigationTitle("个人资料")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("保存") {
                        saveChanges()
                        dismiss()
                    }
                    .bold()
                    .disabled(userName.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .onAppear {
                userName = config.userName ?? ""
                plate = config.plate ?? ""
                selectedImageData = config.avatarData
            }
            .onChange(of: selectedItem) { _, newItem in
                Task {
                    if let data = try? await newItem?.loadTransferable(type: Data.self) {
                        selectedImageData = data
                    }
                }
            }
        }
    }
    
    private func saveChanges() {
        config.userName = userName.trimmingCharacters(in: .whitespaces)
        config.plate = plate.trimmingCharacters(in: .whitespaces)
        if let data = selectedImageData {
            config.avatarData = data
            config.isCustomProfile = true
        } else {
            // If they explicitly cleared it or it was nil
            config.avatarData = nil
            config.avatarUrl = nil
        }
    }
}

