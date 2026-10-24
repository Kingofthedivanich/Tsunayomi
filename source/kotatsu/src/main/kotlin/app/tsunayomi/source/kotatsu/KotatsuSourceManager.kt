package app.tsunayomi.source.kotatsu

import android.content.Context
import android.content.SharedPreferences
import app.tsunayomi.core.common.ApplicationScope
import app.tsunayomi.source.api.DynamicSources
import app.tsunayomi.source.api.Source
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import java.util.concurrent.CountDownLatch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KotatsuSourceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    baseClient: OkHttpClient,
) : DynamicSources {

    internal val loaderContext = AndroidMangaLoaderContext(context, baseClient)
    private val prefs = context.getSharedPreferences("extensions", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val loaded = CountDownLatch(1)

    private val _sources = MutableStateFlow<List<KotatsuCatalogSource>>(emptyList())
    override val sources: StateFlow<List<Source>> = _sources.asStateFlow()

    internal fun getSource(sourceId: Long): KotatsuCatalogSource? = _sources.value.firstOrNull { it.id == sourceId }

    private val _languages = MutableStateFlow(
        prefs.getStringSet("languages", null)?.toSet() ?: setOf("en", "ru", "ja", "ko"),
    )
    private val _showNsfw = MutableStateFlow(prefs.getBoolean("show_nsfw", false))

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "languages") {
            _languages.value = prefs.getStringSet("languages", null)?.toSet() ?: setOf("en", "ru", "ja", "ko")
        } else if (key == "show_nsfw") {
            _showNsfw.value = prefs.getBoolean("show_nsfw", false)
        }
    }

    override val browsable: StateFlow<List<Source>> = combine(
        _sources,
        _languages,
        _showNsfw,
    ) { allSources, langs, nsfw ->
        allSources.filter { src ->
            val langMatches = src.lang in langs || src.lang.substringBefore('-') in langs
            val nsfwAllowed = nsfw || !(src.isNsfw || src.parserSource.contentType in ADULT_TYPES)
            langMatches && nsfwAllowed
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        scope.launch(Dispatchers.IO) {
            try {
                loadAllowlist()
            } finally {
                loaded.countDown()
            }
        }
    }

    override fun awaitLoaded() = loaded.await()

    private fun loadAllowlist() {
        val jsonString = runCatching {
            context.assets.open("allowlist.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return

        val entries = runCatching {
            json.decodeFromString<List<KotatsuAllowlistEntry>>(jsonString)
        }.getOrElse { emptyList() }

        val builtInDomains = setOf(
            "mangadex.org",
            "remanga.org",
            "asurascans.com",
            "comic.naver.com",
            "webtoons.com",
        )

        val sourcesList = mutableListOf<KotatsuCatalogSource>()

        for (entry in entries) {
            val parserSource = runCatching { MangaParserSource.valueOf(entry.name) }.getOrNull() ?: continue
            val parser = runCatching { loaderContext.newParserInstance(parserSource) }.getOrNull() ?: continue

            val domain = parser.domain.lowercase()
            // Exclude duplicates of built-in sources
            if (builtInDomains.any { domain.contains(it) }) continue
            // Exclude Kakao content / mirrors
            if (domain.contains("kakao") || domain.contains("piccoma")) continue

            sourcesList.add(KotatsuCatalogSource(parserSource, parser, isNsfw = entry.isNsfw))
        }

        _sources.value = sourcesList
    }

    private companion object {
        val ADULT_TYPES = setOf(
            ContentType.HENTAI,
            ContentType.DOUJINSHI,
            ContentType.IMAGE_SET,
            ContentType.ARTIST_CG,
            ContentType.GAME_CG,
        )
    }
}
