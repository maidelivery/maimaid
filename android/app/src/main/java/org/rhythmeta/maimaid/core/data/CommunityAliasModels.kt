package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CommunityAliasSubmitStatus {
    @SerialName("created")
    Created,

    @SerialName("rejected_duplicate")
    RejectedDuplicate,

    @SerialName("quota_exceeded")
    QuotaExceeded,

    @SerialName("unauthenticated")
    Unauthenticated,

    @SerialName("invalid_request")
    InvalidRequest,

    @SerialName("error")
    Error,
}

@Serializable
enum class CommunityAliasDuplicateReason {
    @SerialName("lxns_existing")
    LxnsExisting,

    @SerialName("community_existing")
    CommunityExisting,

    @SerialName("admin_rejected_locked")
    AdminRejectedLocked,
}

@Serializable
data class CommunityAliasSubmitResponse(
    val status: CommunityAliasSubmitStatus = CommunityAliasSubmitStatus.Error,
    val message: String = "",
    val duplicateReason: CommunityAliasDuplicateReason? = null,
    val similarAliases: List<String>? = null,
    val quotaRemaining: Int? = null,
)

@Serializable
data class CommunityAliasVotingBoardItem(
    val candidateId: String,
    val songIdentifier: String,
    val aliasText: String,
    val submitterId: String = "",
    val submitterHandle: String? = null,
    val voteOpenAt: String? = null,
    val voteCloseAt: String? = null,
    val supportCount: Int = 0,
    val opposeCount: Int = 0,
    val myVote: Int? = null,
    val createdAt: String = "",
)

@Serializable
data class CommunityAliasMyCandidate(
    val candidateId: String,
    val songIdentifier: String,
    val aliasText: String,
    val status: String,
    val voteOpenAt: String? = null,
    val voteCloseAt: String? = null,
    val supportCount: Int = 0,
    val opposeCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class CommunityAliasVoteResult(
    val candidateId: String,
    val supportCount: Int,
    val opposeCount: Int,
    val myVote: Int? = null,
)

@Serializable
data class CommunityAliasApprovedSyncRow(
    val candidateId: String,
    val songIdentifier: String,
    val aliasText: String,
    val updatedAt: String = "",
    val approvedAt: String? = null,
)

@Serializable
internal data class CommunityAliasRowsResponse<T>(
    val rows: List<T> = emptyList(),
)

@Serializable
internal data class CommunityAliasDailyCountResponse(
    val count: Int = 0,
)

const val CommunityAliasDailyQuota = 5
