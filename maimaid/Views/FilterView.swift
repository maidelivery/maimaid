import SwiftUI

struct FilterSettings {
    var selectedVersions: Set<String> = []
    var selectedDifficulties: Set<String> = []
    var selectedTypes: Set<String> = []
    var bpmRange: ClosedRange<Double> = 50...300
    var isBpmFilterActive: Bool = false
}

struct FilterView: View {
    @Environment(\.dismiss) var dismiss
    @Binding var settings: FilterSettings
    
    let allVersions: [String]
    let allDifficulties = ["basic", "advanced", "expert", "master", "remaster"]
    let allTypes = ["dx", "std", "utage"]
    
    var body: some View {
        NavigationStack {
            Form {
                // Versions - 增加了垂直 padding 和内部 FlowLayout 的 spacing
                Section(header: Text("Version").foregroundColor(.white.opacity(0.7))) {
                    FlowLayout(spacing: 12) { // 这里将 8 改为了 12，增加了 Chip 之间的间距
                        ForEach(allVersions, id: \.self) { version in
                            FilterChip(title: version, isSelected: settings.selectedVersions.contains(version)) {
                                if settings.selectedVersions.contains(version) {
                                    settings.selectedVersions.remove(version)
                                } else {
                                    settings.selectedVersions.insert(version)
                                }
                            }
                        }
                    }
                    .padding(.vertical, 12) // 增加了 Section 内部的上下间距
                    .padding(.horizontal, 4) // 增加了左右微调
                }
                .listRowBackground(Color.white.opacity(0.1).background(.ultraThinMaterial))
                
                // Difficulties
                Section(header: Text("Difficulty").foregroundColor(.white.opacity(0.7))) {
                    FlowLayout(spacing: 10) {
                        ForEach(allDifficulties, id: \.self) { diff in
                            FilterChip(title: diff.capitalized, isSelected: settings.selectedDifficulties.contains(diff)) {
                                if settings.selectedDifficulties.contains(diff) {
                                    settings.selectedDifficulties.remove(diff)
                                } else {
                                    settings.selectedDifficulties.insert(diff)
                                }
                            }
                        }
                    }
                    .padding(.vertical, 10)
                }
                .listRowBackground(Color.white.opacity(0.1).background(.ultraThinMaterial))
                
                // Types
                Section(header: Text("Type").foregroundColor(.white.opacity(0.7))) {
                    HStack(spacing: 12) { // 这里也增加了 HStack 的间距
                        ForEach(allTypes, id: \.self) { type in
                            FilterChip(title: type.uppercased(), isSelected: settings.selectedTypes.contains(type)) {
                                if settings.selectedTypes.contains(type) {
                                    settings.selectedTypes.remove(type)
                                } else {
                                    settings.selectedTypes.insert(type)
                                }
                            }
                        }
                    }
                    .padding(.vertical, 10)
                }
                .listRowBackground(Color.white.opacity(0.1).background(.ultraThinMaterial))
                
                // BPM Range
                Section(header: Text("BPM Range").foregroundColor(.white.opacity(0.7))) {
                    Toggle("Enable BPM Filter", isOn: $settings.isBpmFilterActive)
                        .tint(.blue)
                    
                    if settings.isBpmFilterActive {
                        VStack(spacing: 12) {
                            HStack {
                                Text("\(Int(settings.bpmRange.lowerBound))")
                                Spacer()
                                Text("\(Int(settings.bpmRange.upperBound))")
                            }
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.7))
                            
                            Slider(value: Binding(get: { settings.bpmRange.upperBound }, set: { settings.bpmRange = settings.bpmRange.lowerBound...$0 }), in: 50...300)
                                .tint(.blue)
                        }
                        .padding(.vertical, 8)
                    }
                }
                .listRowBackground(Color.white.opacity(0.1).background(.ultraThinMaterial))
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Filters")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                        .fontWeight(.bold)
                }
                ToolbarItem(placement: .topBarLeading) {
                    Button("Reset") {
                        settings = FilterSettings()
                    }
                }
            }
        }
    }
}

// MARK: - Components

struct FilterChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline)
                .padding(.horizontal, 14) // 增加文字左右内边距，让 Chip 看起来大一点
                .padding(.vertical, 8)    // 增加文字上下内边距
                .background(isSelected ? Color.blue : Color.white.opacity(0.1))
                .cornerRadius(12)
                .foregroundColor(.white)
        }
        .buttonStyle(.plain)
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