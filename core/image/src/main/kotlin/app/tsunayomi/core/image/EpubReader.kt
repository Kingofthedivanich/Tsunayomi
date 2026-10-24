package app.tsunayomi.core.image

import app.tsunayomi.core.common.NaturalOrderComparator
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.net.URLDecoder

data class EpubMetadata(
    val title: String? = null,
    val author: String? = null,
    val description: String? = null,
    val coverEntry: String? = null,
    val isRightToLeft: Boolean? = null,
)

/**
 * Parses EPUB comic/manga archives: extracts page images in spine reading order
 * and reads title, author, description, and reading direction (LTR/RTL).
 */
internal object EpubReader {
    private const val CONTAINER_XML = "META-INF/container.xml"

    fun isEpub(zip: ZipFile): Boolean {
        if (findZipEntry(zip, CONTAINER_XML) != null) return true
        val mimetypeEntry = findZipEntry(zip, "mimetype") ?: return false
        val content = runCatching { zip.getInputStream(mimetypeEntry).use { it.readBytes().decodeToString().trim() } }.getOrNull()
        return content == "application/epub+zip"
    }

    /**
     * Finds image entries in the exact order specified by the EPUB spine.
     * Returns null if no spine images can be extracted (falling back to generic archive sorting).
     */
    fun getSpineImageEntries(zip: ZipFile): List<String>? {
        val opfEntry = findOpfEntry(zip) ?: return null
        val opfText = runCatching { zip.getInputStream(opfEntry).use { it.readBytes().decodeToString() } }.getOrNull()
            ?: return null

        val opfDir = opfEntry.name.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val manifest = parseManifest(opfText, opfDir)
        val spineIdrefs = parseSpine(opfText)

        val pages = mutableListOf<String>()

        for (idref in spineIdrefs) {
            val item = manifest[idref] ?: continue
            if (item.mediaType.startsWith("image/") || ImageFiles.isImage(item.fullPath)) {
                val actual = findZipEntry(zip, item.fullPath)
                if (actual != null && !isJunk(actual.name)) {
                    pages.add(actual.name)
                }
            } else if (item.mediaType.contains("xhtml") || item.mediaType.contains("html") ||
                item.fullPath.endsWith(".xhtml", ignoreCase = true) || item.fullPath.endsWith(".html", ignoreCase = true)
            ) {
                val xhtmlEntry = findZipEntry(zip, item.fullPath) ?: continue
                val xhtmlText = runCatching { zip.getInputStream(xhtmlEntry).use { it.readBytes().decodeToString() } }.getOrNull()
                    ?: continue
                val xhtmlDir = item.fullPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }

                val imagesInXhtml = extractImagesFromHtml(xhtmlText, xhtmlDir, zip)
                pages.addAll(imagesInXhtml)
            }
        }

        if (pages.isNotEmpty()) {
            return pages.distinct()
        }

        // Fallback: all images in manifest in manifest order
        val manifestImages = manifest.values
            .filter { it.mediaType.startsWith("image/") || ImageFiles.isImage(it.fullPath) }
            .mapNotNull { findZipEntry(zip, it.fullPath)?.name }
            .filter { !isJunk(it) }
            .distinct()

