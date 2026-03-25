import SwiftUI
import SwiftData

struct CommunityAliasVotingBoardView: View {
    @Query private var songs: [Song]

    private let service = CommunityAliasService.shared
    @State private var backendSessionManager = BackendSessionManager.shared
    @State private var items: [CommunityAliasVotingBoardItem] = []
    @State private var isLoading = false
    @State private var inFlightVoteCandidateId: UUID?
    @State private var tipMessage: String?
    @State private var tipDismissTask: Task<Void, Never>?

    private struct SongVotingGroup: Identifiable {
        let songIdentifier: String
        let items: [CommunityAliasVotingBoardItem]
        var id: String { songIdentifier }
    }

    private var songTitleMap: [String: String] {
        Dictionary(uniqueKeysWithValues: songs.map { ($0.songIdentifier, $0.title) })
    }

    private var songMap: [String: Song] {
        Dictionary(uniqueKeysWithValues: songs.map { ($0.songIdentifier, $0) })
    }

    private var groupedItems: [SongVotingGroup] {
        let grouped = Dictionary(grouping: items, by: \.songIdentifier)
        return grouped
            .map { key, value in
                SongVotingGroup(
                    songIdentifier: key,
                    items: value.sorted { lhs, rhs in
                        let lhsDate = lhs.voteCloseAt ?? .distantFuture
                        let rhsDate = rhs.voteCloseAt ?? .distantFuture
                        if lhsDate != rhsDate {
                            return lhsDate < rhsDate
                        }
                        return lhs.createdAt > rhs.createdAt
                    }
                )
            }
            .sorted { lhs, rhs in
                let lhsTitle = songTitleMap[lhs.songIdentifier] ?? lhs.songIdentifier
                let rhsTitle = songTitleMap[rhs.songIdentifier] ?? rhs.songIdentifier
                return lhsTitle.localizedStandardCompare(rhsTitle) == .orderedAscending
            }
    }

    var body: some View {
        List {
            if !backendSessionManager.isConfigured {
                configurationHintSection
            } else if !backendSessionManager.isAuthenticated {
                loginHintSection
            } else if items.isEmpty && !isLoading {
                emptyStateSection
            } else {
                boardSection
            }
        }
        .navigationTitle(String(localized: "community.alias.board.title"))
        .navigationBarTitleDisplayMode(.inline)
        .overlay(alignment: .bottom) {
            if let tipMessage {
                Text(tipMessage)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.black.opacity(0.82), in: Capsule())
                    .padding(.bottom, 20)
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
        }
        .refreshable {
            await reloadBoard()
        }
        .task {
            await reloadBoard()
        }
        .onDisappear {
            tipDismissTask?.cancel()
            tipDismissTask = nil
        }
    }

