package app.tsunayomi.extension.manager

import app.tsunayomi.core.network.HttpException
import app.tsunayomi.core.network.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.GzipSource
import okio.buffer
import javax.inject.Inject
import javax.inject.Singleton

/** Reads extension repositories in both the old JSON layout and the newer protobuf index. */
@OptIn(ExperimentalSerializationApi::class)
@Singleton
class RepoService @Inject constructor(private val client: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val protoBuf = ProtoBuf

    /**
     * Resolves what the user typed: a link to index.min.json, repo.json, index.pb, or the repo folder itself.
     */
    suspend fun fetchRepo(url: String): ExtensionRepo = withContext(Dispatchers.IO) {
        val trimmed = url.trim().removeSuffix("/")
        val candidates = when {
            trimmed.endsWith(".json") || trimmed.endsWith(".pb") -> listOf(trimmed)
            else -> listOf("$trimmed/repo.json", "$trimmed/index.pb", "$trimmed/index.min.json")
        }
        var lastError: Exception? = null
        for (candidate in candidates) {
            try {
                return@withContext fetchRepoAt(candidate)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalArgumentException(url)
    }

    private suspend fun fetchRepoAt(indexUrl: String): ExtensionRepo {
        val bytes = download(indexUrl)
        return when (bytes.firstOrNull()) {
            '['.code.toByte() -> {
                require(indexUrl.endsWith("/index.min.json")) { "Not a repository index: $indexUrl" }
                fetchRepoAt(indexUrl.replace("/index.min.json", "/repo.json"))
            }
            '{'.code.toByte() -> {
                val text = bytes.decodeToString()
                val legacy = runCatching { json.decodeFromString<LegacyRepo>(text) }.getOrNull()
                when {
                    legacy?.indexV2 != null -> fetchRepoAt(legacy.indexV2)
                    legacy != null -> ExtensionRepo(
                        indexUrl = indexUrl,
                        name = legacy.meta.name,
                        signingKey = legacy.meta.signingKeyFingerprint,
                        website = legacy.meta.website,
                        legacy = true,
                    )
                    else -> json.decodeFromString<StoreIndex>(text).toRepo(indexUrl)
                }
            }
            else -> protoBuf.decodeFromByteArray<StoreIndex>(bytes).toRepo(indexUrl)
        }
    }

    suspend fun fetchExtensions(repo: ExtensionRepo): List<AvailableExtension> = withContext(Dispatchers.IO) {
        when {
            repo.extensionListUrl != null -> {
                val bytes = download(repo.extensionListUrl)
                val list = if (bytes.firstOrNull() == '{'.code.toByte()) {
                    json.decodeFromString<StoreIndex.ExtensionList>(bytes.decodeToString())
                } else {
                    protoBuf.decodeFromByteArray<StoreIndex.ExtensionList>(bytes)
                }
                list.extensions.map { it.toAvailable(repo) }
            }
            !repo.legacy -> {
                val bytes = download(repo.indexUrl)
                val index = if (bytes.firstOrNull() == '{'.code.toByte()) {
                    json.decodeFromString<StoreIndex>(bytes.decodeToString())
                } else {
                    protoBuf.decodeFromByteArray<StoreIndex>(bytes)
                }
                index.extensionList?.extensions.orEmpty().map { it.toAvailable(repo) }
            }
            else -> {
                val base = repo.indexUrl.removeSuffix("/repo.json")
                json.decodeFromString<List<LegacyExtension>>(download("$base/index.min.json").decodeToString())
                    .map { it.toAvailable(repo, base) }
            }
        }
    }

    /** The response body, un-gzipped if the server sent a .gz file without Content-Encoding. */
    suspend fun download(url: String): ByteArray {
        val response = client.newCall(Request.Builder().url(url).build()).await()
        val bytes = response.use {
            if (!it.isSuccessful) throw HttpException(it.code, url)
            it.body.bytes()
        }
        val gzip = bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
        return if (gzip) GzipSource(Buffer().write(bytes)).buffer().use { it.readByteArray() } else bytes
    }

    private fun StoreIndex.toRepo(indexUrl: String) = ExtensionRepo(
        indexUrl = indexUrl,
        name = name,
        signingKey = signingKey,
        website = contact?.website?.ifEmpty { null },
        extensionListUrl = extensionListUrl,
    )
}
