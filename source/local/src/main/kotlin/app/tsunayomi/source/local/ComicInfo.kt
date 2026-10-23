package app.tsunayomi.source.local

import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/** The subset of ComicInfo.xml (ComicRack / Anansi schema) that maps onto our manga details. */
internal data class ComicInfo(
    val series: String? = null,
    val summary: String? = null,
    val writer: String? = null,
    val penciller: String? = null,
    val genres: List<String> = emptyList(),
    val languageIso: String? = null,
    /** "YesAndRightToLeft" in ComicInfo means a right-to-left manga. */
    val rightToLeft: Boolean? = null,
    val status: String? = null,
) {
    companion object {
        fun parse(input: InputStream): ComicInfo? = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // ComicInfo never needs entities; refuse them so a crafted file cannot read local files.
                // Android's parser does not support the feature (and never resolves external entities), hence runCatching.
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            }
            val root = factory.newDocumentBuilder().parse(input).documentElement
            val values = buildMap {
                val children = root.childNodes
                for (i in 0 until children.length) {
                    val node = children.item(i) as? Element ?: continue
                    val text = node.textContent?.trim().orEmpty()
                    if (text.isNotEmpty()) put(node.localName ?: node.tagName, text)
                }
            }
            ComicInfo(
                series = values["Series"],
                summary = values["Summary"],
                writer = values["Writer"],
                penciller = values["Penciller"],
                genres = listOfNotNull(values["Genre"], values["Tags"])
                    .flatMap { it.split(',') }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct(),
                languageIso = values["LanguageISO"]?.substringBefore('-')?.lowercase(),
                rightToLeft = values["Manga"]?.let { it.equals("YesAndRightToLeft", ignoreCase = true) },
                // Mihon writes the status into its own namespaced element.
                status = values["PublishingStatusTachiyomi"],
            )
        }.getOrNull()
    }
}
