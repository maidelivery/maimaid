import Foundation

nonisolated struct ModelManifestEntry: Codable, Equatable, Sendable {
    let filename: String
    let sha256: String
    let size: Int64

    func validated() throws -> ModelManifestEntry {
        guard !filename.isEmpty,
              filename != ".",
              filename != "..",
              URL(fileURLWithPath: filename).lastPathComponent == filename,
              !filename.contains("/"),
              !filename.contains("\\") else {
            throw ModelStoreError.invalidManifest("Invalid model filename")
        }
        guard sha256.count == 64,
              sha256.utf8.allSatisfy({ byte in
                  (48...57).contains(byte) || (65...70).contains(byte) || (97...102).contains(byte)
              }) else {
            throw ModelStoreError.invalidManifest("Invalid model digest")
        }
        guard size > 0 else {
            throw ModelStoreError.invalidManifest("Invalid model size")
        }
        return ModelManifestEntry(filename: filename, sha256: sha256.lowercased(), size: size)
    }
}

nonisolated enum ModelAsset: String, CaseIterable, Sendable {
    case score = "maimaid-v141n.mlpackage.zip"
    case detector = "maimaidetector-v12n.mlpackage.zip"
    case distinguish = "maimaidistinguish-v12.mlpackage.zip"

    var cacheDirectoryName: String {
        switch self {
        case .score: "score"
        case .detector: "detector"
        case .distinguish: "distinguish"
        }
    }
}

nonisolated enum ModelStoreAvailability: Equatable, Sendable {
    case ready(offline: Bool)
    case downloadRequired(totalBytes: Int64)
    case updateAvailable(totalBytes: Int64)
    case failed(message: String, cachedModelsAvailable: Bool)
}

nonisolated struct ModelDownloadProgress: Equatable, Sendable {
    let downloadedBytes: Int64
    let totalBytes: Int64

    var fraction: Double {
        guard totalBytes > 0 else { return 0 }
        return min(max(Double(downloadedBytes) / Double(totalBytes), 0), 1)
    }
}

nonisolated enum ModelStoreError: LocalizedError, Equatable, Sendable {
    case invalidManifest(String)
    case httpStatus(Int)
    case sizeMismatch(String)
    case digestMismatch(String)
    case unsupportedArchive(String)
    case archivePathTraversal
    case compileFailed(String)
    case unavailable

    var errorDescription: String? {
        switch self {
        case let .invalidManifest(message), let .unsupportedArchive(message), let .compileFailed(message): message
        case let .httpStatus(status): "Model server returned HTTP \(status)"
        case let .sizeMismatch(filename): "Model size verification failed: \(filename)"
        case let .digestMismatch(filename): "Model checksum verification failed: \(filename)"
        case .archivePathTraversal: "Model archive contains an unsafe path"
        case .unavailable: "Vision models are unavailable"
        }
    }
}
