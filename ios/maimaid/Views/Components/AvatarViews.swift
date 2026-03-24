import SwiftUI
import PhotosUI

@MainActor
struct AvatarImageView: View {
    let imageData: Data?
    let avatarURL: String?
    var size: CGFloat
    var placeholderSystemName: String = "person.fill"
    var placeholderTint: Color = .secondary
    var placeholderBackground: Color = Color.secondary.opacity(0.1)

    var body: some View {
        avatarContent
            .frame(width: size, height: size)
            .clipShape(Circle())
    }

    @ViewBuilder
    private var avatarContent: some View {
        if let imageData, let uiImage = UIImage(data: imageData) {
            Image(uiImage: uiImage)
                .resizable()
                .aspectRatio(contentMode: .fill)
        } else if let cachedIconImage {
            Image(uiImage: cachedIconImage)
                .resizable()
                .aspectRatio(contentMode: .fill)
        } else if let remoteURL {
            AsyncImage(url: remoteURL) { image in
                image
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } placeholder: {
                ZStack {
                    Circle()
                        .fill(placeholderBackground)
                    ProgressView()
                }
            }
        } else {
            ZStack {
                Circle()
                    .fill(placeholderBackground)

                Image(systemName: placeholderSystemName)
                    .font(.system(size: size * 0.4))
                    .foregroundStyle(placeholderTint)
            }
        }
    }

    private var remoteURL: URL? {
        guard cachedIconImage == nil, let avatarURL else { return nil }
        return URL(string: avatarURL)
    }

    private var cachedIconImage: UIImage? {
        guard let avatarURL, let iconID = iconID(from: avatarURL) else { return nil }
        return ImageDownloader.shared.loadImage(iconId: iconID)
    }

    private func iconID(from avatarURL: String) -> Int? {
        let fileName = avatarURL.components(separatedBy: "/").last?
            .replacingOccurrences(of: ".png", with: "")

        guard let fileName else { return nil }
        return Int(fileName)
    }
}

@MainActor
struct EditableAvatarSection: View {
    @Binding var selectedItem: PhotosPickerItem?
    @Binding var selectedImageData: Data?
    @Binding var avatarURL: String?
    
    @State private var editorImage: UIImage?
    @State private var isShowingEditor = false
    @State private var isLoadingSelectedPhoto = false

    var body: some View {
        let previewImageData = selectedImageData
        let previewAvatarURL = avatarURL
        
        Section {
            HStack {
                Spacer()

                VStack(spacing: 14) {
                    AvatarImageView(
                        imageData: previewImageData,
                        avatarURL: previewAvatarURL,
                        size: 100
                    )
                    .frame(width: 100, height: 100)
                    .overlay(Circle().stroke(Color.primary.opacity(0.1), lineWidth: 1))
                    .shadow(color: .black.opacity(0.05), radius: 5, y: 2)

                    if isLoadingSelectedPhoto {
                        ProgressView()
                            .controlSize(.small)
                    }
                    
                    ViewThatFits {
                        HStack(spacing: 8) {
                            changeAvatarButton
                            clearAvatarButton
                        }
                        VStack(spacing: 8) {
                            changeAvatarButton
                            clearAvatarButton
                        }
                    }
                }

                Spacer()
            }
            .listRowBackground(Color.clear)
            .padding(.vertical, 10)
        }
        .sheet(isPresented: $isShowingEditor) {
            NavigationStack {
                if let editorImage {
                    AvatarCropEditorView(originalImage: editorImage) { imageData in
                        selectedImageData = imageData
                        avatarURL = nil
                    }
                }
            }
        }
        .onChange(of: selectedItem) { _, newItem in
            guard let newItem else { return }
            isLoadingSelectedPhoto = true
            
            Task {
                let loadedData = try? await newItem.loadTransferable(type: Data.self)
                let image = loadedData.flatMap(UIImage.init(data:))
                
                await MainActor.run {
                    selectedItem = nil
                    isLoadingSelectedPhoto = false
                    
                    if let image {
                        editorImage = image
                        isShowingEditor = true
                    }
                }
            }
        }
    }
    
    private var changeAvatarButton: some View {
        PhotosPicker(selection: $selectedItem, matching: .images) {
            Text("profile.edit.changeAvatar")
                .capsuleActionStyle()
        }
        .disabled(isLoadingSelectedPhoto)
    }
    
    private var clearAvatarButton: some View {
        Button("profile.edit.clearAvatar", role: .destructive) {
            selectedImageData = nil
            avatarURL = nil
        }
        .buttonStyle(.plain)
        .disabled(isLoadingSelectedPhoto || (selectedImageData == nil && avatarURL == nil))
        .opacity((selectedImageData == nil && avatarURL == nil) ? 0.45 : 1)
        .capsuleActionStyle(role: .destructive)
    }
}

private extension View {
    nonisolated func capsuleActionStyle(role: ButtonRole? = nil) -> some View {
        let isDestructive = role == .destructive
        
        return self
            .font(.caption.bold())
            .foregroundStyle(isDestructive ? .red : .primary)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(
                Capsule()
                    .fill(isDestructive ? Color.red.opacity(0.12) : Color.primary.opacity(0.08))
            )
    }
}
