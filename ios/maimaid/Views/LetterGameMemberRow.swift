import SwiftUI

struct LetterGameMemberRow: View {
    let service: LetterGameService
    let room: LetterGameRoom
    let member: LetterGameRoomMember
    let localAvatarData: Data?
    let localAvatarURL: String?

    var body: some View {
        HStack {
            LetterGamePlayerAvatar(
                userId: member.userId,
                currentUserId: service.currentUserId,
                name: member.name,
                avatarURL: member.avatarUrl,
                localAvatarData: localAvatarData,
                localAvatarURL: localAvatarURL,
                size: 38
            )
            VStack(alignment: .leading) {
                Text(member.name)
                    .lineLimit(1)
                Text(statusText)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if service.isHost && member.userId != room.hostUserId && member.status == "pending" {
                HStack(spacing: 6) {
                    Button("letterGame.approve", action: approve)
                        .buttonStyle(.borderedProminent)
                        .controlSize(.small)
                        .tint(.green)
                    Button("letterGame.reject", role: .destructive, action: reject)
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                        .tint(.red)
                }
            } else if service.isHost && member.userId != room.hostUserId {
                Button("letterGame.remove", role: .destructive, action: remove)
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                    .tint(.red)
            }
        }
    }

    private var statusText: LocalizedStringKey {
        if member.userId == room.hostUserId {
            "letterGame.host"
        } else if member.status == "pending" {
            "letterGame.waitingApproval"
        } else if member.userId == service.currentUserId {
            "letterGame.you"
        } else {
            "letterGame.ready"
        }
    }

    private func approve() {
        Task { await service.approve(member) }
    }

    private func reject() {
        Task { await service.reject(member) }
    }

    private func remove() {
        Task { await service.kick(member) }
    }
}
