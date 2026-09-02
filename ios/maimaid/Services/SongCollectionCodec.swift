import Foundation
import SwiftProtobuf
import zlib

enum SongCollectionCodec {
    nonisolated static let prefix = "MMD2."
    nonisolated static let webBaseURL = "https://maimaid.rhythmeta.org/collection/"
    nonisolated static let appBaseURL = "maimaid://collection/"

    private static let maxTextLength = 2_000_000
    private static let maxCompressedBytes = 1_000_000
    private static let maxRawBytes = 1_000_000
    private static let maxEntries = 10_000

    static func encode(collection: SongCollection, items: [SongCollectionItem]) throws -> String {
        let activeItems = items
            .filter { $0.collectionId == collection.id && $0.deletedAt == nil }
            .sorted { left, right in
                left.position == right.position
                    ? left.id.uuidString < right.id.uuidString : left.position < right.position
            }
        guard collection.deletedAt == nil, collection.name.count <= 200, activeItems.count <= maxEntries else {
            throw SongCollectionCodecError.invalid
        }

        var message = MMDSongCollectionShare()
        message.name = collection.name
        message.entries = activeItems.map { item in
            var entry = MMDSongCollectionEntry()
            entry.songID = item.songId
            entry.chartType = item.chartType.lowercased()
            entry.difficulty = item.difficulty.lowercased()
            return entry
        }

        let compressed = try rawDeflate(message.serializedData())
        guard compressed.count <= maxCompressedBytes else {
            throw SongCollectionCodecError.invalid
        }
        return prefix + compressed.base64EncodedString()
            .replacing("+", with: "-")
            .replacing("/", with: "_")
            .replacing("=", with: "")
    }

    static func decode(_ value: String) throws -> SongCollectionExport {
        let normalizedValue = value.filter { !$0.isWhitespace }
        guard normalizedValue.hasPrefix(prefix), normalizedValue.count <= maxTextLength else {
            throw SongCollectionCodecError.invalid
        }

        var encoded = String(normalizedValue.dropFirst(prefix.count))
            .replacing("-", with: "+")
            .replacing("_", with: "/")
        encoded += String(repeating: "=", count: (4 - encoded.count % 4) % 4)
        guard let compressed = Data(base64Encoded: encoded), compressed.count <= maxCompressedBytes,
              let raw = try? rawInflate(compressed),
              raw.count <= maxRawBytes else {
            throw SongCollectionCodecError.invalid
        }

        let message = try MMDSongCollectionShare(serializedBytes: raw)
        guard message.name.count <= 200, message.entries.count <= maxEntries else {
            throw SongCollectionCodecError.invalid
        }
        let entries = message.entries.map { entry in
            SongCollectionExportEntry(songId: entry.songID, chartType: entry.chartType, difficulty: entry.difficulty)
        }
        guard entries.allSatisfy({ entry in
            entry.songId.isEmpty == false && entry.songId.count <= 200 &&
                entry.chartType.isEmpty == false && entry.chartType.count <= 32 &&
                entry.difficulty.isEmpty == false && entry.difficulty.count <= 64
        }) else {
            throw SongCollectionCodecError.invalid
        }
        return SongCollectionExport(name: message.name, entries: entries)
    }

    private static func rawDeflate(_ input: Data) throws -> Data {
        var stream = z_stream()
        guard
            deflateInit2_(
                &stream, Z_DEFAULT_COMPRESSION, Z_DEFLATED, -15, 8, Z_DEFAULT_STRATEGY, ZLIB_VERSION,
                Int32(MemoryLayout<z_stream>.size)) == Z_OK
        else {
            throw SongCollectionCodecError.invalid
        }
        defer { deflateEnd(&stream) }

        return try input.withUnsafeBytes { inputBuffer in
            stream.next_in = UnsafeMutablePointer(mutating: inputBuffer.bindMemory(to: Bytef.self).baseAddress)
            stream.avail_in = uInt(inputBuffer.count)
            var output = Data()
            while true {
                var buffer = [UInt8](repeating: 0, count: 8192)
                let status = buffer.withUnsafeMutableBytes { outputBuffer in
                    stream.next_out = outputBuffer.bindMemory(to: Bytef.self).baseAddress
                    stream.avail_out = uInt(outputBuffer.count)
                    return deflate(&stream, Z_FINISH)
                }
                output.append(contentsOf: buffer.prefix(buffer.count - Int(stream.avail_out)))
                if status == Z_STREAM_END { return output }
                guard status == Z_OK else { throw SongCollectionCodecError.invalid }
            }
        }
    }

    private static func rawInflate(_ input: Data) throws -> Data {
        var stream = z_stream()
        guard inflateInit2_(&stream, -15, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size)) == Z_OK else {
            throw SongCollectionCodecError.invalid
        }
        defer { inflateEnd(&stream) }

        return try input.withUnsafeBytes { inputBuffer in
            stream.next_in = UnsafeMutablePointer(mutating: inputBuffer.bindMemory(to: Bytef.self).baseAddress)
            stream.avail_in = uInt(inputBuffer.count)
            var output = Data()
            while true {
                var buffer = [UInt8](repeating: 0, count: 8192)
                let status = buffer.withUnsafeMutableBytes { outputBuffer in
                    stream.next_out = outputBuffer.bindMemory(to: Bytef.self).baseAddress
                    stream.avail_out = uInt(outputBuffer.count)
                    return inflate(&stream, Z_NO_FLUSH)
                }
                output.append(contentsOf: buffer.prefix(buffer.count - Int(stream.avail_out)))
                guard output.count <= maxRawBytes else { throw SongCollectionCodecError.invalid }
                if status == Z_STREAM_END { return output }
                guard status == Z_OK, stream.avail_in > 0 || stream.avail_out == 0 else {
                    throw SongCollectionCodecError.invalid
                }
            }
        }
    }

    static func webURL(for collectionID: UUID) -> URL {
        makeURL(webBaseURL + collectionID.uuidString.lowercased())
    }

    static func webURL(for snapshot: String) -> URL {
        makeURL(webBaseURL + snapshot)
    }

    private static func makeURL(_ value: String) -> URL {
        guard let url = URL(string: value) else {
            preconditionFailure("Invalid collection URL configuration.")
        }
        return url
    }
}
