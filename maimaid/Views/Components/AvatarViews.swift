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

    var body: some View {
        Section {
            HStack {
                Spacer()

                VStack(spacing: 12) {
                    PhotosPicker(selection: $selectedItem, matching: .images) {
                        ZStack {
                            AvatarImageView(
                                imageData: selectedImageData,
                                avatarURL: avatarURL,
                                size: 100
                            )

                            VStack {
                                Spacer()

                                Text("profile.edit.changeAvatar")
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundStyle(.white)
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

                    if selectedImageData != nil || avatarURL != nil {
                        Button("profile.edit.clearAvatar", role: .destructive) {
                            selectedImageData = nil
                            avatarURL = nil
                        }
                        .font(.subheadline)
                    }
                }

                Spacer()
            }
            .listRowBackground(Color.clear)
            .padding(.vertical, 10)
        }
    }
}
