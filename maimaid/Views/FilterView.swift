import SwiftUI

struct FilterSettings {
    var selectedVersions: Set<String> = []
    var selectedDifficulties: Set<String> = []
    var selectedTypes: Set<String> = []
}

struct FilterView: View {
    @Environment(\.dismiss) var dismiss
    @Binding var settings: FilterSettings
    
    let allVersions: [String]
    let allDifficulties = ["basic", "advanced", "expert", "master", "remaster"]
    let allTypes = ["dx", "std", "utage"]
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    // Versions
                    filterSection(title: "版本") {
                        FlowLayout(spacing: 10) {
                            ForEach(allVersions, id: \.self) { version in
                                FilterChip(
                                    title: version,
                                    isSelected: settings.selectedVersions.contains(version),
                                    color: .blue
                                ) {
                                    toggleSet(&settings.selectedVersions, version)
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    
                    // Difficulties
                    filterSection(title: "难度") {
                        FlowLayout(spacing: 10) {
                            ForEach(allDifficulties, id: \.self) { diff in
                                FilterChip(
                                    title: diff.capitalized,
                                    isSelected: settings.selectedDifficulties.contains(diff),
                                    color: colorForDifficulty(diff)
                                ) {
                                    toggleSet(&settings.selectedDifficulties, diff)
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    
                    // Types
                    filterSection(title: "类型") {
                        HStack(spacing: 10) {
                            ForEach(allTypes, id: \.self) { type in
                                FilterChip(
                                    title: type.uppercased(),
                                    isSelected: settings.selectedTypes.contains(type),
                                    color: type == "dx" ? .orange : .blue
                                ) {
                                    toggleSet(&settings.selectedTypes, type)
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(20)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("筛选")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") { dismiss() }
                        .fontWeight(.bold)
                }
                ToolbarItem(placement: .topBarLeading) {
                    Button("重置") {
                        withAnimation(.spring(response: 0.3)) {
                            settings = FilterSettings()
                        }
                    }
                }
            }
        }
    }
    
    @ViewBuilder
    private func filterSection<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(.secondary)
                .padding(.leading, 4)
            
            content()
                .padding(16)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
        }
    }
    
    private func toggleSet(_ set: inout Set<String>, _ value: String) {
        if set.contains(value) {
            set.remove(value)
        } else {
            set.insert(value)
        }
    }
    
    private func colorForDifficulty(_ diff: String) -> Color {
        ThemeUtils.colorForDifficulty(diff)
    }
}

// MARK: - Components

struct FilterChip: View {
    let title: String
    let isSelected: Bool
    var color: Color = .blue
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .foregroundColor(isSelected ? .white : .primary)
                .background(
                    isSelected ? color : Color.primary.opacity(0.06),
                    in: Capsule()
                )
                .overlay(
                    Capsule()
                        .strokeBorder(isSelected ? color.opacity(0.3) : Color.primary.opacity(0.08), lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .animation(.spring(response: 0.25), value: isSelected)
    }
}

// Simple FlowLayout for wrapping chips
struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = computeRows(subviews: subviews, proposal: proposal)
        if rows.isEmpty { return .zero }
        
        let totalHeight = rows.reduce(0) { $0 + $1.height } + CGFloat(rows.count - 1) * spacing
        let maxWidth = rows.reduce(0) { max($0, $1.width) }
        
        return CGSize(width: maxWidth, height: totalHeight)
    }
    
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let rows = computeRows(subviews: subviews, proposal: proposal)
        var y = bounds.minY
        
        for row in rows {
            var x = bounds.minX
            for index in row.range {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }
    
    private struct Row {
        var range: Range<Int>
        var width: CGFloat
        var height: CGFloat
    }
    
    private func computeRows(subviews: Subviews, proposal: ProposedViewSize) -> [Row] {
        var rows: [Row] = []
        let maxWidth = proposal.width ?? .infinity
        
        var currentX: CGFloat = 0
        var currentRowStart = 0
        var currentRowHeight: CGFloat = 0
        
        for (index, subview) in subviews.enumerated() {
            let size = subview.sizeThatFits(.unspecified)
            
            if currentX + size.width > maxWidth && index > currentRowStart {
                rows.append(Row(range: currentRowStart..<index, width: currentX - spacing, height: currentRowHeight))
                currentX = 0
                currentRowStart = index
                currentRowHeight = 0
            }
            
            currentX += size.width + spacing
            currentRowHeight = max(currentRowHeight, size.height)
        }
        
        if currentRowStart < subviews.count {
            rows.append(Row(range: currentRowStart..<subviews.count, width: currentX - spacing, height: currentRowHeight))
        }
        
        return rows
    }
}