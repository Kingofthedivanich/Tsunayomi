package app.tsunayomi.translation.core

import android.graphics.Bitmap
import app.tsunayomi.core.model.TranslationEngine
import app.tsunayomi.translation.api.PageTranslation
import app.tsunayomi.translation.api.PageTranslator
import app.tsunayomi.translation.api.RecognizedBlock
import app.tsunayomi.translation.api.TextRecognizer
import app.tsunayomi.translation.api.TranslatedRegion
import app.tsunayomi.translation.api.TranslationException
import app.tsunayomi.translation.api.Translator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** The engine and target language to use right now. */
fun interface TranslationTarget {
    suspend fun current(): Pair<TranslationEngine, String>
}

/**
 * recognize → translate → cache, one page at a time per key (a prefetch and the visible page never duplicate work).
 * If the chosen online engine fails for a reason the user can fix later (no key, quota, offline),
 * the page is translated offline with ML Kit and the reason is reported alongside.
 */
@Singleton
class DefaultPageTranslator @Inject constructor(
    private val recognizer: TextRecognizer,
    translators: Set<@JvmSuppressWildcards Translator>,
    private val cache: TranslationCache,
    private val target: TranslationTarget,
) : PageTranslator {
    private val byEngine = translators.associateBy { it.engine }
    private val offline = byEngine[TranslationEngine.MlKit] ?: error("ML Kit translator is required as the fallback")
    private val locks = ConcurrentHashMap<String, Mutex>()

    override suspend fun translate(pageKey: String, sourceLang: String?, loadPage: suspend () -> Bitmap): PageTranslation {
        val (engine, targetLang) = target.current()
        val primary = byEngine[engine] ?: offline
        return locks.getOrPut(pageKey) { Mutex() }.withLock {
            cache.get(pageKey, targetLang, primary.cacheKey())?.let { return@withLock PageTranslation(it, primary.engine) }

            val blocks = recognizer.recognize(loadPage(), sourceLang)
            if (blocks.isEmpty()) {
                cache.put(pageKey, targetLang, primary.cacheKey(), emptyList())
                return@withLock PageTranslation(emptyList(), primary.engine)
            }
            try {
                val regions = translateBlocks(primary, blocks, sourceLang, targetLang)
                cache.put(pageKey, targetLang, primary.cacheKey(), regions)
                PageTranslation(regions, primary.engine)
            } catch (e: TranslationException) {
                if (primary === offline) throw e
                // Cached under ML Kit's key, so the chosen engine is tried again next time.
                val regions = cache.get(pageKey, targetLang, offline.cacheKey())
                    ?: translateBlocks(offline, blocks, sourceLang, targetLang)
                        .also { cache.put(pageKey, targetLang, offline.cacheKey(), it) }
                PageTranslation(regions, offline.engine, fallbackReason = e)
            }
        }
    }

    private suspend fun translateBlocks(
        translator: Translator,
        blocks: List<RecognizedBlock>,
        sourceLang: String?,
        targetLang: String,
    ): List<TranslatedRegion> {
        val translations = translator.translate(blocks.map { it.text }, sourceLang, targetLang)
        return blocks.mapIndexed { index, block ->
            TranslatedRegion(block.box, block.text, translations.getOrElse(index) { block.text })
        }
    }

    private fun Translator.cacheKey() = "${engine.name}:$cacheVersion"
}
