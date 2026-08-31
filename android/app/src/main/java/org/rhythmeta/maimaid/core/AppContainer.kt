package org.rhythmeta.maimaid.core

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.serialization.json.Json
import org.rhythmeta.maimaid.BuildConfig
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.data.AppPreferencesRepository
import org.rhythmeta.maimaid.core.data.BackendSessionManager
import org.rhythmeta.maimaid.core.data.BackendImportService
import org.rhythmeta.maimaid.core.data.BackendSyncCoordinator
import org.rhythmeta.maimaid.core.data.BackendSyncStateStore
import org.rhythmeta.maimaid.core.data.BackendTokenStore
import org.rhythmeta.maimaid.core.data.Best50Repository
import org.rhythmeta.maimaid.core.data.CatalogRepository
import org.rhythmeta.maimaid.core.data.CatalogSyncStateStore
import org.rhythmeta.maimaid.core.data.ChartFitStore
import org.rhythmeta.maimaid.core.data.CoverImageStore
import org.rhythmeta.maimaid.core.data.ConstantTableRepository
import org.rhythmeta.maimaid.core.data.CommunityAliasService
import org.rhythmeta.maimaid.core.data.CollectionSharingService
import org.rhythmeta.maimaid.core.data.LetterGameRepository
import org.rhythmeta.maimaid.core.data.DanRepository
import org.rhythmeta.maimaid.core.data.DanStore
import org.rhythmeta.maimaid.core.data.PlateProgressRepository
import org.rhythmeta.maimaid.core.data.OtogameImportService
import org.rhythmeta.maimaid.core.data.PresetAvatarRepository
import org.rhythmeta.maimaid.core.data.PresetAvatarImageStore
import org.rhythmeta.maimaid.core.data.ProfileAvatarStore
import org.rhythmeta.maimaid.core.data.ProfileCredentialStore
import org.rhythmeta.maimaid.core.data.ProfileRepository
import org.rhythmeta.maimaid.core.data.RecommendationRepository
import org.rhythmeta.maimaid.core.data.ScoreRepository
import org.rhythmeta.maimaid.core.data.ScoreQueryRepository
import org.rhythmeta.maimaid.core.data.ScoreSyncService
import org.rhythmeta.maimaid.core.data.SongCollectionRepository
import org.rhythmeta.maimaid.core.database.MaimaidDatabase
import org.rhythmeta.maimaid.core.ml.OnnxSessionFactory
import org.rhythmeta.maimaid.core.ml.RemoteModelStore
import org.rhythmeta.maimaid.core.network.BackendApiClient
import org.rhythmeta.maimaid.core.network.StaticBundleClient
import org.rhythmeta.maimaid.widget.WidgetUpdateCoordinator

class AppContainer(context: Context) {
    val applicationContext: Context = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    val database: MaimaidDatabase = Room.databaseBuilder(
        applicationContext,
        MaimaidDatabase::class.java,
        "maimaid.db",
    ).addMigrations(
        MaimaidDatabase.Migration1To2,
        MaimaidDatabase.Migration2To3,
        MaimaidDatabase.Migration3To4,
        MaimaidDatabase.Migration4To5,
        MaimaidDatabase.Migration5To6,
        MaimaidDatabase.Migration6To7,
    )
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .build()

    val widgetUpdateCoordinator = WidgetUpdateCoordinator(applicationContext)

