import SwiftUI
import SwiftData
import PhotosUI

struct HomeView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var configs: [SyncConfig]
    @Query private var songs: [Song]
    @Query(sort: \MaimaiIcon.id) private var icons: [MaimaiIcon]
    
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
                                Text("home.bestTable.button.title \(totalCount)")
                                    .font(.system(size: 16, weight: .bold))
                                Text("home.bestTable.button.subtitle \(config?.b35Count ?? 35) \(config?.b15Count ?? 15)")
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
                        NavigationLink(destination: RandomSongView()) {
                            functionCard(
                                icon: "dice.fill",
                                title: "home.randomSong.title",
                                subtitle: "home.randomSong.subtitle",
                                gradient: [Color.purple, Color.pink]
                            )
                        }
                        .buttonStyle(.plain)
                        
                        NavigationLink(destination: RecommendationListView()) {
                            functionCard(
                                icon: "sparkles",
                                title: "home.recommendation.title",
                                subtitle: "home.recommendation.subtitle",
                                gradient: [Color.orange, Color.red]
                            )
                        }
                        NavigationLink(destination: PlateProgressView()) {
                            functionCard(
                                icon: "chart.bar.xaxis",
                                title: "home.plateProgress.title",
                                subtitle: "home.plateProgress.subtitle",
                                gradient: [Color.green, Color.blue]
                            )
                        }
                        .buttonStyle(.plain)
                        
                        NavigationLink(destination: UsefulLinksView()) {
                            functionCard(
                                icon: "link",
                                title: "home.usefulLinks.title",
                                subtitle: "home.usefulLinks.subtitle",
                                gradient: [Color.blue, Color.cyan]
                            )
                        }
                        .buttonStyle(.plain)
                    }

                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("home.title")
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
        let input = songs.toCalculationInput()
        let result = await Task.detached(priority: .userInitiated) {
            await RatingUtils.calculateB50(input: input, b35Count: 35, b15Count: 15)
        }.value
        self.standardB50Total = result.total
    }
    
    private func updateB50() async {
        let input = songs.toCalculationInput()
        
        let b35Limit = config?.b35Count ?? 35
        let b15Limit = config?.b15Count ?? 15
        
        // Background calculation with sendable input
        let result = await Task.detached(priority: .userInitiated) {
            await RatingUtils.calculateB50(input: input, b35Count: b35Limit, b15Count: b15Limit)
        }.value
        
        self.computedB50Total = result.total
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
                        Text(config?.userName ?? String(localized: "home.profile.unbound"))
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.primary)
                    }
                    
                    if let plate = config?.plate {
                        Text(plate)
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    } else {
                        Text("home.profile.editHint")
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
            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 20))
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(Color.primary.opacity(0.05), lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.02), radius: 8, x: 0, y: 4)
        }
        .buttonStyle(.plain)
    }
    
    private func functionCard(icon: String, title: LocalizedStringKey, subtitle: LocalizedStringKey, gradient: [Color]) -> some View {
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
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.primary.opacity(0.05), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.02), radius: 8, x: 0, y: 4)
    }
}

