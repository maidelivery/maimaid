import Foundation
import Testing
@testable import maimaid

struct ZipArchiveExtractorTests {
    @Test("Stored CoreML package ZIP extracts its complete top-level directory")
    func extractsStoredPackage() throws {
        let root = URL.temporaryDirectory.appending(path: "zip-extractor-\(UUID().uuidString)", directoryHint: .isDirectory)
        defer { try? FileManager.default.removeItem(at: root) }
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let archive = root.appending(path: "model.zip")
        let destination = root.appending(path: "output", directoryHint: .isDirectory)
        try StoredZip.make(entries: [
            ("score.mlpackage/", Data()),
            ("score.mlpackage/Manifest.json", Data("manifest".utf8)),
            ("score.mlpackage/Data/weights.bin", Data([1, 2, 3]))
        ]).write(to: archive)

        try ZipArchiveExtractor.extract(archiveURL: archive, to: destination)

        let manifest = try Data(contentsOf: destination.appending(path: "score.mlpackage/Manifest.json"))
        let weights = try Data(contentsOf: destination.appending(path: "score.mlpackage/Data/weights.bin"))
        #expect(manifest == Data("manifest".utf8))
        #expect(weights == Data([1, 2, 3]))
    }

    @Test(
        "ZIP paths cannot escape the extraction directory",
        arguments: ["../outside", "/absolute.mlpackage/file", "score.mlpackage/../../outside", "score.mlpackage\\..\\outside"]
    )
    func rejectsUnsafePath(path: String) throws {
        let root = URL.temporaryDirectory.appending(path: "zip-path-test-\(UUID().uuidString)", directoryHint: .isDirectory)
        defer { try? FileManager.default.removeItem(at: root) }
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let archive = root.appending(path: "model.zip")
        try StoredZip.make(entries: [(path, Data("unsafe".utf8))]).write(to: archive)

        #expect(throws: ModelStoreError.archivePathTraversal) {
            try ZipArchiveExtractor.extract(archiveURL: archive, to: root.appending(path: "output"))
        }
    }
}