        return manifestImages.takeIf { it.isNotEmpty() }
    }

    fun getEpubMetadata(zip: ZipFile): EpubMetadata? {
        val opfEntry = findOpfEntry(zip) ?: return null
        val opfText = runCatching { zip.getInputStream(opfEntry).use { it.readBytes().decodeToString() } }.getOrNull()
            ?: return null

        val opfDir = opfEntry.name.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val manifest = parseManifest(opfText, opfDir)

        val title = extractTagContent(opfText, "dc:title")
        val author = extractTagContent(opfText, "dc:creator")
        val description = extractTagContent(opfText, "dc:description")?.let(::stripHtml)

        val spineTag = Regex("""<spine\b([^>]*)>""", RegexOption.IGNORE_CASE).find(opfText)?.groupValues?.get(1).orEmpty()
        val progression = extractAttribute(spineTag, "page-progression-direction")
        val isRightToLeft = when (progression?.lowercase()) {
            "rtl" -> true
            "ltr" -> false
            else -> null
        }

        val coverEntry = findCoverEntry(opfText, manifest, zip)

        return EpubMetadata(
            title = title,
            author = author,
            description = description,
            coverEntry = coverEntry,
            isRightToLeft = isRightToLeft,
        )
    }

    private fun findOpfEntry(zip: ZipFile): ZipArchiveEntry? {
        val containerEntry = findZipEntry(zip, CONTAINER_XML)
        if (containerEntry != null) {
            val xml = runCatching { zip.getInputStream(containerEntry).use { it.readBytes().decodeToString() } }.getOrNull()
            if (xml != null) {
                val fullPath = Regex("""<rootfile\b[^>]+full-path=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    .find(xml)?.groupValues?.get(1)
                if (fullPath != null) {
                    val decoded = decodeUrl(fullPath)
                    findZipEntry(zip, decoded)?.let { return it }
                }
            }
        }
        // Fallback: search for any .opf entry
        return zip.entries.asSequence().firstOrNull { it.name.endsWith(".opf", ignoreCase = true) }
    }

    private fun parseManifest(opfText: String, opfDir: String): Map<String, ManifestItem> {
        val manifest = mutableMapOf<String, ManifestItem>()
        val itemRegex = Regex("""<item\b([^>]+)>""", RegexOption.IGNORE_CASE)
        for (match in itemRegex.findAll(opfText)) {
            val attrs = match.groupValues[1]
            val id = extractAttribute(attrs, "id") ?: continue
            val rawHref = extractAttribute(attrs, "href") ?: continue
            val mediaType = extractAttribute(attrs, "media-type").orEmpty()
            val properties = extractAttribute(attrs, "properties").orEmpty()

            val cleanHref = decodeUrl(rawHref)
            val fullPath = resolvePath(opfDir, cleanHref)
            manifest[id] = ManifestItem(id = id, fullPath = fullPath, mediaType = mediaType, properties = properties)
        }
        return manifest
    }

    private fun parseSpine(opfText: String): List<String> {
        val spineContent = Regex("""<spine\b[^>]*>(.*?)</spine>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(opfText)?.groupValues?.get(1) ?: return emptyList()

        val itemrefRegex = Regex("""<itemref\b([^>]+)>""", RegexOption.IGNORE_CASE)
        return itemrefRegex.findAll(spineContent).mapNotNull { match ->
            extractAttribute(match.groupValues[1], "idref")
        }.toList()
    }

    private fun extractImagesFromHtml(html: String, htmlDir: String, zip: ZipFile): List<String> {
        val images = mutableListOf<String>()

        val imgRegex = Regex("""<img\b([^>]+)>""", RegexOption.IGNORE_CASE)
        for (match in imgRegex.findAll(html)) {
            val src = extractAttribute(match.groupValues[1], "src") ?: continue
            val resolved = resolvePath(htmlDir, decodeUrl(src))
            val entry = findZipEntry(zip, resolved)
            if (entry != null && ImageFiles.isImage(entry.name) && !isJunk(entry.name)) {
                images.add(entry.name)
            }
        }

        val svgImageRegex = Regex("""<image\b([^>]+)>""", RegexOption.IGNORE_CASE)
        for (match in svgImageRegex.findAll(html)) {
            val attrs = match.groupValues[1]
            val href = extractAttribute(attrs, "xlink:href") ?: extractAttribute(attrs, "href") ?: continue
            val resolved = resolvePath(htmlDir, decodeUrl(href))
            val entry = findZipEntry(zip, resolved)
            if (entry != null && ImageFiles.isImage(entry.name) && !isJunk(entry.name)) {
                images.add(entry.name)
            }
        }

        return images
    }

    private fun findCoverEntry(opfText: String, manifest: Map<String, ManifestItem>, zip: ZipFile): String? {
        // 1. EPUB3 properties="cover-image"
        manifest.values.firstOrNull { it.properties.contains("cover-image") }?.let { item ->
            findZipEntry(zip, item.fullPath)?.let { return it.name }
        }

        // 2. EPUB2 <meta name="cover" content="item_id"/>
        val metaCoverId = Regex("""<meta\b[^>]+name=["']cover["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(opfText)?.groupValues?.get(1)
            ?: Regex("""<meta\b[^>]+content=["']([^"']+)["'][^>]+name=["']cover["']""", RegexOption.IGNORE_CASE)
                .find(opfText)?.groupValues?.get(1)

        if (metaCoverId != null) {
            val item = manifest[metaCoverId]
            if (item != null) {
                findZipEntry(zip, item.fullPath)?.let { return it.name }
            }
        }

        // 3. Any item with "cover" in ID or path that is an image
        manifest.values.firstOrNull { item ->
            (item.id.contains("cover", ignoreCase = true) || item.fullPath.contains("cover", ignoreCase = true)) &&
                (item.mediaType.startsWith("image/") || ImageFiles.isImage(item.fullPath))
        }?.let { item ->
            findZipEntry(zip, item.fullPath)?.let { return it.name }
        }

        return null
    }

    internal fun resolvePath(baseDir: String, relativePath: String): String {
        val cleanRel = relativePath.substringBefore('#').substringBefore('?').replace('\\', '/')
        if (cleanRel.startsWith('/')) {
            return cleanRel.removePrefix("/")
        }
        val combined = (baseDir.trimEnd('/') + "/" + cleanRel).split('/')
        val stack = mutableListOf<String>()
        for (part in combined) {
            when (part) {
                "", "." -> continue
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                else -> stack.add(part)
            }
        }
        return stack.joinToString("/")
    }

    private fun findZipEntry(zip: ZipFile, path: String): ZipArchiveEntry? {
        val normalized = path.replace('\\', '/').trimStart('/')
        zip.getEntry(normalized)?.let { return it }
        return zip.entries.asSequence().firstOrNull {
            it.name.replace('\\', '/').trimStart('/').equals(normalized, ignoreCase = true)
        }
    }

    private fun extractAttribute(tagAttrs: String, attributeName: String): String? {
        val regex = Regex("""\b$attributeName\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        return regex.find(tagAttrs)?.groupValues?.get(1)
    }

    private fun extractTagContent(xml: String, tagName: String): String? {
        val regex = Regex("""<$tagName\b[^>]*>(.*?)</$tagName>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        return regex.find(xml)?.groupValues?.get(1)?.let(::cleanXmlText)
    }

    private fun cleanXmlText(text: String): String = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .trim()

    private fun stripHtml(text: String): String = text.replace(Regex("<[^>]+>"), "").trim()

    private fun decodeUrl(url: String): String = runCatching { URLDecoder.decode(url, "UTF-8") }.getOrDefault(url)

    private fun isJunk(name: String): Boolean =
        name.startsWith("__MACOSX/") || name.substringAfterLast('/').startsWith(".")

    private data class ManifestItem(
        val id: String,
        val fullPath: String,
        val mediaType: String,
        val properties: String,
    )
}