@MainActor
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
                                    } else if let urlString = config.avatarUrl {
                                        // Try to load local version if it's a preset URL
                                        let localImage: UIImage? = {
                                            if let idString = urlString.components(separatedBy: "/").last?.replacingOccurrences(of: ".png", with: ""),
                                               let id = Int(idString) {
                                                return ImageDownloader.shared.loadImage(iconId: id)
                                            }
                                            return nil
                                        }()
                                        
                                        if let uiImage = localImage {
                                            Image(uiImage: uiImage)
                                                .resizable()
                                                .aspectRatio(contentMode: .fill)
                                                .frame(width: 100, height: 100)
                                                .clipShape(Circle())
                                        } else if let url = URL(string: urlString) {
                                            AsyncImage(url: url) { image in
                                                image.resizable()
                                                    .aspectRatio(contentMode: .fill)
                                            } placeholder: {
                                                ProgressView()
                                            }
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
                                        Text("profile.edit.changeAvatar")
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
                                Button("profile.edit.clearAvatar", role: .destructive) {
                                    selectedImageData = nil
                                    config.avatarData = nil
                                    config.avatarUrl = nil
                                }
                                .font(.subheadline)
                            }
                        }
                        Spacer()
                    }
                    .listRowBackground(Color.clear)
                    .padding(.vertical, 10)
                }
                
                Section("profile.edit.basicInfo") {
                    LabeledContent("profile.edit.name") {
                        TextField("profile.edit.name.placeholder", text: $userName)
                            .multilineTextAlignment(.trailing)
                    }
                    LabeledContent("profile.edit.titleName") {
                        TextField("profile.edit.titleName.placeholder", text: $plate)
                            .multilineTextAlignment(.trailing)
                    }
                }
                
                Section("profile.edit.presetIcon") {
                    NavigationLink {
                        IconPickerView(config: config, selectedImageData: $selectedImageData)
                    } label: {
                        HStack {
                            Text("profile.edit.presetIcon.select")
                            Spacer()
                            if let avatarUrl = config.avatarUrl, avatarUrl.contains("lxns.net") {
                                if let idString = avatarUrl.components(separatedBy: "/").last?.replacingOccurrences(of: ".png", with: ""),
                                   let id = Int(idString),
                                   let localImage = ImageDownloader.shared.loadImage(iconId: id) {
                                    Image(uiImage: localImage)
                                        .resizable()
                                        .aspectRatio(contentMode: .fill)
                                        .frame(width: 30, height: 30)
                                        .clipShape(Circle())
                                } else {
                                    AsyncImage(url: URL(string: avatarUrl)) { image in
                                        image.resizable()
                                            .aspectRatio(contentMode: .fill)
                                    } placeholder: {
                                        Color.gray.opacity(0.1)
                                    }
                                    .frame(width: 30, height: 30)
                                    .clipShape(Circle())
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("profile.edit.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("profile.edit.cancel") { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("profile.edit.save") {
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
            config.avatarUrl = nil // Clear preset if custom image is set
            config.isCustomProfile = true
        } else if config.avatarUrl != nil {
            // Keep the preset URL set by IconPickerView
            config.avatarData = nil
            config.isCustomProfile = true
        } else {
            // Truly cleared
            config.avatarData = nil
            config.avatarUrl = nil
            config.isCustomProfile = false
        }
    }
}

@MainActor
struct IconPickerView: View {
    @Bindable var config: SyncConfig
    @Binding var selectedImageData: Data?
    @Query(sort: \MaimaiIcon.id) private var icons: [MaimaiIcon]
    @Environment(\.dismiss) var dismiss
    
    @State private var searchText = ""
    
    var filteredIcons: [MaimaiIcon] {
        if searchText.isEmpty {
            return icons
        }
        return icons.filter { $0.name.localizedCaseInsensitiveContains(searchText) || $0.genre.localizedCaseInsensitiveContains(searchText) }
    }
    
    let columns = [
        GridItem(.adaptive(minimum: 80, maximum: 100), spacing: 16)
    ]
    
    var body: some View {
        ScrollView {
            LazyVGrid(columns: columns, spacing: 20) {
                ForEach(filteredIcons) { icon in
                    Button { [config] in
                        config.avatarUrl = icon.iconUrl
                        config.avatarData = nil // Clear custom data in config
                        selectedImageData = nil // Clear custom data in parent sheet state
                        config.isCustomProfile = true
                        dismiss()
                    } label: {
                        VStack(spacing: 8) {
                            if let localImage = ImageDownloader.shared.loadImage(iconId: icon.id) {
                                Image(uiImage: localImage)
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                                    .frame(width: 70, height: 70)
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.primary.opacity(0.1), lineWidth: 1))
                            } else {
                                AsyncImage(url: URL(string: icon.iconUrl)) { image in
                                    image.resizable()
                                        .aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    ProgressView()
                                }
                                .frame(width: 70, height: 70)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.primary.opacity(0.1), lineWidth: 1))
                            }
                            
                            Text(icon.name)
                                .font(.system(size: 10))
                                .lineLimit(1)
                                .foregroundColor(.primary)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(16)
        }
        .navigationTitle("profile.picker.title")
        .searchable(text: $searchText, prompt: "profile.picker.search")
    }
}
