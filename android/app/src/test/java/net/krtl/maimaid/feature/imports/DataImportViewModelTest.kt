package net.krtl.maimaid.feature.imports

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.krtl.maimaid.core.domain.DomainError
import net.krtl.maimaid.core.domain.ImportSource
import net.krtl.maimaid.core.domain.ImportSummary
import net.krtl.maimaid.core.domain.Result
import net.krtl.maimaid.core.domain.SyncConflictPolicy
import net.krtl.maimaid.core.domain.SyncPullResult
import net.krtl.maimaid.core.domain.SyncPushResult
import net.krtl.maimaid.core.domain.repository.ImportRepository
import net.krtl.maimaid.core.domain.repository.SyncPushPayload
import net.krtl.maimaid.core.domain.repository.SyncRepository
import net.krtl.maimaid.domain.model.GameServer
import net.krtl.maimaid.domain.model.UserProfile
import net.krtl.maimaid.domain.repository.ProfileRepository
import net.krtl.maimaid.testing.MainDispatcherRule
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataImportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun importDivingFish_usesUsernameAndQq_andTriggersPull() = runTest {
        val profileRepository = FakeProfileRepository(activeProfileId = "profile-1")
        val importRepository = FakeImportRepository()
        val syncRepository = FakeSyncRepository()
        val vm = DataImportViewModel(profileRepository, importRepository, syncRepository)
        advanceUntilIdle()

        vm.onIntent(DataImportIntent.UpdateUsername("alice"))
        vm.onIntent(DataImportIntent.UpdateQq("123456"))
        vm.onIntent(DataImportIntent.ImportDivingFish)
        advanceUntilIdle()

        assertThat(importRepository.lastDfProfileId).isEqualTo("profile-1")
        assertThat(importRepository.lastDfUsername).isEqualTo("alice")
        assertThat(importRepository.lastDfQq).isEqualTo("123456")
        assertThat(syncRepository.lastPullProfileId).isEqualTo("profile-1")
        assertThat(vm.uiState.value.statusMessage).contains("DF import done")
    }

    @Test
    fun importLxns_usesOAuthCodeAndGeneratedVerifier() = runTest {
        val profileRepository = FakeProfileRepository(activeProfileId = "profile-2")
        val importRepository = FakeImportRepository()
        val syncRepository = FakeSyncRepository()
        val vm = DataImportViewModel(profileRepository, importRepository, syncRepository)
        advanceUntilIdle()

        val generatedVerifier = vm.uiState.value.lxnsCodeVerifier
        assertThat(generatedVerifier).isNotEmpty()

        vm.onIntent(DataImportIntent.UpdateLxnsCode("oauth-code-123"))
        vm.onIntent(DataImportIntent.ImportLxns)
        advanceUntilIdle()

        assertThat(importRepository.lastLxnsProfileId).isEqualTo("profile-2")
        assertThat(importRepository.lastLxnsCode).isEqualTo("oauth-code-123")
        assertThat(importRepository.lastLxnsCodeVerifier).isEqualTo(generatedVerifier)
        assertThat(syncRepository.lastPullProfileId).isEqualTo("profile-2")
        assertThat(vm.uiState.value.statusMessage).contains("LXNS import done")
    }

    @Test
    fun openLxnsAuthPage_emitsPkceAuthorizationUrl() = runTest {
        val vm = DataImportViewModel(
            profileRepository = FakeProfileRepository(activeProfileId = null),
            importRepository = FakeImportRepository(),
            syncRepository = FakeSyncRepository()
        )
        advanceUntilIdle()

        val eventDeferred = backgroundScope.async {
            vm.events.first { it is DataImportEvent.OpenUrl } as DataImportEvent.OpenUrl
        }
        vm.onIntent(DataImportIntent.OpenLxnsAuthPage)
        advanceUntilIdle()

        val openUrl = eventDeferred.await()
        assertThat(openUrl.url).contains("https://maimai.lxns.net/oauth/authorize")
        assertThat(openUrl.url).contains("code_challenge=")
        assertThat(openUrl.url).contains("code_challenge_method=S256")
    }
}

private class FakeProfileRepository(activeProfileId: String?) : ProfileRepository {
    private val activeProfileFlow = MutableStateFlow(
        activeProfileId?.let { id ->
            UserProfile(
                id = id,
                name = "Player",
                server = GameServer.JP,
                avatarUrl = null,
                isActive = true,
                createdAt = 0L,
                playerRating = 0,
                plate = null,
                b35Count = 35,
                b15Count = 15,
                b35RecLimit = 10,
                b15RecLimit = 10
            )
        }
    )

    override fun observeProfiles(): Flow<List<UserProfile>> = flowOf(activeProfileFlow.value?.let(::listOf) ?: emptyList())

    override fun observeActiveProfile(): Flow<UserProfile?> = activeProfileFlow

    override suspend fun ensureActiveProfile(): UserProfile = requireNotNull(activeProfileFlow.value)

    override suspend fun setActiveProfile(profileId: String) = Unit

    override suspend fun saveProfile(profile: UserProfile) = Unit

    override suspend fun deleteProfile(profileId: String) = Unit
}

private class FakeImportRepository : ImportRepository {
    var lastDfProfileId: String? = null
    var lastDfUsername: String? = null
    var lastDfQq: String? = null
    var lastLxnsProfileId: String? = null
    var lastLxnsCode: String? = null
    var lastLxnsCodeVerifier: String? = null

    override suspend fun importDivingFish(
        profileId: String,
        username: String?,
        qq: String?
    ): Result<ImportSummary, DomainError> {
        lastDfProfileId = profileId
        lastDfUsername = username
        lastDfQq = qq
        return Result.Ok(
            ImportSummary(
                source = ImportSource.DIVING_FISH,
                fetchedCount = 10,
                upsertedCount = 8,
                skippedCount = 2
            )
        )
    }

    override suspend fun importLxnsByOAuthCode(
        profileId: String,
        code: String,
        codeVerifier: String
    ): Result<ImportSummary, DomainError> {
        lastLxnsProfileId = profileId
        lastLxnsCode = code
        lastLxnsCodeVerifier = codeVerifier
        return Result.Ok(
            ImportSummary(
                source = ImportSource.LXNS,
                fetchedCount = 20,
                upsertedCount = 12,
                skippedCount = 8
            )
        )
    }
}

private class FakeSyncRepository : SyncRepository {
    var lastPullProfileId: String? = null

    override suspend fun push(payload: SyncPushPayload): Result<SyncPushResult, DomainError> {
        return Result.Ok(SyncPushResult(latestRevision = "1"))
    }

    override suspend fun pushLocalSnapshot(profileId: String?): Result<SyncPushResult, DomainError> {
        return Result.Ok(SyncPushResult(latestRevision = "1"))
    }

    override suspend fun pull(
        sinceRevision: String,
        profileId: String?,
        force: Boolean
    ): Result<SyncPullResult, DomainError> {
        lastPullProfileId = profileId
        return Result.Ok(
            SyncPullResult(
                latestRevision = "1",
                profileCount = 1,
                scoreCount = 5,
                recordCount = 3
            )
        )
    }

    override suspend fun resolveConflict(policy: SyncConflictPolicy): Result<Unit, DomainError> {
        return Result.Ok(Unit)
    }
}
