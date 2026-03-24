import SwiftUI
import SwiftData

@MainActor
struct MaimaiIconPicker: View {
    @Binding var avatarUrl: String?
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
                    Button {
                        selectIcon(icon)
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
    
    private func selectIcon(_ icon: MaimaiIcon) {
        avatarUrl = icon.iconUrl
        selectedImageData = nil // Clear custom data in parent sheet state
        dismiss()
    }
}