    private var configurationHintSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 8) {
                Text("community.alias.board.config.title")
                    .font(.headline)
                Text("community.alias.board.config.message")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .padding(.vertical, 4)
        }
    }

    private var loginHintSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 10) {
                Text("community.alias.board.login.title")
                    .font(.headline)
                Text("community.alias.board.login.message")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                Button {
                    Task {
                        await backendSessionManager.checkSession()
                        await reloadBoard()
                    }
                } label: {
                    Text("community.alias.board.login.refresh")
                        .font(.system(size: 14, weight: .semibold))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
            }
            .padding(.vertical, 4)
        }
    }

    private var emptyStateSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 8) {
                Text("community.alias.board.empty.title")
                    .font(.headline)
                Text("community.alias.board.empty.message")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .padding(.vertical, 4)
        }
    }

    private var boardSection: some View {
        Group {
            if isLoading {
                Section {
                    HStack(spacing: 10) {
                        ProgressView()
                        Text("community.alias.board.loading")
                            .foregroundStyle(.secondary)
                    }
                }
            }

            ForEach(groupedItems) { group in
                Section {
                    ForEach(group.items) { item in
                        boardRow(item)
                    }
                } header: {
                    songGroupHeader(group)
                }
            }
        }
    }

    private func songGroupHeader(_ group: SongVotingGroup) -> some View {
        let song = songMap[group.songIdentifier]
        let songTitle = songTitleMap[group.songIdentifier] ?? group.songIdentifier
        let subtitle = String(localized: "community.alias.board.group.subtitle \(group.items.count)")

        return HStack(spacing: 12) {
            if let song {
                SongJacketView(
                    imageName: song.imageName,
                    size: 46,
                    cornerRadius: 9,
                    useThumbnail: true
                )
            } else {
                RoundedRectangle(cornerRadius: 9)
                    .fill(Color.primary.opacity(0.08))
                    .frame(width: 46, height: 46)
                    .overlay {
                        Image(systemName: "music.note")
                            .foregroundStyle(.secondary)
                    }
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(songTitle)
                    .font(.system(size: 14, weight: .semibold))
                    .lineLimit(1)
                Text(subtitle)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(.secondary)
            }

            Spacer()
        }
        .padding(.vertical, 4)
        .textCase(nil)
    }

    private func boardRow(_ item: CommunityAliasVotingBoardItem) -> some View {
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(item.aliasText)
                        .font(.system(size: 16, weight: .semibold))
                }

                Spacer()

                Text(String(localized: "community.alias.board.deadline \(formatDeadline(item.voteCloseAt))"))
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(.secondary)
            }

            HStack(spacing: 10) {
                voteButton(item: item, support: true)
                voteButton(item: item, support: false)
                Spacer()
            }
        }
        .padding(.vertical, 6)
    }

    private func voteButton(item: CommunityAliasVotingBoardItem, support: Bool) -> some View {
        let isSelected = support ? (item.myVote == 1) : (item.myVote == -1)
        let count = support ? item.supportCount : item.opposeCount
        let inFlight = inFlightVoteCandidateId == item.id

        return Button {
            Task {
                await submitVote(candidateId: item.id, support: support)
            }
        } label: {
            HStack(spacing: 6) {
                if inFlight {
                    ProgressView()
                        .controlSize(.mini)
                }
                Image(systemName: support ? (isSelected ? "hand.thumbsup.fill" : "hand.thumbsup") : (isSelected ? "hand.thumbsdown.fill" : "hand.thumbsdown"))
                Text(
                    support
                        ? (isSelected ? String(localized: "community.alias.vote.cancelSupport \(count)") : String(localized: "community.alias.vote.support \(count)"))
                        : (isSelected ? String(localized: "community.alias.vote.cancelOppose \(count)") : String(localized: "community.alias.vote.oppose \(count)"))
                )
            }
            .font(.system(size: 12, weight: .semibold))
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .foregroundStyle(isSelected ? .white : (support ? Color.green : Color.red))
            .background(
                RoundedRectangle(cornerRadius: 999)
                    .fill(isSelected ? (support ? Color.green : Color.red) : .clear)
            )
            .overlay(
                Capsule().strokeBorder((support ? Color.green : Color.red).opacity(0.35), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .disabled(inFlight)
    }

    private func formatDeadline(_ date: Date?) -> String {
        guard let date else { return "--" }
        return date.formatted(date: .abbreviated, time: .shortened)
    }

    private func reloadBoard() async {
        guard backendSessionManager.isConfigured else { return }
        if !backendSessionManager.isAuthenticated {
            await backendSessionManager.checkSession()
        }
        guard backendSessionManager.isAuthenticated else {
            items = []
            return
        }

        isLoading = true
        items = await service.fetchVotingBoard(limit: 150)
        isLoading = false
    }

    private func submitVote(candidateId: UUID, support: Bool) async {
        guard backendSessionManager.isAuthenticated else {
            showTip(String(localized: "community.alias.board.tip.loginRequired"))
            return
        }

        inFlightVoteCandidateId = candidateId
        defer { inFlightVoteCandidateId = nil }

        guard let result = await service.vote(candidateId: candidateId, support: support) else {
            showTip(service.lastVoteErrorMessage ?? String(localized: "community.alias.board.tip.voteFailedFallback"))
            return
        }

        if let index = items.firstIndex(where: { $0.id == candidateId }) {
            let old = items[index]
            items[index] = CommunityAliasVotingBoardItem(
                candidateId: old.candidateId,
                songIdentifier: old.songIdentifier,
                aliasText: old.aliasText,
                submitterId: old.submitterId,
                voteOpenAt: old.voteOpenAt,
                voteCloseAt: old.voteCloseAt,
                supportCount: result.supportCount,
                opposeCount: result.opposeCount,
                myVote: result.myVote,
                createdAt: old.createdAt
            )
        }

        showTip(String(localized: "community.alias.board.tip.voteUpdated"))
    }

    private func showTip(_ message: String) {
        tipDismissTask?.cancel()
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
            tipMessage = message
        }

        tipDismissTask = Task {
            try? await Task.sleep(for: .seconds(1.6))
            guard !Task.isCancelled else { return }
            if tipMessage == message {
                withAnimation {
                    tipMessage = nil
                }
            }
        }
    }
}

#Preview {
    NavigationStack {
        CommunityAliasVotingBoardView()
    }
}
