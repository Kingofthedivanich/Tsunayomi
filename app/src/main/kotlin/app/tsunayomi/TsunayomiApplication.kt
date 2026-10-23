package app.tsunayomi

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.tsunayomi.crash.CrashReporter
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TsunayomiApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    // Lazy: Coil asks for the loader on first image, long after injection is done.
    @Inject
    lateinit var imageLoader: Lazy<ImageLoader>

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this, BuildConfig.VERSION_NAME)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()

    // Workers (chapter downloads) get their dependencies from Hilt.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
