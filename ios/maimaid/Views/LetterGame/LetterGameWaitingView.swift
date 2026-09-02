import SwiftUI

struct LetterGameWaitingView: View {
  let service: LetterGameService
  let room: LetterGameRoom
  let localAvatarData: Data?
  let localAvatarURL: String?

  private var acceptedMembers: [LetterGameRoomMember] {
    room.members.filter { ["accepted", "pending"].contains($0.status) }
  }

  private var currentMemberIsPending: Bool {
    room.members.first(where: { $0.userId == service.currentUserId })?.status == "pending"
  }

  var body: some View {
    List {
      if currentMemberIsPending {
        Section {
          Label("letterGame.waitingApproval", systemImage: "hourglass")
            .foregroundStyle(.secondary)
        }
      }

      Section("letterGame.playersTitle") {
        ForEach(acceptedMembers) { member in
          LetterGameMemberRow(
            service: service,
            room: room,
            member: member,
            localAvatarData: localAvatarData,
            localAvatarURL: localAvatarURL
          )
        }
      }

      Section("letterGame.settings") {
        LetterGameRoomSettingsSummaryView(room: room)
      }

      Section {
        Button("letterGame.start", systemImage: "play.fill") {
          Task { await service.startMatch() }
        }
        .disabled(!service.isHost || service.isLoading || currentMemberIsPending)
      } footer: {
        if !service.isHost {
          Text("letterGame.hostOnlyStart")
        }
      }
    }
    .overlay {
      if service.isLoading { ProgressView() }
    }
  }
}
