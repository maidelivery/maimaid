import Foundation
import CryptoKit
@preconcurrency import CoreML
@preconcurrency import Vision

typealias ModelCompiler = @Sendable (URL) throws -> URL

actor MLModelStore {
    @MainActor static let shared = MLModelStore(baseURL: BackendConfig.modelAssetsBaseURL)

    private let baseURL: URL?
    private let fileManager = FileManager.default
    private let session: URLSession
    private let modelCompiler: ModelCompiler
    private let rootDirectory: URL
    private let activeManifestURL: URL
    private var pendingManifest: [ModelManifestEntry]?
    private var loadedModels: [ModelAsset: VNCoreMLModel] = [:]

    init(
        baseURL: URL?,
        rootDirectory: URL? = nil,
        session: URLSession = .shared,
        modelCompiler: @escaping ModelCompiler = { try MLModel.compileModel(at: $0) }
    ) {
        self.baseURL = baseURL
        self.session = session
        self.modelCompiler = modelCompiler
        let root = rootDirectory ?? URL.applicationSupportDirectory
            .appending(path: "Maimaid", directoryHint: .isDirectory)
            .appending(path: "Models", directoryHint: .isDirectory)
        self.rootDirectory = root
        self.activeManifestURL = root.appending(path: "active-manifest.json")
        try? fileManager.createDirectory(at: root, withIntermediateDirectories: true)
    }

    func hasUsableCachedModels() -> Bool {
        loadActiveManifest().map(isComplete) == true
    }

    func invalidateActiveCompiledModels() {
        if let active = loadActiveManifest() {
            for entry in active {
                try? fileManager.removeItem(at: compiledURL(for: entry))
            }
        }
        try? fileManager.removeItem(at: activeManifestURL)
        loadedModels.removeAll()
        pendingManifest = nil
    }

    func inspect() async -> ModelStoreAvailability {
        let active = loadActiveManifest()
        let activeReady = active.map(isComplete) == true
        do {
            let remote = try await fetchManifest()
            if isComplete(remote) {
                try activate(remote)
                pendingManifest = nil
                return .ready(offline: false)
            }
            pendingManifest = remote
            let missing = missingBytes(remote)
            if activeReady {
                return .updateAvailable(totalBytes: missing)
            }
            return .downloadRequired(totalBytes: missing)
        } catch is CancellationError {
            return .failed(message: ModelStoreError.unavailable.localizedDescription, cachedModelsAvailable: activeReady)
        } catch {
            pendingManifest = nil
            if activeReady {
                return .ready(offline: true)
            }
            return .failed(message: error.localizedDescription, cachedModelsAvailable: false)
        }
    }

    func downloadPending(
        onProgress: @escaping @MainActor @Sendable (ModelDownloadProgress) -> Void
    ) async throws {
        let manifest: [ModelManifestEntry]
        if let pendingManifest {
            manifest = pendingManifest
        } else {
            manifest = try await fetchManifest()
        }
        pendingManifest = manifest
        let total = missingBytes(manifest)
        var downloaded: Int64 = 0
        await onProgress(ModelDownloadProgress(downloadedBytes: 0, totalBytes: total))

        do {
            for entry in manifest {
                try Task.checkCancellation()
                if isCompiled(entry) { continue }
                let downloadedArchive: URL
                if await isValidArchive(entry) {
                    downloadedArchive = archiveURL(for: entry)
                } else {
                    let downloadedBeforeEntry = downloaded
                    downloadedArchive = try await download(entry: entry) { bytesWritten in
                        Task { @MainActor in
                            onProgress(
                                ModelDownloadProgress(
                                    downloadedBytes: min(downloadedBeforeEntry + bytesWritten, total),
                                    totalBytes: total
                                )
                            )
                        }
                    }
                }
                downloaded += entry.size
                await onProgress(ModelDownloadProgress(downloadedBytes: downloaded, totalBytes: total))
                try await compile(entry: entry, archiveURL: downloadedArchive)
            }
            guard isComplete(manifest) else { throw ModelStoreError.unavailable }
            try activate(manifest)
            pendingManifest = nil
            await onProgress(ModelDownloadProgress(downloadedBytes: total, totalBytes: total))
        } catch is CancellationError {
            throw CancellationError()
        }
    }

    func model(for asset: ModelAsset) throws -> VNCoreMLModel {
        if let loaded = loadedModels[asset] { return loaded }
        guard let manifest = loadActiveManifest(),
              let entry = manifest.first(where: { $0.filename == asset.rawValue }) else {
            throw ModelStoreError.unavailable
        }
        let compiled = compiledURL(for: entry)
        guard fileManager.fileExists(atPath: compiled.path) else {
            throw ModelStoreError.unavailable
        }
        let configuration = MLModelConfiguration()
        let model = try MLModel(contentsOf: compiled, configuration: configuration)
        let visionModel = try VNCoreMLModel(for: model)
        loadedModels[asset] = visionModel
        return visionModel
    }

    private func fetchManifest() async throws -> [ModelManifestEntry] {
        guard let url = baseURL?.appending(path: "manifest.json") else { throw ModelStoreError.unavailable }
        var request = URLRequest(url: url)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(AppKeys.userAgent, forHTTPHeaderField: "User-Agent")
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw ModelStoreError.httpStatus((response as? HTTPURLResponse)?.statusCode ?? 0)
        }
        return try validatedRequiredEntries(JSONDecoder().decode([ModelManifestEntry].self, from: data))
    }

    private func download(
        entry: ModelManifestEntry,
        onBytesWritten: @escaping @Sendable (Int64) -> Void
    ) async throws -> URL {
        guard let baseURL else { throw ModelStoreError.unavailable }
        let url = baseURL.appending(path: entry.filename)
        var request = URLRequest(url: url)
        request.setValue("application/zip, application/octet-stream", forHTTPHeaderField: "Accept")
        request.setValue(AppKeys.userAgent, forHTTPHeaderField: "User-Agent")
        let delegate = ModelDownloadDelegate(onBytesWritten: onBytesWritten)
        let (downloadedURL, response) = try await session.download(for: request, delegate: delegate)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw ModelStoreError.httpStatus((response as? HTTPURLResponse)?.statusCode ?? 0)
        }

        let directory = cacheDirectory(for: entry)
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        let temporary = directory.appending(path: "archive.download")
        let destination = archiveURL(for: entry)
        try? fileManager.removeItem(at: temporary)
        do {
            try fileManager.moveItem(at: downloadedURL, to: temporary)
            guard fileSize(at: temporary) == entry.size else {
                throw ModelStoreError.sizeMismatch(entry.filename)
            }
            let digest = try await Task.detached(priority: .utility) {
                try Self.sha256(fileAt: temporary)
            }.value
            guard digest == entry.sha256.lowercased() else {
                throw ModelStoreError.digestMismatch(entry.filename)
            }
            try? fileManager.removeItem(at: destination)
            try fileManager.moveItem(at: temporary, to: destination)
            return destination
        } catch {
            try? fileManager.removeItem(at: temporary)
            throw error
        }
    }

    private func compile(entry: ModelManifestEntry, archiveURL: URL) async throws {
        let directory = cacheDirectory(for: entry)
        let packageDirectory = directory.appending(path: "package", directoryHint: .isDirectory)
        let compiledDirectory = directory.appending(path: "compiled.mlmodelc", directoryHint: .isDirectory)
        let compiler = modelCompiler
        try await Task.detached(priority: .userInitiated) {
            let fileManager = FileManager.default
            try? fileManager.removeItem(at: packageDirectory)
            try? fileManager.removeItem(at: compiledDirectory)
            try fileManager.createDirectory(at: packageDirectory, withIntermediateDirectories: true)
            do {
                try ZipArchiveExtractor.extract(archiveURL: archiveURL, to: packageDirectory)
                let roots = try fileManager.contentsOfDirectory(
                    at: packageDirectory,
                    includingPropertiesForKeys: [.isDirectoryKey]
                )
                guard let packageRoot = roots.first(where: { $0.pathExtension == "mlpackage" }) else {
                    throw ModelStoreError.unsupportedArchive("CoreML package directory is missing")
                }
                let compiled = try compiler(packageRoot)
                try fileManager.moveItem(at: compiled, to: compiledDirectory)
            } catch let error as ModelStoreError {
                try? fileManager.removeItem(at: packageDirectory)
                try? fileManager.removeItem(at: compiledDirectory)
                throw error
            } catch {
                try? fileManager.removeItem(at: packageDirectory)
                try? fileManager.removeItem(at: compiledDirectory)
                throw ModelStoreError.compileFailed(error.localizedDescription)
            }
        }.value
    }

    private func loadActiveManifest() -> [ModelManifestEntry]? {
        guard let data = try? Data(contentsOf: activeManifestURL),
              let decoded = try? JSONDecoder().decode([ModelManifestEntry].self, from: data) else {
            return nil
        }
        return try? validatedRequiredEntries(decoded)
    }

    private func writeActiveManifest(_ manifest: [ModelManifestEntry]) throws {
        try JSONEncoder().encode(manifest).write(to: activeManifestURL, options: .atomic)
    }

    private func activate(_ manifest: [ModelManifestEntry]) throws {
        try writeActiveManifest(manifest)
        loadedModels.removeAll()
    }

    private func validatedRequiredEntries(_ entries: [ModelManifestEntry]) throws -> [ModelManifestEntry] {
        var byFilename: [String: ModelManifestEntry] = [:]
        for entry in entries {
            let validated = try entry.validated()
            guard byFilename.updateValue(validated, forKey: validated.filename) == nil else {
                throw ModelStoreError.invalidManifest("Manifest contains duplicate model entries")
            }
        }
        return try ModelAsset.allCases.map { asset in
            guard let entry = byFilename[asset.rawValue] else {
                throw ModelStoreError.invalidManifest("Manifest is missing \(asset.rawValue)")
            }
            return entry
        }
    }

    private func isComplete(_ manifest: [ModelManifestEntry]) -> Bool {
        manifest.allSatisfy { entry in
            isCompiled(entry)
        }
    }

    private func missingBytes(_ manifest: [ModelManifestEntry]) -> Int64 {
        manifest.reduce(into: Int64(0)) { result, entry in
            if !isCompiled(entry) { result += entry.size }
        }
    }

    private func isCompiled(_ entry: ModelManifestEntry) -> Bool {
        var isDirectory: ObjCBool = false
        return fileManager.fileExists(atPath: compiledURL(for: entry).path, isDirectory: &isDirectory)
            && isDirectory.boolValue
    }

    private func isValidArchive(_ entry: ModelManifestEntry) async -> Bool {
        let archive = archiveURL(for: entry)
        guard fileSize(at: archive) == entry.size else { return false }
        let digest = try? await Task.detached(priority: .utility) {
            try Self.sha256(fileAt: archive)
        }.value
        return digest == entry.sha256
    }

    private func fileSize(at url: URL) -> Int64? {
        guard let values = try? url.resourceValues(forKeys: [.fileSizeKey]),
              let size = values.fileSize else {
            return nil
        }
        return Int64(size)
    }

    private func archiveURL(for entry: ModelManifestEntry) -> URL {
        cacheDirectory(for: entry).appending(path: "package.zip")
    }

    private func compiledURL(for entry: ModelManifestEntry) -> URL {
        cacheDirectory(for: entry).appending(path: "compiled.mlmodelc", directoryHint: .isDirectory)
    }

    private func cacheDirectory(for entry: ModelManifestEntry) -> URL {
        let modelName = ModelAsset.allCases.first(where: { $0.rawValue == entry.filename })?.cacheDirectoryName
            ?? "unknown"
        return rootDirectory
            .appending(path: modelName, directoryHint: .isDirectory)
            .appending(path: entry.sha256, directoryHint: .isDirectory)
    }

    nonisolated private static func sha256(fileAt url: URL) throws -> String {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        var hasher = SHA256()
        while let data = try handle.read(upToCount: 64 * 1_024), !data.isEmpty {
            hasher.update(data: data)
        }
        return hasher.finalize().map { byte in
            let value = String(byte, radix: 16)
            return value.count == 1 ? "0\(value)" : value
        }.joined()
    }
}

