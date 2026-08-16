import SwiftUI

struct HomeDashboardView<Content: View>: View {
    @Binding var profileEditMode: UserProfileEditView.Mode?
    @Binding var showingOnboarding: Bool
    @Binding var didShowOnboarding: Bool
    @ViewBuilder let content: Content

    var body: some View {
        ScrollView {
            content
                .padding(16)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("home.title")
        .sheet(item: $profileEditMode) { mode in
            NavigationStack {
                UserProfileEditView(mode: mode)
            }
        }
        .sheet(isPresented: $showingOnboarding) {
            FirstLaunchView(onCompleted: {
                didShowOnboarding = true
                showingOnboarding = false
            })
            .presentationDetents([.large])
            .presentationDragIndicator(.hidden)
            .interactiveDismissDisabled(true)
        }
    }
}
