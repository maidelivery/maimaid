package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommunityAliasModelsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun `decodes duplicate submission response`() {
        val response = json.decodeFromString<CommunityAliasSubmitResponse>(
            """
                {
                  "status": "rejected_duplicate",
                  "message": "duplicate",
                  "duplicateReason": "community_existing",
                  "similarAliases": ["mai mai"],
                  "quotaRemaining": 3
                }
            """.trimIndent(),
        )

        assertEquals(CommunityAliasSubmitStatus.RejectedDuplicate, response.status)
        assertEquals(CommunityAliasDuplicateReason.CommunityExisting, response.duplicateReason)
        assertEquals(listOf("mai mai"), response.similarAliases)
        assertEquals(3, response.quotaRemaining)
    }

    @Test
    fun `decodes voting board and vote result`() {
        val board = json.decodeFromString(
            CommunityAliasRowsResponse.serializer(CommunityAliasVotingBoardItem.serializer()),
            """
                {
                  "rows": [{
                    "candidateId": "candidate-1",
                    "songIdentifier": "song-1",
                    "aliasText": "alias",
                    "submitterHandle": "user#1234",
                    "voteOpenAt": null,
                    "voteCloseAt": "2026-08-20T04:00:00.000Z",
                    "supportCount": 5,
                    "opposeCount": 2,
                    "myVote": 1,
                    "createdAt": "2026-08-13T04:00:00.000Z"
                  }]
                }
            """.trimIndent(),
        ).rows.single()
        val vote = json.decodeFromString<CommunityAliasVoteResult>(
            """
                {
                  "candidateId": "candidate-1",
                  "supportCount": 4,
                  "opposeCount": 2,
                  "myVote": null
                }
            """.trimIndent(),
        )

        assertEquals("alias", board.aliasText)
        assertEquals("user#1234", board.submitterHandle)
        assertEquals(1, board.myVote)
        assertEquals(4, vote.supportCount)
        assertNull(vote.myVote)
    }

    @Test
    fun `decodes approved alias sync rows`() {
        val row = json.decodeFromString(
            CommunityAliasRowsResponse.serializer(CommunityAliasApprovedSyncRow.serializer()),
            """
                {
                  "rows": [{
                    "candidateId": "candidate-2",
                    "songIdentifier": "song-2",
                    "aliasText": "approved alias",
                    "updatedAt": "2026-08-13T05:00:00.000Z",
                    "approvedAt": "2026-08-13T05:00:00.000Z"
                  }]
                }
            """.trimIndent(),
        ).rows.single()

        assertEquals("song-2", row.songIdentifier)
        assertEquals("approved alias", row.aliasText)
    }
}
