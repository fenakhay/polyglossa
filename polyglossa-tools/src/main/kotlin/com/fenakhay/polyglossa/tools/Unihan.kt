package com.fenakhay.polyglossa.tools

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import java.util.zip.ZipInputStream
import kotlin.io.path.inputStream
import kotlin.io.path.name

/**
 * The Unicode Consortium's Unihan database, which tells Traditional Han from Simplified Han for script
 * detection: `Unihan_Variants.txt` of Unicode [VERSION].
 */
internal object Unihan {
    /** The version of Unicode whose Unihan is read; moved by hand when Unicode makes a new one. */
    const val VERSION = "18.0.0"

    private const val URL = "https://www.unicode.org/Public/$VERSION/ucd/Unihan.zip"
    private const val VARIANTS = "Unihan_Variants.txt"
    private const val USER_AGENT = "Polyglossa/0.1 (https://github.com/fenakhay/polyglossa)"
    private const val TIMEOUT_SECONDS = 120L
    private const val HTTP_OK = 200
    private const val HEX = 16
    private const val SIMPLIFIED = "kSimplifiedVariant"
    private const val TRADITIONAL = "kTraditionalVariant"

    /** `Unihan_Variants.txt`, from Unihan.zip of Unicode [VERSION] at unicode.org. */
    fun download(): String {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS)).build()
        val request =
            HttpRequest.newBuilder(URI(URL))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("User-Agent", USER_AGENT)
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() != HTTP_OK) throw IOException("$URL answered ${response.statusCode()}")
        return variants(ByteArrayInputStream(response.body()))
    }

    /** `Unihan_Variants.txt` from [file], that file itself or a Unihan.zip that holds it. */
    fun read(file: Path): String =
        if (file.name.endsWith(".zip")) file.inputStream().use(::variants)
        else file.inputStream().use { it.readBytes().decodeToString() }

    private fun variants(zip: InputStream): String {
        ZipInputStream(zip).use { entries ->
            while (true) {
                val entry = entries.nextEntry ?: throw IOException("Unihan.zip holds no $VARIANTS")
                if (entry.name == VARIANTS) return entries.readBytes().decodeToString()
            }
        }
    }

    /**
     * The marks of Traditional Han, `Hant`, and Simplified Han, `Hans`, in [variants]: the characters whose
     * every simplified form is another character, and those whose every traditional form is. A character that
     * is its own simplified or traditional form among others, as 出 is beside 齣, marks neither.
     *
     * @throws IllegalStateException when [variants] gives either no marks, as a broken download would.
     */
    fun marks(variants: String): Map<String, Set<Int>> {
        val entries = variants.lineSequence().mapNotNull(::entry).toList()
        val traditional = entries.filter { it.field == SIMPLIFIED }.map { it.point }.toSet()
        val simplified = entries.filter { it.field == TRADITIONAL }.map { it.point }.toSet()
        check(traditional.isNotEmpty() && simplified.isNotEmpty()) {
            "Unihan $VERSION gave no traditional or no simplified characters; " +
                "refusing what looks like a broken download"
        }
        return mapOf("Hant" to traditional, "Hans" to simplified)
    }

    /** A character of one line of `Unihan_Variants.txt` whose every other form is another, or `null`. */
    private fun entry(line: String): Entry? {
        val cells = line.split('\t')
        val point = cells.firstOrNull()?.let(::codePoint) ?: return null
        val forms = cells.getOrNull(2).orEmpty().split(' ').mapNotNull { codePoint(it.substringBefore('<')) }
        val field = cells.getOrNull(1)?.takeIf { forms.isNotEmpty() && point !in forms }
        return field?.let { Entry(point, it) }
    }

    /** One character and the Unihan field that gives it its other forms. */
    private class Entry(val point: Int, val field: String)

    private fun codePoint(text: String): Int? =
        text.removePrefix("U+").takeIf { it != text }?.toIntOrNull(HEX)
}
