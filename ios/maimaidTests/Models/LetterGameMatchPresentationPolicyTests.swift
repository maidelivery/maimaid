import Foundation
import Testing
@testable import maimaid

struct LetterGameMatchPresentationPolicyTests {
    @Test("Fresh clients ignore historical results but accept active matches")
    func freshClientAcceptance() {
        #expect(!LetterGameMatchPresentationPolicy.shouldAccept(
            status: "finished",
            matchId: "match-1",
            trackedMatchId: nil
        ))
        #expect(LetterGameMatchPresentationPolicy.shouldAccept(
            status: "active",
            matchId: "match-2",
            trackedMatchId: nil
        ))
    }

    @Test("Tracked matches can transition to results")
    func trackedMatchAcceptance() {
        #expect(LetterGameMatchPresentationPolicy.shouldAccept(
            status: "finished",
            matchId: "match-1",
            trackedMatchId: "match-1"
        ))
        #expect(!LetterGameMatchPresentationPolicy.shouldAccept(
            status: "abandoned",
            matchId: "match-1",
            trackedMatchId: "match-2"
        ))
    }

    @Test("Gameplay players follow accepted room membership")
    func filtersDepartedPlayers() throws {
        let players = try JSONDecoder().decode(
            [LetterGameMatchPlayer].self,
            from: Data("""
            [
              {"userId":"a","score":1,"turnOrder":0,"status":"active"},
              {"userId":"b","score":2,"turnOrder":1,"status":"active"},
              {"userId":"c","score":3,"turnOrder":2,"status":"active"}
            ]
            """.utf8)
        )
        let members = try JSONDecoder().decode(
            [LetterGameRoomMember].self,
            from: Data("""
            [
              {"id":"member-b","userId":"b","status":"accepted","seatOrder":1},
              {"id":"member-c","userId":"c","status":"pending","seatOrder":2}
            ]
            """.utf8)
        )

        let visible = LetterGameMatchPresentationPolicy.visiblePlayers(players, roomMembers: members)

        #expect(visible.map(\.userId) == ["b"])
    }
}
