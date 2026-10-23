package app.tsunayomi.translation.mlkit

import app.tsunayomi.core.model.TranslationEngine
import app.tsunayomi.translation.api.TranslationException
import app.tsunayomi.translation.api.Translator
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import com.google.mlkit.nl.translate.Translator as MlKitClient

/**
 * Free offline translation. Quality is basic and non-English pairs pivot through English,
 * but it needs no key and works without network once the ~30 MB language pack is downloaded.
 */
@Singleton
class MlKitTranslator @Inject constructor() : Translator {
    override val engine: TranslationEngine = TranslationEngine.MlKit

    private val languageId by lazy { LanguageIdentification.getClient() }
    private val clients = ConcurrentHashMap<Pair<String, String>, MlKitClient>()

    override suspend fun translate(texts: List<String>, sourceLang: String?, targetLang: String): List<String> {
        if (texts.isEmpty()) return emptyList()
        val sourceTag = sourceLang ?: identify(texts.joinToString(" "))
        val source = TranslateLanguage.fromLanguageTag(sourceTag) ?: throw TranslationException.UnsupportedLanguage(sourceTag)
        val target = TranslateLanguage.fromLanguageTag(targetLang) ?: throw TranslationException.UnsupportedLanguage(targetLang)
        if (source == target) return texts

        val client = clients.getOrPut(source to target) {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        }
        try {
            client.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        } catch (e: Exception) {
            throw TranslationException.Network(e)
        }
        return texts.map { text ->
            try {
                client.translate(text).await()
            } catch (e: Exception) {
                throw TranslationException.Engine("ML Kit failed to translate", e)
            }
        }
    }

    private suspend fun identify(text: String): String {
        val tag = runCatching { languageId.identifyLanguage(text).await() }.getOrNull()
        if (tag != null && tag != "und") return tag.substringBefore('-')
        // Too little text to tell; manga is the common case.
        return if (text.any(TextJoining::isCjk)) "ja" else "en"
    }
}
