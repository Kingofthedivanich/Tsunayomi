package app.tsunayomi

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TsunayomiApplication : Application(), SingletonImageLoader.Factory {
    // Lazy: Coil asks for the loader on first image, long after injection is done.
    @Inject
    lateinit var imageLoader: Lazy<ImageLoader>

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()
}
