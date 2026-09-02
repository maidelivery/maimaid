import Foundation
import Testing
@testable import maimaid

extension Tag {
    @Tag static var networking: Self
}

@Suite(.tags(.networking))
struct MLModelStoreTests {
    @Test("Downloads known models, ignores unknown entries, and activates the complete set")
    func downloadsAndActivatesKnownModels() async throws {
        let fixture = try ModelStoreFixture()
        defer { fixture.remove() }
        let unknown = ModelManifestEntry(
            filename: "future-model.bin", sha256: String(repeating: "f", count: 64), size: 12)
        let manifest = try fixture.install(marker: "v1", extraEntries: [unknown])
        let store = fixture.makeStore()

        let availability = await store.inspect()
        #expect(availability == .downloadRequired(totalBytes: manifest.reduce(0) { $0 + $1.size }))

        try await store.downloadPending { _ in }

        let cacheIsUsable = await store.hasUsableCachedModels()
        let activeData = try Data(contentsOf: fixture.rootDirectory.appending(path: "active-manifest.json"))
        let active = try JSONDecoder().decode([ModelManifestEntry].self, from: activeData)
        #expect(cacheIsUsable)
        #expect(active == manifest)
    }

    @Test("Valid active models remain available when the manifest request is offline")
    func usesValidCacheOffline() async throws {
        let fixture = try ModelStoreFixture()
        defer { fixture.remove() }
        _ = try fixture.install(marker: "offline")
        let store = fixture.makeStore()
        _ = await store.inspect()
        try await store.downloadPending { _ in }
        fixture.registerManifestFailure(URLError(.notConnectedToInternet))

        let availability = await store.inspect()

        #expect(availability == .ready(offline: true))
    }

    @Test("A size mismatch rejects the downloaded archive")
    func rejectsSizeMismatch() async throws {
        let fixture = try ModelStoreFixture()
        defer { fixture.remove() }
        var manifest = try fixture.install(marker: "size")
        let original = manifest[0]
        manifest[0] = ModelManifestEntry(filename: original.filename, sha256: original.sha256, size: original.size + 1)
        try fixture.registerManifest(manifest)
        let store = fixture.makeStore()
        _ = await store.inspect()

        do {
            try await store.downloadPending { _ in }
            Issue.record("Expected the model size verification to fail")
        } catch let error as ModelStoreError {
            #expect(error == .sizeMismatch(original.filename))
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }

    @Test("A digest mismatch rejects the downloaded archive")
    func rejectsDigestMismatch() async throws {
        let fixture = try ModelStoreFixture()
        defer { fixture.remove() }
        var manifest = try fixture.install(marker: "digest")
        let original = manifest[0]
        manifest[0] = ModelManifestEntry(
            filename: original.filename, sha256: String(repeating: "0", count: 64), size: original.size)
        try fixture.registerManifest(manifest)
        let store = fixture.makeStore()
        _ = await store.inspect()

        do {
            try await store.downloadPending { _ in }
            Issue.record("Expected the model digest verification to fail")
        } catch let error as ModelStoreError {
            #expect(error == .digestMismatch(original.filename))
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }

    @Test("A compile failure preserves the previous active model set")
    func preservesActiveManifestWhenCompilationFails() async throws {
        let fixture = try ModelStoreFixture()
        defer { fixture.remove() }
        let firstManifest = try fixture.install(marker: "v1")
        let store = fixture.makeStore()
        _ = await store.inspect()
        try await store.downloadPending { _ in }

        let secondManifest = try fixture.install(marker: "v2")
        let updateAvailability = await store.inspect()
        #expect(updateAvailability == .updateAvailable(totalBytes: secondManifest.reduce(0) { $0 + $1.size }))
        fixture.compiler.setFails(true)

        do {
            try await store.downloadPending { _ in }
            Issue.record("Expected CoreML compilation to fail")
        } catch let error as ModelStoreError {
            if case .compileFailed = error {
                // Expected failure.
            } else {
                Issue.record("Unexpected model-store error: \(error)")
            }
        } catch {
            Issue.record("Unexpected error: \(error)")
        }

        let activeData = try Data(contentsOf: fixture.rootDirectory.appending(path: "active-manifest.json"))
        let active = try JSONDecoder().decode([ModelManifestEntry].self, from: activeData)
        let cacheIsUsable = await store.hasUsableCachedModels()
        #expect(active == firstManifest)
        #expect(cacheIsUsable)
    }

    @Test("Temporary compilation output is never treated as an active model")
    func ignoresIncompleteTemporaryOutput() async throws {
        let fixture = try ModelStoreFixture()
        defer { fixture.remove() }
        let manifest = try fixture.install(marker: "temporary")
        try JSONEncoder().encode(manifest).write(
            to: fixture.rootDirectory.appending(path: "active-manifest.json"),
            options: .atomic
        )
        for (asset, entry) in zip(ModelAsset.allCases, manifest) {
            let temporary = fixture.rootDirectory
                .appending(path: asset.cacheDirectoryName, directoryHint: .isDirectory)
                .appending(path: entry.sha256, directoryHint: .isDirectory)
                .appending(path: "compiled.mlmodelc.download", directoryHint: .isDirectory)
            try FileManager.default.createDirectory(at: temporary, withIntermediateDirectories: true)
        }
        let store = fixture.makeStore()

        let cacheIsUsable = await store.hasUsableCachedModels()
        #expect(cacheIsUsable == false)
    }

    @Test("HTTP errors are exposed as retryable model availability failures")
    func reportsHTTPError() async throws {
        let fixture = try ModelStoreFixture()
        defer { fixture.remove() }
        let manifest = try fixture.install(marker: "http")
        try fixture.registerManifest(manifest, status: 503)
        let store = fixture.makeStore()

        let availability = await store.inspect()

        guard case let .failed(message, cachedModelsAvailable) = availability else {
            Issue.record("Expected a failed availability state")
            return
        }
        #expect(message.contains("503"))
        #expect(cachedModelsAvailable == false)
    }
}
