import CryptoKit
import Foundation
@testable import maimaid

nonisolated enum ModelTestError: Error {
    case compilationFailed
}

nonisolated final class ControlledModelCompiler: @unchecked Sendable {
    private let lock = NSLock()
    private let outputRoot: URL
    private var fails = false

    init(outputRoot: URL) {
        self.outputRoot = outputRoot
    }

    func setFails(_ value: Bool) {
        lock.lock()
        fails = value
        lock.unlock()
    }

    func compile(_ packageURL: URL) throws -> URL {
        lock.lock()
        let shouldFail = fails
        lock.unlock()
        if shouldFail { throw ModelTestError.compilationFailed }

        let output = outputRoot.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true)
        try Data(packageURL.lastPathComponent.utf8).write(to: output.appending(path: "compiled.bin"))
        return output
    }
}

nonisolated enum MockModelResponse: Sendable {
    case http(status: Int, data: Data)
    case failure(URLError)
}

nonisolated final class ModelURLProtocol: URLProtocol, @unchecked Sendable {
    private static let lock = NSLock()
    nonisolated(unsafe) private static var responses: [URL: MockModelResponse] = [:]

    static func register(_ response: MockModelResponse, for url: URL) {
        lock.lock()
        responses[url] = response
        lock.unlock()
    }

    override class func canInit(with request: URLRequest) -> Bool {
        request.url?.host?.hasSuffix(".models.test") == true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        guard let url = request.url else {
            client?.urlProtocol(self, didFailWithError: URLError(.badURL))
            return
        }
        Self.lock.lock()
        let response = Self.responses[url]
        Self.lock.unlock()

        switch response {
        case let .http(status, data):
            guard let http = HTTPURLResponse(
                url: url,
                statusCode: status,
                httpVersion: "HTTP/1.1",
                headerFields: ["Content-Length": String(data.count)]
            ) else {
                client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
                return
            }
            client?.urlProtocol(self, didReceive: http, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        case let .failure(error):
            client?.urlProtocol(self, didFailWithError: error)
        case nil:
            client?.urlProtocol(self, didFailWithError: URLError(.resourceUnavailable))
        }
    }

    override func stopLoading() {}
}

nonisolated struct ModelStoreFixture {
    let rootDirectory: URL
    let baseURL: URL
    let session: URLSession
    let compiler: ControlledModelCompiler

    init() throws {
        let identifier = UUID().uuidString.lowercased()
        rootDirectory = URL.temporaryDirectory.appending(path: "maimaid-model-tests-\(identifier)", directoryHint: .isDirectory)
        guard let testBaseURL = URL(string: "https://\(identifier).models.test") else {
            throw URLError(.badURL)
        }
        baseURL = testBaseURL
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [ModelURLProtocol.self]
        session = URLSession(configuration: configuration)
        compiler = ControlledModelCompiler(outputRoot: rootDirectory.appending(path: "compiler", directoryHint: .isDirectory))
        try FileManager.default.createDirectory(at: rootDirectory, withIntermediateDirectories: true)
    }

    func makeStore() -> MLModelStore {
        MLModelStore(
            baseURL: baseURL,
            rootDirectory: rootDirectory,
            session: session,
            modelCompiler: compiler.compile
        )
    }

    func install(marker: String, extraEntries: [ModelManifestEntry] = []) throws -> [ModelManifestEntry] {
        let payloads = Dictionary(uniqueKeysWithValues: ModelAsset.allCases.map { asset in
            let packageName = asset.rawValue.replacing(".zip", with: "")
            let archive = StoredZip.make(entries: [
                ("\(packageName)/", Data()),
                ("\(packageName)/Manifest.json", Data("{\"marker\":\"\(marker)\"}".utf8))
            ])
            return (asset, archive)
        })
        let manifest = ModelAsset.allCases.map { asset in
            let data = payloads[asset] ?? Data()
            return ModelManifestEntry(filename: asset.rawValue, sha256: Self.sha256(data), size: Int64(data.count))
        }
        ModelURLProtocol.register(.http(status: 200, data: try JSONEncoder().encode(manifest + extraEntries)), for: baseURL.appending(path: "manifest.json"))
        for (asset, data) in payloads {
            ModelURLProtocol.register(.http(status: 200, data: data), for: baseURL.appending(path: asset.rawValue))
        }
        return manifest
    }

    func registerManifest(_ entries: [ModelManifestEntry], status: Int = 200) throws {
        ModelURLProtocol.register(.http(status: status, data: try JSONEncoder().encode(entries)), for: baseURL.appending(path: "manifest.json"))
    }

    func registerManifestFailure(_ error: URLError) {
        ModelURLProtocol.register(.failure(error), for: baseURL.appending(path: "manifest.json"))
    }

    func remove() {
        session.invalidateAndCancel()
        try? FileManager.default.removeItem(at: rootDirectory)
    }

    static func sha256(_ data: Data) -> String {
        SHA256.hash(data: data).map { byte in
            let value = String(byte, radix: 16)
            return value.count == 1 ? "0\(value)" : value
        }.joined()
    }
}

nonisolated enum StoredZip {
    static func make(entries: [(name: String, data: Data)]) -> Data {
        var archive = Data()
        for entry in entries {
            let name = Data(entry.name.utf8)
            archive.appendUInt32LittleEndian(0x04034b50)
            archive.appendUInt16LittleEndian(20)
            archive.appendUInt16LittleEndian(0)
            archive.appendUInt16LittleEndian(0)
            archive.appendUInt16LittleEndian(0)
            archive.appendUInt16LittleEndian(0)
            archive.appendUInt32LittleEndian(0)
            archive.appendUInt32LittleEndian(UInt32(entry.data.count))
            archive.appendUInt32LittleEndian(UInt32(entry.data.count))
            archive.appendUInt16LittleEndian(UInt16(name.count))
            archive.appendUInt16LittleEndian(0)
            archive.append(name)
            archive.append(entry.data)
        }
        return archive
    }
}

private extension Data {
    mutating func appendUInt16LittleEndian(_ value: UInt16) {
        append(UInt8(value & 0xff))
        append(UInt8((value >> 8) & 0xff))
    }

    mutating func appendUInt32LittleEndian(_ value: UInt32) {
        appendUInt16LittleEndian(UInt16(value & 0xffff))
        appendUInt16LittleEndian(UInt16((value >> 16) & 0xffff))
    }
}
