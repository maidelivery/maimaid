import SwiftUI
import SwiftData
import PhotosUI

struct UserProfileEditView: View {
    enum Mode: Identifiable {
        case create
        case edit(UUID)

        var id: String {
            switch self {
            case .create: return "create"
            case .edit(let profileId): return profileId.uuidString
            }
        }
    }

    let mode: Mode

    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss

    @State private var name: String = ""
    @State private var plate: String = ""
    @State private var selectedServer: GameServer = .jp

    @State private var avatarUrl: String?
    @State private var selectedItem: PhotosPickerItem?
    @State private var selectedImageData: Data?
    @State private var isSaving = false
    @State private var errorMessage: String?

    private var isEditing: Bool {
        if case .edit = mode { return true }
        return false
    }

    private var profileId: UUID? {
        if case .edit(let profileId) = mode { return profileId }
        return nil
    }

    var body: some View {
        Form {
            EditableAvatarSection(
                selectedItem: $selectedItem,
                selectedImageData: $selectedImageData,
                avatarURL: $avatarUrl
            )

            Section("userProfile.section.basic") {
                TextField("userProfile.name", text: $name)
                TextField("profile.edit.titleName", text: $plate)

                Picker("userProfile.server", selection: $selectedServer) {
                    ForEach(GameServer.allCases) { server in
                        Text(server.displayName).tag(server)
                    }
                }

            }

            Section("profile.edit.presetIcon") {
                NavigationLink {
                    MaimaiIconPicker(avatarUrl: $avatarUrl, selectedImageData: $selectedImageData)
                } label: {
                    HStack {
                        Text("profile.edit.presetIcon.select")
                        Spacer()
                        if selectedImageData != nil || avatarUrl != nil {
                            AvatarImageView(
                                imageData: selectedImageData,
                                avatarURL: avatarUrl,
                                size: 30,
                                placeholderSystemName: "photo.circle.fill"
                            )
                        }
                    }
                }
            }
        }
        .navigationTitle(isEditing ? "userProfile.editTitle" : "userProfile.createTitle")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("userProfile.cancel") {
                    dismiss()
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("userProfile.save") {
                    save()
                }
                .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isSaving)
            }
        }
        .task {
            if let profileId, let profile = fetchProfile(profileId) {
                name = profile.name
                plate = profile.plate ?? ""
                selectedServer = GameServer(rawValue: profile.server) ?? .jp
                selectedImageData = profile.avatarData
                avatarUrl = profile.avatarUrl
            }
        }
        .interactiveDismissDisabled(isSaving)
        .alert(
            "userProfile.error.title",
            isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )
        ) {
            Button("common.ok", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "")
        }
    }

    private func fetchProfile(_ profileId: UUID) -> UserProfile? {
        let descriptor = FetchDescriptor<UserProfile>(
            predicate: #Predicate<UserProfile> { $0.id == profileId }
        )
        return try? modelContext.fetch(descriptor).first
    }

    private func save() {
        isSaving = true

        let values = UserProfileFormValues(
            name: name,
            plate: plate,
            server: selectedServer,
            avatarData: selectedImageData,
            avatarURL: avatarUrl
        )
        do {
            let savedProfileId = try UserProfileMutationService.save(
                profileId: profileId,
                values: values,
                context: modelContext
            )
            isSaving = false
            dismiss()
            Task {
                await UserProfileMutationService.synchronizeProfileUpdate(
                    profileId: savedProfileId,
                    context: modelContext
                )
            }
        } catch {
            isSaving = false
            errorMessage = error.localizedDescription
        }
    }
}
