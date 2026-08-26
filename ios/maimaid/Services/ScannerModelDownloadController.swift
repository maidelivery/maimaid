import Foundation
import Observation

enum ScannerModelDownloadState: Equatable {
    case checking(cachedModelsAvailable: Bool)
    case downloadRequired(totalBytes: Int64)
    case updateAvailable(totalBytes: Int64)
    case downloading(progress: ModelDownloadProgress, isUpdate: Bool)
    case ready(offline: Bool)
    case failed(message: String, cachedModelsAvailable: Bool)
}

@MainActor
@Observable
final class ScannerModelDownloadController {
    private(set) var state: ScannerModelDownloadState = .checking(cachedModelsAvailable: false)
    var isShowingPrompt = false

    @ObservationIgnored private var task: Task<Void, Never>?
    @ObservationIgnored private var operationID = UUID()

    var canRecognize: Bool {
        switch state {
        case .ready, .updateAvailable: true
        case let .checking(cachedModelsAvailable): cachedModelsAvailable
        case let .downloading(_, isUpdate): isUpdate
        case let .failed(_, cachedModelsAvailable): cachedModelsAvailable
        case .downloadRequired: false
        }
    }

    var blocksScanner: Bool { !canRecognize }

    func check() {
        let cachedModelsAvailable = canRecognize
        task?.cancel()
        let currentOperationID = UUID()
        operationID = currentOperationID
        state = .checking(cachedModelsAvailable: cachedModelsAvailable)
        task = Task {
            let locallyCachedModelsAvailable = await MLModelStore.shared.hasUsableCachedModels()
            let cacheIsUsable = cachedModelsAvailable || locallyCachedModelsAvailable
            guard operationID == currentOperationID, !Task.isCancelled else { return }
            state = .checking(cachedModelsAvailable: cacheIsUsable)
            let availability = await MLModelStore.shared.inspect()
            guard operationID == currentOperationID, !Task.isCancelled else { return }
            switch availability {
            case let .ready(offline):
                state = .ready(offline: offline)
            case let .downloadRequired(totalBytes):
                state = .downloadRequired(totalBytes: totalBytes)
                isShowingPrompt = true
            case let .updateAvailable(totalBytes):
                state = .updateAvailable(totalBytes: totalBytes)
                isShowingPrompt = true
            case let .failed(message, cachedModelsAvailable):
                state = .failed(message: message, cachedModelsAvailable: cachedModelsAvailable)
            }
        }
    }

    func download() {
        let previous = state
        let isUpdate = canRecognize
        task?.cancel()
        let currentOperationID = UUID()
        operationID = currentOperationID
        isShowingPrompt = false
        task = Task {
            do {
                try await MLModelStore.shared.downloadPending { progress in
                    guard self.operationID == currentOperationID else { return }
                    self.state = .downloading(progress: progress, isUpdate: isUpdate)
                }
                guard operationID == currentOperationID, !Task.isCancelled else { return }
                state = .ready(offline: false)
            } catch is CancellationError {
                guard operationID == currentOperationID else { return }
                state = previous
            } catch {
                guard operationID == currentOperationID else { return }
                state = .failed(message: error.localizedDescription, cachedModelsAvailable: isUpdate)
            }
        }
    }

    func cancelDownload() {
        task?.cancel()
        task = nil
    }

    func reportRuntimeFailure(_ error: Error) async {
        task?.cancel()
        operationID = UUID()
        state = .failed(message: error.localizedDescription, cachedModelsAvailable: false)
        isShowingPrompt = false
        await MLModelStore.shared.invalidateActiveCompiledModels()
    }
}
