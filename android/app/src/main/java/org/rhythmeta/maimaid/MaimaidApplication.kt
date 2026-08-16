package org.rhythmeta.maimaid

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.network.ImageRequestHeaders

class MaimaidApplication : Application(), ImageLoaderFactory {
    val container: AppContainer by lazy { AppContainer(this) }

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
