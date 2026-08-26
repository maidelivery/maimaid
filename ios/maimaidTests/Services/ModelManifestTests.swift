import Testing
@testable import maimaid

struct ModelManifestTests {
    @Test("Valid manifest entries normalize uppercase digests")
    func validatesEntry() throws {
        let entry = ModelManifestEntry(
            filename: ModelAsset.score.rawValue,
            sha256: String(repeating: "A", count: 64),
            size: 42
        )

        let validated = try entry.validated()

        #expect(validated.sha256 == String(repeating: "a", count: 64))
    }

    @Test(
        "Unsafe model filenames are rejected",
        arguments: ["../model.zip", "/model.zip", "folder/model.zip", "folder\\model.zip", ".", ".."]
    )
    func rejectsUnsafeFilename(filename: String) {
        let entry = ModelManifestEntry(
            filename: filename,
            sha256: String(repeating: "a", count: 64),
            size: 1
        )

        #expect(throws: ModelStoreError.invalidManifest("Invalid model filename")) {
            try entry.validated()
        }
    }

    @Test("Non-ASCII and malformed SHA-256 values are rejected", arguments: ["", String(repeating: "g", count: 64), String(repeating: "０", count: 64)])
    func rejectsInvalidDigest(digest: String) {
        let entry = ModelManifestEntry(filename: ModelAsset.score.rawValue, sha256: digest, size: 1)

        #expect(throws: ModelStoreError.invalidManifest("Invalid model digest")) {
            try entry.validated()
        }
    }
}