    val appPreferencesRepository = AppPreferencesRepository(applicationContext)
    val remoteModelStore = RemoteModelStore(
        context = applicationContext,
        baseUrl = BuildConfig.MODEL_ASSETS_URL,
        json = json,
    )
    val onnxSessionFactory = OnnxSessionFactory(remoteModelStore)
    val coverImageStore = CoverImageStore(applicationContext)
    val presetAvatarImageStore = PresetAvatarImageStore(applicationContext)
    val profileAvatarStore = ProfileAvatarStore(applicationContext)
    val profileCredentialStore = ProfileCredentialStore(applicationContext)
    private val chartFitStore = ChartFitStore(applicationContext, json)
    private val backendApiClient = BackendApiClient(BuildConfig.BACKEND_URL, json)
    val backendSessionManager = BackendSessionManager(
        authBaseUrl = BuildConfig.BACKEND_AUTH_URL,
        apiClient = backendApiClient,
        tokenStore = BackendTokenStore(applicationContext, json),
    )
    val backendImportService = BackendImportService(
        sessionManager = backendSessionManager,
        json = json,
    )
    val letterGameRepository = LetterGameRepository(
        apiClient = backendApiClient,
        sessionManager = backendSessionManager,
        json = json,
    )
    val communityAliasService = CommunityAliasService(
        apiClient = backendApiClient,
        sessionManager = backendSessionManager,
        catalogDao = database.catalogDao(),
        json = json,
    )
    val backendSyncStateStore = BackendSyncStateStore(applicationContext, json)

    val profileRepository = ProfileRepository(
        profileDao = database.profileDao(),
        defaultProfileName = applicationContext.getString(R.string.default_profile_name),
        onProfileChanged = widgetUpdateCoordinator::requestUpdate,
    )

    val presetAvatarRepository = PresetAvatarRepository(
        apiClient = backendApiClient,
        json = json,
        database = database,
        avatarDao = database.presetAvatarDao(),
        imageStore = presetAvatarImageStore,
    )

    val scoreRepository = ScoreRepository(
        database = database,
        profileRepository = profileRepository,
        syncStateStore = backendSyncStateStore,
    )
    val songCollectionRepository = SongCollectionRepository(database.songCollectionDao())
    val collectionSharingService = CollectionSharingService(backendApiClient, json)

    val catalogRepository = CatalogRepository(
        database = database,
        client = StaticBundleClient(BuildConfig.STATIC_ASSETS_URL, json),
        syncStateStore = CatalogSyncStateStore(applicationContext),
        coverImageStore = coverImageStore,
        presetAvatarRepository = presetAvatarRepository,
        chartFitStore = chartFitStore,
        danStore = DanStore(applicationContext, json),
    )

    val best50Repository = Best50Repository(
        database = database,
        profileRepository = profileRepository,
        catalogRepository = catalogRepository,
    )

    val recommendationRepository = RecommendationRepository(
        catalogRepository = catalogRepository,
        scoreRepository = scoreRepository,
        profileRepository = profileRepository,
    )

    val scoreQueryRepository = ScoreQueryRepository(
        catalogRepository = catalogRepository,
        scoreRepository = scoreRepository,
        communityAliasService = communityAliasService,
        profileRepository = profileRepository,
    )

    val constantTableRepository = ConstantTableRepository(
        catalogRepository = catalogRepository,
        scoreRepository = scoreRepository,
        profileRepository = profileRepository,
    )

    val plateProgressRepository = PlateProgressRepository(
        catalogRepository = catalogRepository,
        scoreRepository = scoreRepository,
        profileRepository = profileRepository,
    )

    val danRepository = DanRepository(
        catalogRepository = catalogRepository,
        scoreRepository = scoreRepository,
    )

    val backendSyncCoordinator = BackendSyncCoordinator(
        database = database,
        profileRepository = profileRepository,
        sessionManager = backendSessionManager,
        apiClient = backendApiClient,
        syncStateStore = backendSyncStateStore,
        profileAvatarStore = profileAvatarStore,
        profileCredentialStore = profileCredentialStore,
        json = json,
    )

    val scoreSyncService = ScoreSyncService(
        database = database,
        preferences = appPreferencesRepository,
        credentials = profileCredentialStore,
        backendSyncCoordinator = backendSyncCoordinator,
        backendImportService = backendImportService,
        json = json,
    )

    val otogameImportService = OtogameImportService(
        database = database,
        json = json,
    )

}
