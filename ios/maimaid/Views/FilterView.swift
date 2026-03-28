import SwiftUI

struct FilterView: View {
    @Environment(\.dismiss) var dismiss
    @Binding var settings: FilterSettings
    
    let allCategories: [String]
    let allVersions: [String]
    let allDifficulties = ["Basic", "Advanced", "Expert", "Master", "Re: Master"]
    let allTypes = ["dx", "std", "utage"]
    
    var sortedVersions: [String] {
        allVersions.sorted { ThemeUtils.versionSortOrder($0) < ThemeUtils.versionSortOrder($1) }
    }
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    filterSection(title: "filter.quick") {
                        HStack {
                            Label("filter.favorites", systemImage: settings.showFavoritesOnly ? "star.fill" : "star")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(.primary)
                            Spacer()
                            Toggle("", isOn: $settings.showFavoritesOnly)
                                .labelsHidden()
                        }
                        
                        HStack {
                            Label("filter.hideDeleted", systemImage: settings.hideDeletedSongs ? "eye.slash.fill" : "eye.slash")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(.primary)
                            Spacer()
                            Toggle("", isOn: $settings.hideDeletedSongs)
                                .labelsHidden()
                        }
                    }

                    // Difficulty & Range Section (Grouped)
                    VStack(alignment: .leading, spacing: 8) {
                        filterSection(title: "filter.difficulty") {
                            VStack(alignment: .leading, spacing: 16) {
                                FlowLayout(spacing: 10) {
                                    ForEach(allDifficulties, id: \.self) { diff in
                                        FilterChip(
                                            title: diff,
                                            isSelected: settings.selectedDifficulties.contains(internalName(for: diff)),
                                            color: ThemeUtils.colorForDifficulty(internalName(for: diff), nil)
                                        ) {
                                            toggleSet(&settings.selectedDifficulties, internalName(for: diff))
                                        }
                                    }
                                }
                                
                                Divider()
                                
                                HStack {
                                    Text("filter.levelRange")
                                        .font(.system(size: 11, weight: .bold))
                                        .foregroundStyle(.secondary)
                                    Spacer()
                                    Text("\(settings.minLevel, specifier: "%.1f") - \(settings.maxLevel, specifier: "%.1f")")
                                        .font(.system(.subheadline, design: .monospaced, weight: .bold))
                                        .foregroundStyle(settings.selectedDifficulties.isEmpty ? AnyShapeStyle(.secondary) : AnyShapeStyle(.blue))
                                }
                                
                                RangeSlider(minValue: $settings.minLevel, maxValue: $settings.maxLevel, range: 1.0...15.0, step: 0.1, isActive: !settings.selectedDifficulties.isEmpty)
                                    .padding(.horizontal, 8)
                            }
                        }
                        
                        Text("filter.levelRange.hint")
                            .font(.system(size: 10))
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 4)
                    }
                    
                    // Categories
                    filterSection(title: "filter.category") {
                        FlowLayout(spacing: 10) {
                            ForEach(allCategories, id: \.self) { category in
                                FilterChip(
                                    title: category,
                                    isSelected: settings.selectedCategories.contains(category),
                                    color: .blue
                                ) {
                                    toggleSet(&settings.selectedCategories, category)
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    
                    // Versions
                    filterSection(title: "filter.version") {
                        FlowLayout(spacing: 10) {
                            ForEach(sortedVersions.reversed(), id: \.self) { version in
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
                    
                    // Types
                    filterSection(title: "filter.type") {
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
            .navigationTitle("filter.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("filter.done") { dismiss() }
                        .fontWeight(.bold)
                }
                ToolbarItem(placement: .topBarLeading) {
                    Button("filter.reset") {
                        withAnimation(.spring(response: 0.3)) {
                            settings = FilterSettings()
                        }
                    }
                }
            }
            .onChange(of: settings.hideDeletedSongs) { _, newValue in
                UserDefaults.app.hideDeletedSongs = newValue
            }
        }
    }
    
    @ViewBuilder
    private func filterSection<Content: View>(title: LocalizedStringKey, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(.secondary)
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
    
    private func internalName(for displayDiff: String) -> String {
        switch displayDiff {
        case "Re: Master": return "remaster"
        default: return displayDiff.lowercased()
        }
    }
    

}

// MARK: - Components

struct RangeSlider: View {
    @Binding var minValue: Double
    @Binding var maxValue: Double
    let range: ClosedRange<Double>
    let step: Double
    var isActive: Bool = true
    @State private var draggingHandle: DraggingHandle = .none
    
    private enum DraggingHandle {
        case none, min, max
    }
    
    var body: some View {
        GeometryReader { geometry in
            let totalWidth = geometry.size.width
            let rangeSpan = range.upperBound - range.lowerBound
            
            ZStack(alignment: .leading) {
                // Track
                Capsule()
                    .fill(Color.primary.opacity(0.1))
                    .frame(height: 4)
                
                // Active Track
                Capsule()
                    .fill(isActive ? Color.blue : Color.gray.opacity(0.5))
                    .frame(width: CGFloat((maxValue - minValue) / rangeSpan) * totalWidth, height: 4)
                    .offset(x: CGFloat((minValue - range.lowerBound) / rangeSpan) * totalWidth)
                
                // Min Handle (Visual)
                Circle()
                    .fill(Color.white)
                    .frame(width: 24, height: 24)
                    .shadow(radius: 2, y: 1)
                    .offset(x: CGFloat((minValue - range.lowerBound) / rangeSpan) * totalWidth - 12)
                
                // Max Handle (Visual)
                Circle()
                    .fill(Color.white)
                    .frame(width: 24, height: 24)
                    .shadow(radius: 2, y: 1)
                    .offset(x: CGFloat((maxValue - range.lowerBound) / rangeSpan) * totalWidth - 12)
                
                // Gesture Overlay
                Color.clear
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { value in
                                let relativeX = Double(value.location.x / totalWidth)
                                let newValue = range.lowerBound + relativeX * rangeSpan
                                let steppedValue = (newValue / step).rounded() * step
                                let clampedValue = Swift.min(Swift.max(range.lowerBound, steppedValue), range.upperBound)
                                
                                if draggingHandle == .none {
                                    // Logic to pick a handle when they overlap or determine closest
                                    if abs(clampedValue - minValue) < abs(clampedValue - maxValue) {
                                        draggingHandle = .min
                                    } else if abs(clampedValue - minValue) > abs(clampedValue - maxValue) {
                                        draggingHandle = .max
                                    } else {
                                        // Exactly overlapping! Pick based on movement direction
                                        if value.translation.width < 0 {
                                            draggingHandle = .min
                                        } else if value.translation.width > 0 {
                                            draggingHandle = .max
                                        }
                                    }
                                }
                                
                                switch draggingHandle {
                                case .min:
                                    minValue = Swift.min(clampedValue, maxValue)
                                case .max:
                                    maxValue = Swift.max(clampedValue, minValue)
                                case .none:
                                    break
                                }
                            }
                            .onEnded { _ in
                                draggingHandle = .none
                            }
                    )
            }
        }
        .frame(height: 24)
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
                .foregroundStyle(isSelected ? .white : .primary)
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
