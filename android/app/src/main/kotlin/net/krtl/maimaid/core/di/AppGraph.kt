package net.krtl.maimaid.core.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import net.krtl.maimaid.core.data.remote.BackendHttpClient
import net.krtl.maimaid.core.data.repository.AuthRepositoryImpl
import net.krtl.maimaid.core.data.repository.CommunityAliasRepositoryImpl
import net.krtl.maimaid.core.data.repository.ImportRepositoryImpl
import net.krtl.maimaid.core.data.repository.SyncRepositoryImpl
import net.krtl.maimaid.core.data.session.BackendSessionManager
import net.krtl.maimaid.core.data.session.SecureSessionStore
import net.krtl.maimaid.core.domain.repository.AuthRepository
import net.krtl.maimaid.core.domain.repository.CommunityAliasRepository
import net.krtl.maimaid.core.domain.repository.ImportRepository
import net.krtl.maimaid.core.domain.repository.SyncRepository
import net.krtl.maimaid.data.local.AppDatabase
import net.krtl.maimaid.data.remote.api.StaticDataApi
import net.krtl.maimaid.data.remote.dto.VersionCheckResponse
import net.krtl.maimaid.data.repository.AppPreferences
import net.krtl.maimaid.data.repository.ProfileRepositoryImpl
import net.krtl.maimaid.data.repository.RecommendationRepositoryImpl
import net.krtl.maimaid.data.repository.ScoreRepositoryImpl
import net.krtl.maimaid.data.repository.StaticDataRepositoryImpl
import net.krtl.maimaid.domain.usecase.CalculateB50UseCase
import net.krtl.maimaid.domain.usecase.GetHomeSummaryUseCase
import net.krtl.maimaid.domain.usecase.GetPlateProgressUseCase
import net.krtl.maimaid.domain.usecase.GetRecommendationsUseCase
import net.krtl.maimaid.domain.usecase.SyncStaticDataUseCase
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.create
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class VersionAccessDecision(
    val revoked: Boolean,
    val versionDescription: String? = null
)

class AppGraph(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val stringFormat: StringFormat = json
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()
    private val contentType = "application/json".toMediaType()

    val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "maimaid.db"
    ).fallbackToDestructiveMigration(true)
        .build()

    private val staticRetrofit = Retrofit.Builder()
        .baseUrl("https://dp4p6x0xfi5o9.cloudfront.net/")
        .client(okHttpClient)
        .addConverterFactory(stringFormat.asConverterFactory(contentType))
        .build()

    private val dao = database.maimaiDao()
    val preferencesRepository = AppPreferences(context)
    private val staticDataApi: StaticDataApi = staticRetrofit.create()

    val profileRepository = ProfileRepositoryImpl(dao)
    val scoreRepository = ScoreRepositoryImpl(dao)
    val staticDataRepository = StaticDataRepositoryImpl(
        context = appContext,
        dao = dao,
        preferencesRepository = preferencesRepository,
        staticDataApi = staticDataApi,
        json = json,
        okHttpClient = okHttpClient
    )
    val recommendationRepository = RecommendationRepositoryImpl(
        dao = dao,
        preferencesRepository = preferencesRepository,
        profileRepository = profileRepository,
        scoreRepository = scoreRepository
    )

    val syncStaticDataUseCase = SyncStaticDataUseCase(staticDataRepository)
    val calculateB50UseCase = CalculateB50UseCase(
        staticDataRepository = staticDataRepository,
        profileRepository = profileRepository,
        scoreRepository = scoreRepository,
        recommendationRepository = recommendationRepository,
        preferencesRepository = preferencesRepository
    )
    val getRecommendationsUseCase = GetRecommendationsUseCase(
        staticDataRepository = staticDataRepository,
        profileRepository = profileRepository,
        scoreRepository = scoreRepository,
        recommendationRepository = recommendationRepository,
        preferencesRepository = preferencesRepository
    )
    val getPlateProgressUseCase = GetPlateProgressUseCase(
        staticDataRepository = staticDataRepository,
        profileRepository = profileRepository,
        scoreRepository = scoreRepository,
        recommendationRepository = recommendationRepository
    )
    val getHomeSummaryUseCase = GetHomeSummaryUseCase(recommendationRepository)

    private val backendHttpClient = BackendHttpClient(okHttpClient, json)
    private val secureSessionStore = SecureSessionStore(appContext, json)
    val backendSessionManager = BackendSessionManager(
        httpClient = backendHttpClient,
        secureSessionStore = secureSessionStore,
        json = json
    )

    val authRepository: AuthRepository = AuthRepositoryImpl(backendSessionManager)
    val syncRepository: SyncRepository = SyncRepositoryImpl(
        httpClient = backendHttpClient,
        sessionManager = backendSessionManager,
        dao = dao,
        json = json
    )
    val importRepository: ImportRepository = ImportRepositoryImpl(
        httpClient = backendHttpClient,
        sessionManager = backendSessionManager,
        json = json
    )
    val communityAliasRepository: CommunityAliasRepository = CommunityAliasRepositoryImpl(
        context = appContext,
        httpClient = backendHttpClient,
        sessionManager = backendSessionManager,
        dao = dao,
        json = json
    )
//    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(
//        preferencesRepository = preferencesRepository,
//        staticDataRepository = staticDataRepository
//    )

    suspend fun checkVersionAccess(type: String, versionCode: Int): VersionAccessDecision {
        if (type == "debug") {
            return VersionAccessDecision(revoked = false)
        }
        val response = fetchVersionCheck(type, versionCode).getOrThrow()
        return VersionAccessDecision(
            revoked = response.success && response.data?.status == "revoked",
            versionDescription = response.data?.description
        )
    }

    private suspend fun fetchVersionCheck(
        type: String,
        versionCode: Int
    ): Result<VersionCheckResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection =
                    URL("https://a.krtl.net/maimaid/version/$type/$versionCode").openConnection() as HttpURLConnection

                with(connection) {
                    requestMethod = "GET"
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    useCaches = false

                    try {
                        connect()
                        if (responseCode != HttpURLConnection.HTTP_OK) {
                            val errorBody =
                                errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                                    ?: ""
                            throw IOException("HTTP ${responseCode}: $errorBody")
                        }

                        val response =
                            getInputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }

                        Log.i("versionCheck", "Response Body: $response")
                        json.decodeFromString<VersionCheckResponse>(response)

                    } catch (e: Exception) {
                        e.printStackTrace()
                        throw e
                    } finally {
                        disconnect()
                    }
                }
            }
        }
}
