package net.krtl.maimaid.core.data.repository

import kotlinx.serialization.json.Json
import net.krtl.maimaid.core.data.remote.BackendErrorDto
import net.krtl.maimaid.core.data.remote.BackendHttpClient
import net.krtl.maimaid.core.data.remote.BackendImportDivingFishRequestDto
import net.krtl.maimaid.core.data.remote.BackendImportLxnsRequestDto
import net.krtl.maimaid.core.data.remote.BackendImportRunResponseDto
import net.krtl.maimaid.core.data.remote.BackendLxnsOauthTokenRequestDto
import net.krtl.maimaid.core.data.remote.BackendLxnsOauthTokenResponseDto
import net.krtl.maimaid.core.data.session.BackendSessionManager
import net.krtl.maimaid.core.domain.DomainError
import net.krtl.maimaid.core.domain.ImportSource
import net.krtl.maimaid.core.domain.ImportSummary
import net.krtl.maimaid.core.domain.Result
import net.krtl.maimaid.core.domain.repository.ImportRepository

class ImportRepositoryImpl(
    private val httpClient: BackendHttpClient,
    private val sessionManager: BackendSessionManager,
    private val json: Json
) : ImportRepository {
    override suspend fun importDivingFish(
        profileId: String,
        username: String?,
        qq: String?
    ): Result<ImportSummary, DomainError> {
        val normalizedUsername = username?.trim().takeUnless { it.isNullOrEmpty() }
        val normalizedQq = qq?.trim().takeUnless { it.isNullOrEmpty() }
        if (normalizedUsername == null && normalizedQq == null) {
            return Result.Err(DomainError.Validation("username or qq is required"))
        }

        val request = BackendImportDivingFishRequestDto(
            profileId = profileId,
            username = normalizedUsername,
            qq = normalizedQq
        )
        return doAuthorizedImport(
            path = "v1/imports:importDf",
            body = httpClient.encodeBody(request),
            source = ImportSource.DIVING_FISH
        )
    }

    override suspend fun importLxnsByOAuthCode(
        profileId: String,
        code: String,
        codeVerifier: String
    ): Result<ImportSummary, DomainError> {
        if (code.isBlank() || codeVerifier.length < 20) {
            return Result.Err(DomainError.Validation("Valid code and code verifier are required"))
        }

        val tokenResult = withAuthorizedCall {
            val body = httpClient.encodeBody(
                BackendLxnsOauthTokenRequestDto(
                    code = code.trim(),
                    codeVerifier = codeVerifier.trim()
                )
            )
            httpClient.execute(
                path = "v1/imports:exchangeLxnsToken",
                method = "POST",
                bodyJson = body,
                bearerToken = it
            )
        }

        val tokenPayload = when (tokenResult) {
            is Result.Ok -> {
                if (tokenResult.value.statusCode !in 200..299) {
                    return Result.Err(parseDomainError(tokenResult.value.statusCode, tokenResult.value.body))
                }
                runCatching {
                    httpClient.decodeBody<BackendLxnsOauthTokenResponseDto>(tokenResult.value.body)
                }.getOrElse { return Result.Err(DomainError.Unknown("Invalid LXNS OAuth token payload")) }
            }
            is Result.Err -> return tokenResult
        }

        val importBody = httpClient.encodeBody(
            BackendImportLxnsRequestDto(
                profileId = profileId,
                accessToken = tokenPayload.accessToken
            )
        )
        return doAuthorizedImport(
            path = "v1/imports:importLxns",
            body = importBody,
            source = ImportSource.LXNS
        )
    }

    private suspend fun doAuthorizedImport(
        path: String,
        body: String,
        source: ImportSource
    ): Result<ImportSummary, DomainError> {
        val result = withAuthorizedCall {
            httpClient.execute(
                path = path,
                method = "POST",
                bodyJson = body,
                bearerToken = it
            )
        }
        return when (result) {
            is Result.Err -> result
            is Result.Ok -> {
                if (result.value.statusCode !in 200..299) {
                    Result.Err(parseDomainError(result.value.statusCode, result.value.body))
                } else {
                    val payload = runCatching {
                        httpClient.decodeBody<BackendImportRunResponseDto>(result.value.body)
                    }.getOrElse {
                        return Result.Err(DomainError.Unknown("Invalid import payload"))
                    }
                    Result.Ok(
                        ImportSummary(
                            source = source,
                            fetchedCount = payload.fetchedCount,
                            upsertedCount = payload.upsertedCount,
                            skippedCount = payload.skippedCount
                        )
                    )
                }
            }
        }
    }

    private suspend fun withAuthorizedCall(
        block: suspend (token: String) -> BackendHttpClient.HttpResult
    ): Result<BackendHttpClient.HttpResult, DomainError> {
        val token = sessionManager.accessTokenForRequest()
            ?: return Result.Err(DomainError.Unauthorized())

        val first = runCatching { block(token) }
            .getOrElse { return Result.Err(DomainError.Network(it.message ?: "Network failure")) }
        if (first.statusCode != 401) {
            return Result.Ok(first)
        }
        val refreshed = sessionManager.refreshSessionSilently()
        if (!refreshed) {
            return Result.Err(DomainError.Unauthorized())
        }
        val retryToken = sessionManager.accessTokenForRequest()
            ?: return Result.Err(DomainError.Unauthorized())
        val second = runCatching { block(retryToken) }
            .getOrElse { return Result.Err(DomainError.Network(it.message ?: "Network failure")) }
        return Result.Ok(second)
    }

    private fun parseDomainError(statusCode: Int, body: String): DomainError {
        val payload = runCatching {
            json.decodeFromString(BackendErrorDto.serializer(), body)
        }.getOrNull()
        val message = payload?.message?.takeIf { it.isNotBlank() } ?: "HTTP $statusCode"
        return when (statusCode) {
            400 -> DomainError.Validation(message)
            401 -> DomainError.Unauthorized(message)
            409 -> DomainError.Conflict(message)
            in 500..599 -> DomainError.Server(statusCode, message)
            else -> DomainError.Unknown(message)
        }
    }
}
