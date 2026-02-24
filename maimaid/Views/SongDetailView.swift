import SwiftUI
import SwiftData

struct SongDetailView: View {
    let song: Song
    @Environment(\.modelContext) private var modelContext
    
    @State private var selectedSheet: Sheet? = nil
    @State private var selectedType: String = ""
    @State private var copyStatus: [String: Bool] = [:]
    
    init(song: Song) {
        self.song = song
        let types = Set(song.sheets.map { $0.type.lowercased() })
        if types.contains("dx") {
            _selectedType = State(initialValue: "dx")
        } else if types.contains("std") {
            _selectedType = State(initialValue: "std")
        } else {
            _selectedType = State(initialValue: types.first ?? "")
        }
    }
    
    var body: some View {
        List {
            // --- Song Header ---
            Section {
                VStack(spacing: 20) {
                    SongJacketView(imageName: song.imageName, remoteUrl: song.imageUrl, size: 200, cornerRadius: 24)
                        .shadow(color: .black.opacity(0.4), radius: 15, x: 0, y: 8)
                    
                    VStack(spacing: 6) {
                        Text(song.title)
                            .font(.title2)
                            .fontWeight(.black)
                            .foregroundColor(.white)
                            .multilineTextAlignment(.center)
                        Text(song.artist)
                            .font(.subheadline)
                            .foregroundColor(.white.opacity(0.6))
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets())
            
            // --- Version Picker ---
            let availableTypes = Array(Set(song.sheets.map { $0.type.lowercased() })).sorted().reversed()
            if availableTypes.count > 1 {
                Section {
                    Picker("Version", selection: $selectedType) {
                        ForEach(availableTypes, id: \.self) { type in
                            Text(type.uppercased() == "STD" ? "标准" : type.uppercased()).tag(type)
                        }
                    }
                    .pickerStyle(.segmented)
                }
                .listRowBackground(Color.clear)
            }

            // --- Basic Info (无缝块) ---
            Section(header: Text("基本信息").font(.caption).foregroundColor(.white.opacity(0.5))) {
                VStack(spacing: 0) {
                    infoRow(icon: "music.note", label: "曲名", value: song.title)
                    Divider().background(Color.white.opacity(0.1)).padding(.leading, 40)
                    infoRow(icon: "person.fill", label: "艺术家", value: song.artist)
                    
                    if let keywords = song.searchKeywords, !keywords.isEmpty {
                        Divider().background(Color.white.opacity(0.1)).padding(.leading, 40)
                        infoRow(icon: "tag.fill", label: "别名", value: keywords.replacingOccurrences(of: ",", with: ", "))
                    }
                    
                    Divider().background(Color.white.opacity(0.1)).padding(.leading, 40)
                    infoRow(icon: "square.grid.2x2.fill", label: "分类", value: song.category)
                    Divider().background(Color.white.opacity(0.1)).padding(.leading, 40)
                    infoRow(icon: "gauge.with.dots.needle.bottom.50percent", label: "BPM", value: song.bpm != nil ? "\(Int(song.bpm!))" : "-")
                    Divider().background(Color.white.opacity(0.1)).padding(.leading, 40)
                    infoRow(icon: "clock.arrow.circlepath", label: "版本", value: song.version ?? "-")
                }
            }
            .listRowBackground(Color.white.opacity(0.1).background(.ultraThinMaterial))
            .listRowInsets(EdgeInsets())

            // --- Sheets List (修正宽度和收回动画) ---
            Section(header: Text("谱面详情").font(.caption).foregroundColor(.white.opacity(0.5))) {
                let filteredSheets = song.sheets.filter { $0.type.lowercased() == selectedType }
                let sortedSheets = filteredSheets.sorted(by: { difficultyOrder($0.difficulty) > difficultyOrder($1.difficulty) })
                
                ForEach(sortedSheets) { sheet in
                    SheetRowView(sheet: sheet) {
                        selectedSheet = sheet
                    }
                    .listRowBackground(Color.clear) // 必须背景透明，让 SheetRowView 自己的背景显示
                    .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0)) // 左右为0，内部控制padding
                    .listRowSeparator(.hidden)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(Color.black.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
    }
    
    private func difficultyOrder(_ difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "basic": return 0
        case "advanced": return 1
        case "expert": return 2
        case "master": return 3
        case "remaster": return 4
        default: return -1
        }
    }
    
    @ViewBuilder
    private func infoRow(icon: String, label: String, value: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 14))
                .foregroundColor(.blue.opacity(0.7))
                .frame(width: 24)
            Text(label)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(.white.opacity(0.5))
                .frame(width: 50, alignment: .leading)
            Text(value)
                .font(.system(size: 14))
                .foregroundColor(.white)
                .lineLimit(1)
            Spacer()
            Button { handleCopy(value: value, label: label) } label: {
                Image(systemName: copyStatus[label] == true ? "checkmark.circle.fill" : "doc.on.doc")
                    .font(.system(size: 14))
                    .foregroundColor(copyStatus[label] == true ? .green : .white.opacity(0.3))
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }

    private func handleCopy(value: String, label: String) {
        UIPasteboard.general.string = value
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        withAnimation(.easeInOut(duration: 0.2)) { copyStatus[label] = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
            withAnimation { copyStatus[label] = false }
        }
    }
}

struct SheetRowView: View {
    let sheet: Sheet
    let action: () -> Void
    @State private var isExpanded = false
    
    var body: some View {
        VStack(spacing: 0) {
            // 主卡片
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(sheet.type.uppercased() == "STD" ? "标准" : sheet.type.uppercased())
                            .font(.system(size: 9, weight: .black))
                            .padding(.horizontal, 4)
                            .padding(.vertical, 2)
                            .background(sheet.type.lowercased() == "dx" ? Color.orange : Color.blue)
                            .cornerRadius(4)
                        
                        Text(sheet.difficulty.uppercased())
                            .font(.system(size: 13, weight: .black))
                            .foregroundColor(colorForDifficulty(sheet.difficulty))
                    }
                    if let designer = sheet.noteDesigner, !designer.isEmpty {
                        Text(designer)
                            .font(.system(size: 10))
                            .foregroundColor(.white.opacity(0.4))
                            .lineLimit(1)
                    }
                }
                Spacer()
                Text("Lv.\(sheet.level)")
                    .font(.system(size: 18, weight: .black, design: .rounded))
                    .foregroundColor(colorForDifficulty(sheet.difficulty))
                
                Image(systemName: "chevron.down")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(.white.opacity(0.2))
                    .rotationEffect(.degrees(isExpanded ? 180 : 0))
            }
            .padding(.all, 16)
            .contentShape(Rectangle())
            .onTapGesture {
                withAnimation(.easeInOut(duration: 0.25)) {
                    isExpanded.toggle()
                }
            }
            
            // 展开内容 (修正收回动画的关键)
            if isExpanded {
                VStack(spacing: 16) {
                    Divider().background(Color.white.opacity(0.05))
                    
                    if sheet.total != nil {
                        noteCountsGrid(for: sheet)
                    }
                    
                    Button(action: action) {
                        Text("记录成绩")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(colorForDifficulty(sheet.difficulty).opacity(0.2))
                            .cornerRadius(10)
                    }
                }
                .padding([.horizontal, .bottom], 16)
            }
        }
        .background(Color.white.opacity(0.1).background(.ultraThinMaterial))
        .cornerRadius(10)
        // 关键：在这一层级绑定动画，确保收回时能够追踪状态
        .animation(.easeInOut(duration: 0.25), value: isExpanded)
    }
    
    @ViewBuilder
    private func noteCountsGrid(for sheet: Sheet) -> some View {
        let totalWeight = calculateTotalWeight(sheet)
        let columns = [GridItem(.flexible()), GridItem(.flexible())]
        
        LazyVGrid(columns: columns, spacing: 8) {
            noteItem(label: "TAP", count: sheet.tap, weight: 1.0, total: totalWeight)
            noteItem(label: "HOLD", count: sheet.hold, weight: 2.0, total: totalWeight)
            noteItem(label: "SLIDE", count: sheet.slide, weight: 3.0, total: totalWeight)
            noteItem(label: "TOUCH", count: sheet.touch, weight: 1.0, total: totalWeight)
            noteItem(label: "BREAK", count: sheet.breakCount, weight: 5.0, total: totalWeight)
        }
    }
    
    @ViewBuilder
    private func noteItem(label: String, count: Int?, weight: Double, total: Double) -> some View {
        if let count = count, count > 0 {
            HStack {
                Text(label)
                    .font(.system(size: 9, weight: .black))
                    .foregroundColor(.white.opacity(0.3))
                Spacer()
                Text("\(count)")
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                let rowWeight = Double(count) * weight
                let percent = total > 0 ? (rowWeight / total) * 100 : 0
                Text("\(Int(percent))%")
                    .font(.system(size: 9, design: .monospaced))
                    .foregroundColor(.orange.opacity(0.8))
            }
            .padding(8)
            .background(Color.white.opacity(0.05))
            .cornerRadius(6)
        }
    }

    private func calculateTotalWeight(_ sheet: Sheet) -> Double {
        (Double(sheet.tap ?? 0) * 1.0) + (Double(sheet.hold ?? 0) * 2.0) +
        (Double(sheet.slide ?? 0) * 3.0) + (Double(sheet.touch ?? 0) * 1.0) +
        (Double(sheet.breakCount ?? 0) * 5.0)
    }
    
    private func colorForDifficulty(_ difficulty: String) -> Color {
        let low = difficulty.lowercased()
        if low.contains("basic") { return .green }
        if low.contains("advanced") { return .orange }
        if low.contains("expert") { return .red }
        if low.contains("master") { return .purple }
        if low.contains("remaster") { return .white }
        return .pink
    }
}
