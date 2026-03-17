import SwiftUI

struct DanListView: View {
    @State private var categories: [DanCategory] = []
    @State private var isLoading = true
    
    private var groupedCategories: [(version: String, items: [DanCategory])] {
        let groups = Dictionary(grouping: categories) { category in
            versionName(for: category)
        }
        
        return groups
            .map { (version: $0.key, items: sortCategories($0.value)) }
            .sorted { lhs, rhs in
                versionSortKey(lhs.version) > versionSortKey(rhs.version)
            }
    }
    
    var body: some View {
        List {
            if isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .listRowBackground(Color.clear)
            } else if categories.isEmpty {
                ContentUnavailableView(
                    "songs.unavailable.title",
                    systemImage: "doc.text.magnifyingglass",
                    description: Text("songs.unavailable.description")
                )
            } else {
                ForEach(groupedCategories, id: \.version) { group in
                    Section {
                        ForEach(group.items) { category in
                            NavigationLink(destination: DanDetailView(category: category)) {
                                HStack(spacing: 12) {
//                                    ZStack {
//                                        RoundedRectangle(cornerRadius: 10)
//                                            .fill(
//                                                LinearGradient(
//                                                    colors: [Color.orange.opacity(0.18), Color.red.opacity(0.10)],
//                                                    startPoint: .topLeading,
//                                                    endPoint: .bottomTrailing
//                                                )
//                                            )
//                                            .frame(width: 44, height: 44)
//                                        
//                                        Image(systemName: "checkmark.seal.fill")
//                                            .font(.system(size: 18, weight: .bold))
//                                            .foregroundStyle(.orange)
//                                    }
                                    
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(category.title)
                                            .font(.system(size: 16, weight: .bold, design: .rounded))
                                            .foregroundColor(.primary)
                                        
                                        Text("\(category.sections.count) Sections")
                                            .font(.system(size: 12))
                                            .foregroundColor(.secondary)
                                    }
                                }
                                .padding(.vertical, 4)
                            }
                        }
                    } header: {
                        Text(ThemeUtils.versionAbbreviation(group.version))
                            .font(.system(size: 13, weight: .bold, design: .rounded))
                            .foregroundColor(.primary)
                    }
                }
            }
        }
        .navigationTitle("home.dan.title")
        .task {
            loadData()
        }
    }
    
    private func loadData() {
        categories = MaimaiDataFetcher.shared.loadCachedDanData()
        isLoading = false
    }
    
    private func versionName(for category: DanCategory) -> String {
        let title = category.title.trimmingCharacters(in: .whitespacesAndNewlines)
        
        let sequence = UserDefaults.standard.stringArray(forKey: "MaimaiVersionSequence") ?? []
        if let matched = sequence
            .sorted(by: { $0.count > $1.count })
            .first(where: { title.localizedCaseInsensitiveContains($0) || category.id.localizedCaseInsensitiveContains($0) }) {
            return matched
        }
        
        if let prefix = category.id.split(separator: "_").first, !prefix.isEmpty {
            return String(prefix)
        }
        
        return "Unknown"
    }
    
    private func versionSortKey(_ version: String) -> Int {
        let order = ThemeUtils.versionSortOrder(version)
        return order == 999 ? -1 : order
    }
    
    private func sortCategories(_ items: [DanCategory]) -> [DanCategory] {
        items.sorted { lhs, rhs in
            rankOrder(lhs.title) < rankOrder(rhs.title)
        }
    }
    
    private func rankOrder(_ title: String) -> Int {
        let normalized = title.lowercased()
        
        if normalized.contains("expert") { return -2 }
        if normalized.contains("master") { return -1 }
        
        if normalized.contains("真") { return 0 }
        if normalized.contains("超") { return 1 }
        if normalized.contains("檄") { return 2 }
        if normalized.contains("橙") { return 3 }
        if normalized.contains("暁") || normalized.contains("晓") { return 4 }
        if normalized.contains("桃") { return 5 }
        if normalized.contains("櫻") || normalized.contains("樱") { return 6 }
        if normalized.contains("紫") { return 7 }
        if normalized.contains("菫") { return 8 }
        if normalized.contains("白") { return 9 }
        if normalized.contains("雪") { return 10 }
        if normalized.contains("輝") || normalized.contains("辉") { return 11 }
        if normalized.contains("熊") { return 12 }
        if normalized.contains("華") || normalized.contains("华") { return 13 }
        if normalized.contains("爽") { return 14 }
        if normalized.contains("煌") { return 15 }
        if normalized.contains("舞") { return 16 }
        if normalized.contains("霸") { return 17 }
        
        if normalized.contains("初") { return 100 }
        if normalized.contains("二") { return 101 }
        if normalized.contains("三") { return 102 }
        if normalized.contains("四") { return 103 }
        if normalized.contains("五") { return 104 }
        if normalized.contains("六") { return 105 }
        if normalized.contains("七") { return 106 }
        if normalized.contains("八") { return 107 }
        if normalized.contains("九") { return 108 }
        if normalized.contains("十") { return 109 }
        
        return 999
    }
}

#Preview {
    NavigationStack {
        DanListView()
    }
}
