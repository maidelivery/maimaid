package net.krtl.maimaid.core.domain.repository

import kotlinx.coroutines.flow.Flow
import net.krtl.maimaid.core.domain.DomainError
import net.krtl.maimaid.core.domain.Result
import net.krtl.maimaid.domain.model.CommunityAliasApprovedAlias
import net.krtl.maimaid.domain.model.CommunityAliasMyCandidate
import net.krtl.maimaid.domain.model.CommunityAliasSubmitResponse
import net.krtl.maimaid.domain.model.CommunityAliasVoteResult
import net.krtl.maimaid.domain.model.CommunityAliasVotingBoardItem

interface CommunityAliasRepository {
    fun observeApprovedAliases(songIdentifier: String): Flow<List<CommunityAliasApprovedAlias>>
    suspend fun syncApprovedAliasesIfNeeded(minimumIntervalMinutes: Long = 10): Result<Int, DomainError>
    suspend fun syncApprovedAliasesIntoSongs(force: Boolean = false): Result<Int, DomainError>
    suspend fun submitAlias(songIdentifier: String, aliasText: String): CommunityAliasSubmitResponse
    suspend fun fetchVotingBoard(limit: Int = 120, offset: Int = 0): Result<List<CommunityAliasVotingBoardItem>, DomainError>
    suspend fun fetchMySongCandidates(songIdentifier: String, limit: Int = 50): Result<List<CommunityAliasMyCandidate>, DomainError>
    suspend fun fetchMyDailySubmissionCount(): Result<Int, DomainError>
    suspend fun vote(candidateId: String, support: Boolean): Result<CommunityAliasVoteResult, DomainError>
}
