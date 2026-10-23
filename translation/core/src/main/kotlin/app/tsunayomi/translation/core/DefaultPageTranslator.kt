package app.tsunayomi.translation.core

import app.tsunayomi.core.model.TranslationEngine
import app.tsunayomi.translation.api.PageRequest
import app.tsunayomi.translation.api.PageTranslation
import app.tsunayomi.translation.api.PageTranslator
import app.tsunayomi.translation.api.RecognizedBlock
import app.tsunayomi.translation.api.SourceText
import app.tsunayomi.translation.api.TextJoining
import app.tsunayomi.translation.api.TextRecognizer
import app.tsunayomi.translation.api.TranslatedRegion
import app.tsunayomi.translation.api.TranslationContext
import app.tsunayomi.translation.api.TranslationException
import app.tsunayomi.translation.api.Translator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** The engine and target language to use right now. */
fun interface TranslationTarget {
    suspend fun current(): Pair<TranslationEngine, String>
}

/**
 * recognize → group bubbles → translate → cache, for a run of pages at once.
 * Batch engines (Gemini) get all pages in as few requests as possible, together with the manga's glossary,
 * and the names they report are added to it. Offline engines are called page by page.
 * If the chosen online engine fails for a reason the user can fix later (no key, quota, offline),
 * the pages are translated offline with ML Kit and the reason is reported alongside.
 */
@Singleton
class DefaultPageTranslator @Inject constructor(
    private val recognizer: TextRecognizer,
    translators: Set<@JvmSuppressWildcards Translator>,
    private val cache: TranslationCache,
    private val glossary: GlossaryStore,
    private val target: TranslationTarget,
) : PageTranslator {
    private val byEngine = translators.associateBy { it.engine }
    private val offline = byEngine[TranslationEngine.MlKit] ?: error("ML Kit translator is required as the fallback")

    // One batch at a time: no duplicate work for overlapping requests, and free API tiers count requests per minute.
    private val mutex = Mutex()

    override suspend fun translate(pages: List<PageRequest>, sourceLang: String?, mangaId: Long?): List<PageTranslation> =
        mutex.withLock {
            val (engine, targetLang) = target.current()
            val primary = byEngine[engine] ?: offline
            val primaryKey = primary.cacheKey(sourceLang)
            val results = arrayOfNulls<PageTranslation>(pages.size)
            pages.forEachIndexed { index, page ->
                cache.get(page.key, targetLang, primaryKey)?.let { results[index] = PageTranslation(it, primary.engine) }
            }
            val missing = pages.indices.filter { results[it] == null }
            if (missing.isEmpty()) return@withLock results.requireNoNulls().toList()

            val blocks = missing.associateWith { BubbleGrouping.group(recognizer.recognize(pages[it].load(), sourceLang)) }
            try {
                translateBlocks(primary, blocks, sourceLang, targetLang, mangaId).forEach { (index, regions) ->
                    cache.put(pages[index].key, targetLang, primaryKey, regions)
                    results[index] = PageTranslation(regions, primary.engine)
                }
            } catch (e: TranslationException) {
                if (primary === offline) throw e
                // Cached under ML Kit's key, so the chosen engine is tried again next time.
                val offlineKey = offline.cacheKey(sourceLang)
                missing.forEach { index ->
                    val regions = cache.get(pages[index].key, targetLang, offlineKey)
                        ?: translateBlocks(offline, mapOf(index to blocks.getValue(index)), sourceLang, targetLang, mangaId)
                            .getValue(index)
                            .also { cache.put(pages[index].key, targetLang, offlineKey, it) }
                    results[index] = PageTranslation(regions, offline.engine, fallbackReason = e)
                }
            }
            results.requireNoNulls().toList()
        }

    /** Translates the blocks of several pages; keys are positions in the request list. */
    private suspend fun translateBlocks(
        translator: Translator,
        blocksByPage: Map<Int, List<RecognizedBlock>>,
        sourceLang: String?,
        targetLang: String,
        mangaId: Long?,
    ): Map<Int, List<TranslatedRegion>> {
        val result = blocksByPage.filterValues { it.isEmpty() }.mapValuesTo(mutableMapOf()) { emptyList<TranslatedRegion>() }
        val chunks = if (translator.batchesPages) {
            chunk(blocksByPage.filterValues { it.isNotEmpty() })
        } else {
            blocksByPage.filterValues { it.isNotEmpty() }.map { mapOf(it.key to it.value) }
        }
        for (pagesInChunk in chunks) {
            val items = pagesInChunk.entries.flatMapIndexed { pagePosition, (index, blocks) ->
                blocks.map { block -> Triple(index, block, pagePosition + 1) }
            }
            val terms = if (translator.batchesPages && mangaId != null) glossary.terms(mangaId, targetLang) else emptyMap()
            val batch = translator.translate(
                texts = items.map { (_, block, page) -> SourceText(TextJoining.normalizeCase(block.text), page) },
                sourceLang = sourceLang,
                targetLang = targetLang,
                context = TranslationContext(glossary = terms),
            )
            if (mangaId != null && batch.newTerms.isNotEmpty()) {
                glossary.add(mangaId, targetLang, batch.newTerms.filterKeys { it !in terms })
            }
            items.forEachIndexed { position, (index, block, _) ->
                val translated = batch.translations.getOrElse(position) { block.text }
                result[index] = result[index].orEmpty() + TranslatedRegion(block.box, block.text, translated)
            }
        }
        return result
    }

    /** Splits pages into requests small enough for one LLM call, keeping pages whole and in order. */
    internal fun chunk(blocksByPage: Map<Int, List<RecognizedBlock>>): List<Map<Int, List<RecognizedBlock>>> {
        val chunks = mutableListOf<Map<Int, List<RecognizedBlock>>>()
        var current = linkedMapOf<Int, List<RecognizedBlock>>()
        var items = 0
        var chars = 0
        for ((index, blocks) in blocksByPage.toSortedMap()) {
            val pageChars = blocks.sumOf { it.text.length }
            if (current.isNotEmpty() && (items + blocks.size > MAX_ITEMS_PER_REQUEST || chars + pageChars > MAX_CHARS_PER_REQUEST)) {
                chunks += current
                current = linkedMapOf()
                items = 0
                chars = 0
            }
            current[index] = blocks
            items += blocks.size
            chars += pageChars
        }
        if (current.isNotEmpty()) chunks += current
        return chunks
    }

    /** Engine, its prompt version, the pipeline version and the source language all change the result. */
    private fun Translator.cacheKey(sourceLang: String?) =
        "${engine.name}:$cacheVersion:p$PIPELINE_VERSION:${sourceLang ?: "auto"}"

    private companion object {
        /** Bump when recognition or grouping changes, so pages translated the old way are redone. */
        const val PIPELINE_VERSION = 2

        const val MAX_ITEMS_PER_REQUEST = 200
        const val MAX_CHARS_PER_REQUEST = 12_000
    }
}
