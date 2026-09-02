import SwiftUI

struct LetterGameRoomContentView: View {
    let service: LetterGameService
    let room: LetterGameRoom
    let localAvatarData: Data?
    let localAvatarURL: String?

    var body: some View {
        Group {
            switch service.match?.status {
            case "active":
                if let match = service.match {
                    LetterGamePlayingView(
                        service: service,
                        room: room,
                        match: match,
                        localAvatarData: localAvatarData,
                        localAvatarURL: localAvatarURL
                    )
                }
            case "finished", "abandoned":
                if let match = service.match {
                    LetterGameResultsView(
                        service: service,
                        match: match,
                        localAvatarData: localAvatarData,
                        localAvatarURL: localAvatarURL
                    )
                }
            default:
                LetterGameWaitingView(
                    service: service,
                    room: room,
                    localAvatarData: localAvatarData,
                    localAvatarURL: localAvatarURL
                )
            }
        }
    }
}
