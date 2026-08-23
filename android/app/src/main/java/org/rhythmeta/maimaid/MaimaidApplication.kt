package org.rhythmeta.maimaid

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.CrashLogStore
import org.rhythmeta.maimaid.core.network.ImageRequestHeaders

class MaimaidApplication : Application(), ImageLoaderFactory {
    val container: AppContainer by lazy { AppContainer(this) }

    internal val crashLogStore by lazy { CrashLogStore(this) }
    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        crashLogStore.install()
        widgetScope.launch {
            val best50 = container.best50Repository.observeBest50(
                b35CountOverride = 35,
                b15CountOverride = 15,
            )
            combine(container.profileRepository.activeProfile, best50) { profile, state ->
                profile to state
            }.distinctUntilChanged().collect {
                container.widgetUpdateCoordinator.requestUpdate()
            }
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                        .newBuilder()
                        .header("Accept", ImageRequestHeaders.ACCEPT)
                        .build()
                    chain.proceed(request)
                }
                .build()
        }
        .build()
}