nonisolated private final class ModelDownloadDelegate: NSObject, URLSessionDownloadDelegate, @unchecked Sendable {
    private let onBytesWritten: @Sendable (Int64) -> Void

    init(onBytesWritten: @escaping @Sendable (Int64) -> Void) {
        self.onBytesWritten = onBytesWritten
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didWriteData bytesWritten: Int64,
        totalBytesWritten: Int64,
        totalBytesExpectedToWrite: Int64
    ) {
        onBytesWritten(totalBytesWritten)
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didFinishDownloadingTo location: URL
    ) {}
}

nonisolated enum ZipArchiveExtractor {
    static func extract(archiveURL: URL, to destination: URL) throws {
        let data = try Data(contentsOf: archiveURL)
        var offset = 0
        var packageRoot: String?
        while offset + 4 <= data.count {
            let signature = data.uint32(at: offset)
            if signature == 0x02014b50 || signature == 0x06054b50 { break }
            guard signature == 0x04034b50, offset + 30 <= data.count else {
                throw ModelStoreError.unsupportedArchive("Invalid ZIP local header")
            }
            let flags = data.uint16(at: offset + 6)
            let method = data.uint16(at: offset + 8)
            let compressedSize = Int(data.uint32(at: offset + 18))
            let nameLength = Int(data.uint16(at: offset + 26))
            let extraLength = Int(data.uint16(at: offset + 28))
            guard flags & 0x08 == 0, method == 0 else {
                throw ModelStoreError.unsupportedArchive("ZIP entry uses unsupported compression")
            }
            let nameStart = offset + 30
            let payloadStart = nameStart + nameLength + extraLength
            let payloadEnd = payloadStart + compressedSize
            guard payloadEnd <= data.count,
                  let name = String(data: data[nameStart..<(nameStart + nameLength)], encoding: .utf8) else {
                throw ModelStoreError.unsupportedArchive("Invalid ZIP entry")
            }
            let components = name.split(separator: "/", omittingEmptySubsequences: true).map(String.init)
            guard !name.hasPrefix("/"),
                  !name.contains("\\"),
                  !components.contains(".."),
                  !components.contains(".") else {
                throw ModelStoreError.archivePathTraversal
            }
            guard let root = components.first, root.hasSuffix(".mlpackage") else {
                throw ModelStoreError.unsupportedArchive("ZIP package root is invalid")
            }
            if let packageRoot, packageRoot != root { throw ModelStoreError.unsupportedArchive("ZIP contains multiple roots") }
            packageRoot = root
            let outputURL = destination.appending(path: components.joined(separator: "/"), directoryHint: name.hasSuffix("/") ? .isDirectory : .notDirectory)
            if name.hasSuffix("/") {
                try FileManager.default.createDirectory(at: outputURL, withIntermediateDirectories: true)
            } else {
                try FileManager.default.createDirectory(at: outputURL.deletingLastPathComponent(), withIntermediateDirectories: true)
                try Data(data[payloadStart..<payloadEnd]).write(to: outputURL, options: .atomic)
            }
            offset = payloadEnd
        }
        guard packageRoot != nil else { throw ModelStoreError.unsupportedArchive("ZIP has no CoreML package") }
    }
}

private extension Data {
    nonisolated func uint16(at offset: Int) -> UInt16 {
        UInt16(self[offset]) | UInt16(self[offset + 1]) << 8
    }

    nonisolated func uint32(at offset: Int) -> UInt32 {
        UInt32(uint16(at: offset)) | UInt32(uint16(at: offset + 2)) << 16
    }
}
