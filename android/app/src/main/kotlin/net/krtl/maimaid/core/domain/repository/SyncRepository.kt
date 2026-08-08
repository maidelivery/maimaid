package net.krtl.maimaid.core.domain.repository

import net.krtl.maimaid.core.domain.DomainError
import net.krtl.maimaid.core.domain.Result
import net.krtl.maimaid.core.domain.SyncConflictPolicy
import net.krtl.maimaid.core.domain.SyncPullResult
import net.krtl.maimaid.core.domain.SyncPushResult
import kotlinx.serialization.json.JsonObject

data class SyncPushPayload(
    val idempotencyKey: String,
    val profileUpserts: List<JsonObject> = emptyList(),
    val scoreUpserts: List<JsonObject> = emptyList(),
    val playRecordUpserts: List<JsonObject> = emptyList()
)

interface SyncRepository {
    suspend fun push(payload: SyncPushPayload): Result<SyncPushResult, DomainError>
    suspend fun pushLocalSnapshot(profileId: String? = null): Result<SyncPushResult, DomainError>
    suspend fun pull(
        sinceRevision: String,
        profileId: String? = null,
        force: Boolean = false
    ): Result<SyncPullResult, DomainError>

    suspend fun resolveConflict(policy: SyncConflictPolicy): Result<Unit, DomainError>
}
