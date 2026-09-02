import SwiftData
import SwiftUI

struct LetterGameView: View {
    @Query(filter: #Predicate<UserProfile> { $0.isActive == true })
    private var activeProfiles: [UserProfile]
    @Query(
        filter: #Predicate<SongCollection> { $0.deletedAt == nil },
        sort: \SongCollection.sortIndex
    )
    private var collections: [SongCollection]
    @Query(filter: #Predicate<SongCollectionItem> { $0.deletedAt == nil })
    private var collectionItems: [SongCollectionItem]
    @Query private var songs: [Song]

    @State private var service = LetterGameService()
    @State private var session = BackendSessionManager.shared
    @State private var isShowingSettings = false
    @State private var isConfirmingLeave = false

    private var activeProfile: UserProfile? { activeProfiles.first }
    private var navigationTitle: String {
        service.room.map { String(localized: "letterGame.room \($0.code)") }
            ?? String(localized: "letterGame.title")
    }

    private var availableCategories: [String] {
        Set(songs.map(\.category))
            .filter {
                !$0.isEmpty
                    && !$0.localizedStandardContains("utage")
                    && !$0.contains("宴")
            }
            .sorted()
    }

    var body: some View {
        Group {
            if !session.isAuthenticated {
                LetterGameLoginRequiredView()
            } else if let room = service.room {
                LetterGameRoomContentView(
                    service: service,
                    room: room,
                    localAvatarData: activeProfile?.avatarData,
                    localAvatarURL: activeProfile?.avatarUrl
                )
            } else {
                LetterGameLobbyView(service: service)
            }
        }
        .navigationTitle(navigationTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { roomToolbar }
        .task(id: session.currentUser?.id) { await service.run() }
        .onDisappear(perform: service.disconnect)
        .alert("letterGame.error", isPresented: $service.isShowingError) {
            Button("letterGame.dismiss", role: .cancel) {}
        } message: {
            Text(service.errorMessage)
        }
    }

    @ToolbarContentBuilder
    private var roomToolbar: some ToolbarContent {
        if let room = service.room {
            ToolbarItemGroup(placement: .topBarTrailing) {
                ShareLink(item: room.code) {
                    Label("letterGame.shareCode", systemImage: "square.and.arrow.up")
                }

                Button("letterGame.settings", systemImage: "slider.horizontal.3") {
                    isShowingSettings = true
                }
                .sheet(isPresented: $isShowingSettings) {
                    LetterGameRoomSettingsView(
                        service: service,
                        room: room,
                        versions: UserDefaults.app.maimaiVersionSequence,
                        categories: availableCategories,
                        collections: collections,
                        collectionItems: collectionItems
                    )
                }

                Button(
                    "letterGame.leave",
                    systemImage: "rectangle.portrait.and.arrow.right",
                    role: .destructive
                ) {
                    isConfirmingLeave = true
                }
                .confirmationDialog(
                    "letterGame.leaveConfirm.title",
                    isPresented: $isConfirmingLeave,
                    titleVisibility: .visible
                ) {
                    Button("letterGame.leave", role: .destructive) {
                        Task { await service.leaveRoom() }
                    }
                    Button("letterGame.cancel", role: .cancel) {}
                } message: {
                    Text("letterGame.leaveConfirm.message")
                }
            }
        }
    }
}
