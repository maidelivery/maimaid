package net.krtl.maimaid.core.data.remote

import kotlinx.serialization.Serializable

@Serializable
internal data class CommunityAliasSubmitRequestDto(
    val songIdentifier: String,
    val aliasText: String,
    val deviceLocalDate: String,
    val tzOffsetMinutes: Int
)

@Serializable
internal data class CommunityAliasSubmitCandidateDto(
    val id: String,
    val songIdentifier: String,
    val aliasText: String,
    val status: String,
    val createdAt: String
)

@Serializable
internal data class CommunityAliasExistingCandidateDto(
    val candidateId: String,
    val aliasText: String,
    val status: String,
    val similarity: Double = 0.0,
    val bucket: String = "",
    val supportCount: Int = 0,
    val opposeCount: Int = 0
)

@Serializable
internal data class CommunityAliasSubmitResponseDto(
    val status: String,
    val message: String,
    val duplicateReason: String? = null,
    val candidate: CommunityAliasSubmitCandidateDto? = null,
    val existingCandidates: List<CommunityAliasExistingCandidateDto> = emptyList(),
    val similarAliases: List<String> = emptyList(),
    val quotaRemaining: Int? = null
)

@Serializable
internal data class CommunityAliasVotingBoardItemDto(
    val candidateId: String,
    val songIdentifier: String,
    val aliasText: String,
    val submitterId: String,
    val voteOpenAt: String? = null,
    val voteCloseAt: String? = null,
    val supportCount: Int,
    val opposeCount: Int,
    val myVote: Int? = null,
    val createdAt: String
)

@Serializable
internal data class CommunityAliasMyCandidateDto(
    val candidateId: String,
    val songIdentifier: String,
    val aliasText: String,
    val status: String,
    val voteOpenAt: String? = null,
    val voteCloseAt: String? = null,
    val supportCount: Int,
    val opposeCount: Int,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
internal data class CommunityAliasVoteRequestDto(
    val vote: Int
)

@Serializable
internal data class CommunityAliasVoteResultDto(
    val candidateId: String,
    val supportCount: Int,
    val opposeCount: Int,
    val myVote: Int? = null
)

@Serializable
internal data class CommunityAliasApprovedSyncRowDto(
    val candidateId: String,
    val songIdentifier: String,
    val aliasText: String,
    val updatedAt: String,
    val approvedAt: String? = null
)

@Serializable
internal data class CommunityAliasRowsResponseDto<T>(
    val rows: List<T> = emptyList()
)

@Serializable
internal data class CommunityAliasDailyCountResponseDto(
    val count: Int
)
