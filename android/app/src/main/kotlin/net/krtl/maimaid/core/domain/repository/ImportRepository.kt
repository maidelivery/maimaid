package net.krtl.maimaid.core.domain.repository

import net.krtl.maimaid.core.domain.DomainError
import net.krtl.maimaid.core.domain.ImportSummary
import net.krtl.maimaid.core.domain.Result

interface ImportRepository {
    suspend fun importDivingFish(
        profileId: String,
        username: String?,
        qq: String?
    ): Result<ImportSummary, DomainError>

    suspend fun importLxnsByOAuthCode(
        profileId: String,
        code: String,
        codeVerifier: String
    ): Result<ImportSummary, DomainError>
}

