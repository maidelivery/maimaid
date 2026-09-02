import SwiftUI

struct LetterGameLobbyView: View {
  let service: LetterGameService

  @State private var visibility = "public"
  @State private var roomCode = ""
  @State private var isShowingJoin = false

  var body: some View {
    List {
      Section("letterGame.create") {
        LetterGameCreateRoomPanel(
          visibility: $visibility,
          isLoading: service.isLoading,
          onCreate: createRoom
        )
      }

      Section("letterGame.publicRooms") {
        if service.publicRooms.isEmpty {
          ContentUnavailableView(
            "letterGame.noPublicRooms",
            systemImage: "person.3",
            description: Text("letterGame.noPublicRooms.message")
          )
        } else {
          ForEach(service.publicRooms) { room in
            Button {
              Task { await service.join(code: room.code) }
            } label: {
              HStack {
                Image(systemName: "globe")
                  .foregroundStyle(.tint)
                VStack(alignment: .leading) {
                  Text(room.code)
                    .font(.headline.monospaced())
                  Text("letterGame.players \(room.memberCount)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "arrow.right")
                  .foregroundStyle(.secondary)
              }
              .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .disabled(service.isLoading)
          }
        }
      }
    }
    .refreshable { await service.refreshPublicRooms() }
    .overlay {
      if service.isLoading { ProgressView() }
    }
    .toolbar {
      ToolbarItem(placement: .topBarTrailing) {
        Button("letterGame.join", systemImage: "rectangle.portrait.and.arrow.right") {
          isShowingJoin = true
        }
        .alert("letterGame.join", isPresented: $isShowingJoin) {
          TextField("letterGame.roomCode", text: $roomCode)
            .textInputAutocapitalization(.characters)
            .autocorrectionDisabled()
          Button("letterGame.cancel", role: .cancel) {
            roomCode = ""
          }
          Button("letterGame.join", action: joinRoom)
            .disabled(roomCode.count != 6 || service.isLoading)
        }
      }
    }
    .onChange(of: roomCode) { _, value in
      let sanitized = String(value.filter { $0.isLetter || $0.isNumber }.prefix(6)).uppercased()
      if roomCode != sanitized {
        roomCode = sanitized
      }
    }
  }

  private func createRoom() {
    Task { await service.createRoom(visibility: visibility) }
  }

  private func joinRoom() {
    Task {
      await service.join(code: roomCode)
      if service.room != nil {
        roomCode = ""
      }
    }
  }
}
