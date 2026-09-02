import SwiftData
import SwiftUI

struct LetterGameView: View {
    @Environment(\.dismiss) private var dismiss
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

    private var isWaitingRoom: Bool {
        guard service.room != nil else { return false }
        return !["active", "finished", "abandoned"].contains(service.match?.status)
    }

    private var isShowingResults: Bool {
        ["finished", "abandoned"].contains(service.match?.status)
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
        .navigationBarBackButtonHidden(service.room != nil)
        .toolbarVisibility(service.room == nil ? .automatic : .hidden, for: .tabBar)
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
            ToolbarItem(placement: .topBarLeading) {
                Button("letterGame.back", systemImage: "chevron.backward", action: handleBack)
                .confirmationDialog(
                    "letterGame.leaveConfirm.title",
                    isPresented: $isConfirmingLeave,
                    titleVisibility: .visible
                ) {
                    Button("letterGame.leave", role: .destructive, action: leaveAndDismiss)
                    Button("letterGame.cancel", role: .cancel) {}
                } message: {
                    Text("letterGame.leaveConfirm.message")
                }
            }

            if isWaitingRoom {
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
                }
            }
        }
    }

    private func handleBack() {
        if isShowingResults {
            Task { await service.reopenRoom() }
        } else {
            isConfirmingLeave = true
        }
    }

    private func leaveAndDismiss() {
        Task {
            await service.leaveRoom()
            if service.room == nil {
                dismiss()
            }
        }
    }
}
