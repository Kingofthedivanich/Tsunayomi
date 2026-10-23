package app.tsunayomi.core.network

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Interceptors that sources contribute to the shared client (`@IntoSet`), e.g. MangaDex@Home load reporting. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SharedInterceptor

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NetworkModule {
    @Multibinds
    @SharedInterceptor
    abstract fun sharedInterceptors(): Set<Interceptor>

    companion object {
        private const val CACHE_SIZE_BYTES = 50L * 1024 * 1024

        @Provides
        @Singleton
        fun providesOkHttpClient(
            @ApplicationContext context: Context,
            hostAuthRegistry: HostAuthRegistry,
            @SharedInterceptor interceptors: Set<@JvmSuppressWildcards Interceptor>,
        ): OkHttpClient = OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http"), CACHE_SIZE_BYTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(UserAgentInterceptor())
            .addInterceptor(HostAuthInterceptor(hostAuthRegistry))
            .apply { interceptors.forEach(::addInterceptor) }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                }
            }
            .build()
    }
}
