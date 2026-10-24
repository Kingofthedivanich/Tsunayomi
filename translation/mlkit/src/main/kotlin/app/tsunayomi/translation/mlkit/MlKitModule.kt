package app.tsunayomi.translation.mlkit

import app.tsunayomi.translation.api.TextRecognizer
import app.tsunayomi.translation.api.Translator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MlKitModule {
    @Binds
    abstract fun bindsTextRecognizer(recognizer: MlKitTextRecognizer): TextRecognizer

    @Binds
    @IntoSet
    abstract fun bindsTranslator(translator: MlKitTranslator): Translator
}
