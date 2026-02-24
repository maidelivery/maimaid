import SwiftUI

struct SongRowView: View {
    let song: Song
    
    var body: some View {
        HStack(spacing: 16) {
            // Jacket Image with local support
            SongJacketView(imageName: song.imageName, remoteUrl: song.imageUrl, size: 60)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(song.title)
                    .font(.headline)
                    .foregroundColor(.primary)
                    .lineLimit(1)
                
                Text(song.artist)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
                
                HStack {
                    if let version = song.version {
                        Text(version)
                            .font(.caption2)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(song.sheets.contains(where: { $0.type.lowercased() == "dx" }) ? Color.orange.opacity(0.8) : Color.blue.opacity(0.8))
                            .cornerRadius(4)
                            .foregroundColor(.white)
                    }
                    
                    Text(song.category)
                        .font(.caption2)
                        .foregroundColor(.secondary.opacity(0.7))
                }
            }
            
            Spacer()
            
            // Difficulty circles - Show only DX if available, otherwise STD
            let prioritizedSheets: [Sheet] = {
                let dxSheets = song.sheets.filter { $0.type.lowercased() == "dx" }
                if !dxSheets.isEmpty {
                    return dxSheets.sorted(by: { difficultyOrder($0.difficulty) > difficultyOrder($1.difficulty) })
                }
                return song.sheets
                    .filter { $0.type.lowercased() == "std" }
                    .sorted(by: { difficultyOrder($0.difficulty) > difficultyOrder($1.difficulty) })
            }()
            
            HStack(spacing: 4) {
                ForEach(prioritizedSheets) { sheet in
                    Circle()
                        .fill(colorForDifficulty(sheet.difficulty))
                        .frame(width: 8, height: 8)
                }
            }
        }
        .padding()
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.primary.opacity(0.1), lineWidth: 0.5)
        )
        .padding(.horizontal)
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
    
    private func colorForDifficulty(_ difficulty: String) -> Color {
        let low = difficulty.lowercased()
        if low.contains("basic") { return .green }
        if low.contains("advanced") { return .orange }
        if low.contains("expert") { return .red }
        if low.contains("master") { return .purple }
        if low.contains("remaster") { return .white }
        return Color(red: 0.8, green: 0.2, blue: 0.8) // Utage - Purple/Pink
    }
}
