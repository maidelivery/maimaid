import Foundation
import Testing
@testable import maimaid

struct LetterGameProtocolTests {
    @Test("Android-compatible match snapshots decode all gameplay metadata")
    func decodesCompleteSnapshot() throws {
        let json = """
        {
          "matchId": "83dc664f-8f50-444f-abd4-b8acaefef777",
          "status": "active",
          "revision": 4,
          "turnUserId": "user-1",
          "turnDeadline": "2026-09-02T12:00:00.123Z",
          "players": [{
            "userId": "user-1",
            "score": 12,
            "turnOrder": 0,
            "status": "active",
            "displayName": "Mia"
          }],
          "songs": [{
            "slotId": "slot-1",
            "title": "W_R_D",
            "remainingCharacterCount": 2,
            "status": "active",
            "facts": [{"type": "version", "visibility": "public", "value": "FESTiVAL"}],
            "imageName": "12345.png",
            "artist": "Artist",
            "version": "FESTiVAL",
            "chartTypes": ["standard", "dx"],
            "hasRemaster": true,
            "maxConstant": "14.7"
          }],
          "logs": [{
            "id": "log-1",
            "message": "",
            "actionType": "open_character",
            "character": "O",
            "newlyRevealedCount": 1,
            "points": 1
          }]
        }
        """

        let snapshot = try JSONDecoder().decode(LetterGameMatchSnapshot.self, from: Data(json.utf8))
        let player = try #require(snapshot.players.first)
        let song = try #require(snapshot.songs.first)

        #expect(player.scoringEligible)
        #expect(player.displayName == "Mia")
        #expect(song.imageName == "12345.png")
        #expect(song.chartTypes == ["standard", "dx"])
        #expect(song.hasRemaster)
        #expect(snapshot.logs.first?.character == "O")
    }

    @Test("Room settings encode structured filter values")
    func encodesSelectionConfiguration() throws {
        let request = LetterGameCreateRequest(
            visibility: "private",
            songCount: 6,
            selectionConfig: [
                "excludeDeleted": .boolean(true),
                "categories": .array([.string("POPS＆アニメ")]),
                "minVersion": .null
            ]
        )

        let data = try JSONEncoder().encode(request)
        let object = try JSONDecoder().decode([String: LetterGameJSONValue].self, from: data)
        let selectionConfig = try #require(object["selectionConfig"])
        guard case .object(let config) = selectionConfig else {
            Issue.record("Expected selectionConfig to remain a JSON object.")
            return
        }

        #expect(config["excludeDeleted"] == .boolean(true))
        #expect(config["categories"] == .array([.string("POPS＆アニメ")]))
        #expect(config["minVersion"] == .null)
    }
}
