package net.krtl.maimaid

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import net.krtl.maimaid.ui.app.AppContainer
import okhttp3.OkHttpClient

class MaimaidApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(
                OkHttpClient.Builder()
                    .retryOnConnectionFailure(true)
                    .build()
            )
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.28)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_cover_cache"))
                    .maxSizePercent(0.04)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }
}
