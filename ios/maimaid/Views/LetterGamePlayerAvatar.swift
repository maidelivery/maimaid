import SwiftUI

struct LetterGamePlayerAvatar: View {
    let userId: String
    let currentUserId: String?
    let name: String
    let avatarURL: String?
    let localAvatarData: Data?
    let localAvatarURL: String?
    var size: Double = 42

    var body: some View {
        AvatarImageView(
            imageData: userId == currentUserId ? localAvatarData : nil,
            avatarURL: userId == currentUserId ? localAvatarURL ?? avatarURL : avatarURL,
            size: size
        )
        .accessibilityLabel(name)
    }
}
