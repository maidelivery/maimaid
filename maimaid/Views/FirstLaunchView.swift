import SwiftUI

struct FirstLaunchView: View {
    @Environment(\.modelContext) private var modelContext
    @AppStorage(UserDefaultsKeys.didPerformInitialSync) private var didPerformInitialSync = false
    @AppStorage(AppStorageKeys.didShowOnboarding) private var didShowOnboarding = false

    let onCompleted: () -> Void

    private let features: [(icon: String, titleKey: LocalizedStringKey, detailKey: LocalizedStringKey)] = [
        ("bolt.fill", "onboarding.feature.fastScan", "onboarding.feature.fastScan.detail"),
        ("lock.fill", "onboarding.feature.localOffline", "onboarding.feature.localOffline.detail"),
        ("person.2.fill", "onboarding.feature.multiUser", "onboarding.feature.multiUser.detail")
    ]
    
    @State private var downloadError: String?
    @State private var didTriggerCompletion = false
    
    private var fetcher: MaimaiDataFetcher { MaimaiDataFetcher.shared }
    private var isSyncing: Bool { fetcher.isSyncing }
    
    private var actionTitleKey: LocalizedStringKey {
        if downloadError != nil { return "onboarding.download.action.retry" }
        return "onboarding.download.action.start"
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                heroCard
                featureCard
            }
            .padding(.horizontal, 16)
            .padding(.top, 64)
            .padding(.bottom, 36)
        }
        .scrollIndicators(.hidden)
        .background(Color(.systemGroupedBackground).ignoresSafeArea())
        .safeAreaInset(edge: .bottom) {
            bottomAction
        }
        .onAppear {
            if didPerformInitialSync {
                didShowOnboarding = true
                completeIfNeeded()
            }
        }
        .onChange(of: fetcher.isSyncing) { _, newValue in
            if newValue {
                downloadError = nil
                return
            }
            if didPerformInitialSync {
                didShowOnboarding = true
                completeIfNeeded()
            }
        }
    }

    private var heroCard: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top, spacing: 16) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("onboarding.titlePrefix")
                        .font(.system(size: 34, weight: .heavy))
                        .foregroundStyle(.primary)
                        .fixedSize(horizontal: false, vertical: true)

                    Text("onboarding.appName")
                        .font(.system(size: 34, weight: .heavy))
                        .foregroundStyle(Color.accentColor)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .accessibilityElement(children: .combine)

                Spacer(minLength: 12)

                Image("OnboardingAppIcon")
                    .resizable()
                    .interpolation(.high)
                    .scaledToFit()
                    .frame(width: 88, height: 88)
                    .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 22, style: .continuous)
                            .stroke(Color.primary.opacity(0.08), lineWidth: 1)
                    )
                    .shadow(color: .black.opacity(0.06), radius: 8, y: 4)
                    .accessibilityHidden(true)
            }

            Text("onboarding.subtitle")
                .font(.title3)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 4)
        .padding(.top, 2)
    }

    private var featureCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(features.indices, id: \.self) { index in
                let feature = features[index]
                featureRow(icon: feature.icon, titleKey: feature.titleKey, detailKey: feature.detailKey)

                if index < features.count - 1 {
                    Divider()
                        .padding(.leading, 48)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private func featureRow(icon: String, titleKey: LocalizedStringKey, detailKey: LocalizedStringKey) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 28, height: 28)
                .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                .accessibilityHidden(true)
                .padding(.top, 2)

            VStack(alignment: .leading, spacing: 6) {
                Text(titleKey)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.primary)

                Text(detailKey)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer()
        }
        .padding(.vertical, 14)
    }
    
    private var bottomAction: some View {
        Button {
            guard !isSyncing else { return }
            downloadError = nil
            Task {
                await startInitialDownload()
            }
        } label: {
            GeometryReader { proxy in
                let progress = min(max(fetcher.progress, 0), 1)
                let progressWidth = proxy.size.width * progress

                ZStack(alignment: .leading) {
                    Capsule(style: .continuous)
                        .fill(Color.accentColor.opacity(0.24))

                    Capsule(style: .continuous)
                        .fill(Color.accentColor)
                        .frame(width: isSyncing ? progressWidth : proxy.size.width)

                    if isSyncing {
                        if fetcher.statusMessage.isEmpty {
                            Text(LocalizedStringKey(fetcher.currentStage.rawValue))
                                .font(.headline.weight(.bold))
                                .foregroundStyle(.white)
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                                .frame(maxWidth: .infinity)
                                .padding(.horizontal, 12)
                        } else {
                            Text(fetcher.statusMessage)
                                .font(.headline.weight(.bold))
                                .foregroundStyle(.white)
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                                .frame(maxWidth: .infinity)
                                .padding(.horizontal, 12)
                        }
                    } else {
                        Text(actionTitleKey)
                            .font(.headline.weight(.bold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                            .frame(maxWidth: .infinity)
                            .padding(.horizontal, 12)
                    }
                }
            }
            .frame(height: 54)
        }
        .buttonStyle(.plain)
        .disabled(isSyncing)
        .padding(.horizontal, 16)
        .padding(.top, 12)
        .padding(.bottom, 8)
        .background(.ultraThinMaterial)
    }
    
    private func startInitialDownload() async {
        let options = MaimaiDataFetcher.SyncOptions(
            updateRemoteData: true,
            updateAliases: true,
            updateCovers: true,
            updateIcons: true,
            updateDanData: true,
            updateChartStats: true,
            updateUtageChartStats: true
        )
        
        do {
            try await fetcher.fetchSongs(modelContext: modelContext, options: options)
            didPerformInitialSync = true
            didShowOnboarding = true
            completeIfNeeded()
        } catch {
            downloadError = error.localizedDescription
        }
    }
    
    private func completeIfNeeded() {
        guard !didTriggerCompletion else { return }
        didTriggerCompletion = true
        onCompleted()
    }
}
