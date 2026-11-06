package com.fenakhay.polyglossa

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * A set of languages and families that know one another: a language's [Language.family] and
 * [Language.ancestors] are looked up here, as are a family's [LanguageFamily.parent] and its languages.
 *
 * [BUNDLED], English Wiktionary's own, is the one [Language] and [LanguageFamily] look up, built into the
 * library so nothing is asked of the wiki. Another is made for a test, or by the tool that records the
 * bundled one: `./gradlew catalogCheck` says what Wiktionary has changed since, and `catalogDump` records it
 * again. The data is Wiktionary's, under CC BY-SA 4.0 like the rest of its content.
 *
 * @param languages every language and etymology-only variety, each made for this catalog alone.
 * @param families every family, made the same way.
 * @param scripts every script, made the same way.
 * @property date the day Wiktionary exported the data, as `2026-11-05`, or `null` when nobody said.
 */
public class Catalog
internal constructor(
    languages: Collection<Language>,
    families: Collection<LanguageFamily> = emptyList(),
    scripts: Collection<Script> = emptyList(),
    public val date: String? = null,
) {

    private val byCode: Map<String, Language> = languages.associate {
        it.code to it.also { language -> language.catalog = this }
    }
    private val familiesByCode: Map<String, LanguageFamily> = families.associate {
        it.code to it.also { family -> family.catalog = this }
    }
    private val scriptsByCode: Map<String, Script> = scripts.associate {
        it.code to it.also { script -> script.catalog = this }
    }
    private val scriptsByName: Map<String, Script> =
        scriptsByCode.values.filter { it.parentCode != null }.associateBy { it.name } +
            scriptsByCode.values.filter { it.parentCode == null }.associateBy { it.name }
    private val byName: Map<String, Language> =
        byCode.values.filter { it.etymologyOnly }.associateBy { it.name } +
            byCode.values.filterNot { it.etymologyOnly }.associateBy { it.name }
    private val byFamily: Map<String, List<Language>> by lazy {
        byCode.values.filter { it.familyCode != null }.groupBy { checkNotNull(it.familyCode) }
    }

    /** Every language and etymology-only variety, in no particular order. */
    public val languages: Collection<Language>
        get() = byCode.values

    /** Every family, in no particular order. */
    public val families: Collection<LanguageFamily>
        get() = familiesByCode.values

    /** The language with [code], or `null`. */
    public operator fun get(code: String): Language? = byCode[code]

    /** Whether there is a language with [code]. */
    public operator fun contains(code: String): Boolean = code in byCode

    /**
     * The language called [name], as the heading of its section in an entry names it, or `null`. A full
     * language wins over an etymology-only variety of the same name.
     */
    public fun named(name: String): Language? = byName[name.trim()]

    /** The family with [code], or `null`. */
    public fun family(code: String): LanguageFamily? = familiesByCode[code]

    /** Every script, in no particular order. */
    public val scripts: Collection<Script>
        get() = scriptsByCode.values

    /** The script with [code], or `null`. */
    public fun script(code: String): Script? = scriptsByCode[code]

    /** The script called [name], or `null`; one that is no variety of another wins over one that is. */
    public fun scriptNamed(name: String): Script? = scriptsByName[name.trim()]

    /** The codes of every script. */
    internal val scriptCodes: Set<String>
        get() = scriptsByCode.keys

    private val detection by lazy { ScriptDetection(this) }

    /**
     * The script [text] is written in, whatever its language, as Wiktionary's `findBestScriptWithoutLang`
     * finds it: the one most of its characters belong to, or `null` when more of them belong to no script. A
     * tie goes to a script that holds no other whole, as Greek does beside Polytonic Greek, then to a script
     * over notation such as `Zmth`, then to the one with the most characters, as Han is beside Latin, then to
     * one that is no variety of another, as Latin is beside Fraktur. [Language.detectScript] knows better
     * when the language is known.
     *
     * ```
     * Catalog.BUNDLED.detectScript("Ελληνικά")     // Greek (Grek)
     * Catalog.BUNDLED.detectScript("ひらがな漢字")    // Japanese (Jpan)
     * Catalog.BUNDLED.detectScript("2026")         // null
     * ```
     */
    public fun detectScript(text: String): Script? = detection.detect(text)

    /** The script of [text] in [language]; see [Language.detectScript]. */
    internal fun detectScript(language: Language, text: String): Script? = detection.detect(language, text)

    /** The languages directly in the family with [code]. */
    internal fun inFamily(code: String): List<Language> = byFamily[code].orEmpty()

    /**
     * This catalog with [marks] as the characters that tell some scripts from the others with the same
     * characters, by script code: for `Hant`, the Han characters Unihan simplifies, and for `Hans`, those it
     * gives a traditional form. [Language.detectScript] reads them; the tool that records [BUNDLED] adds them
     * from Unihan. A script not in [marks] keeps the marks it has.
     */
    public fun withScriptMarks(marks: Map<String, Collection<Int>>): Catalog =
        Catalog(
            byCode.values.map { it.copy() },
            familiesByCode.values.map { it.copy() },
            scriptsByCode.values.map { script ->
                script.copy(marks = marks[script.code]?.let(::rangesOf) ?: script.marks)
            },
            date,
        )

    /**
     * This catalog as tab-separated lines, for [fromTsv] to read back: a language or a family to a line,
     * sorted by code, so two copies differ line by line where the data does.
     */
    public fun toTsv(): String =
        CatalogTable.write(byCode.values, familiesByCode.values, scriptsByCode.values, date)

    /** The catalog built into the library, and reading others. */
    public companion object {
        /**
         * English Wiktionary's languages and families as `Module:JSON data` exported them when this version
         * was made, from `data/catalog.tsv`: what [Language] and [LanguageFamily] look up.
         */
        public val BUNDLED: Catalog by lazy { fromTsv(BUNDLED_LANGUAGES.joinToString("")) }

        /**
         * What `Module:JSON data` is asked to expand for this data, in the order [fromExports] takes the
         * results: the languages' fields, their aliases, the etymology-only varieties and the families.
         */
        public val EXPORTS: List<String>
            get() =
                listOf(
                    EXPORT_LANGUAGES,
                    EXPORT_ALIASES,
                    EXPORT_ETYMOLOGY,
                    EXPORT_FAMILIES,
                    EXPORT_SCRIPTS,
                    EXPORT_VARIETIES,
                )

        private const val EXPORT_LANGUAGES = "{{#invoke:JSON data|export_languages||1|2|3|4|type|ancestors}}"
        private const val EXPORT_ALIASES = "{{#invoke:JSON data|export_languages||aliases}}"
        private const val EXPORT_ETYMOLOGY = "{{#invoke:JSON data|export_etymology_languages}}"
        private const val EXPORT_FAMILIES = "{{#invoke:JSON data|export_families}}"
        private const val EXPORT_SCRIPTS =
            "{{#invoke:JSON data|export_scripts||1|2|3|aliases|parent|direction|ranges}}"
        private const val EXPORT_VARIETIES = "{{#invoke:JSON data|export_languages||varieties}}"
        private const val FIRST_EXPORTS = 3
        private const val DEFAULT_NAME = "default"

        /**
         * The data `Module:JSON data` exported: [languages] with the keys `1`, `2`, `3`, `4`, `type` and
         * `ancestors`, [aliases] with `aliases` alone, [etymology] from `export_etymology_languages`,
         * [families] from `export_families`, [scripts] from `export_scripts` with the keys `1`, `2`, `3`,
         * `aliases`, `parent`, `direction` and `ranges`, and [varieties] from `export_languages` with
         * `varieties` alone, each the JSON text the module wrote.
         *
         * @throws IllegalStateException when one of them is the error the module wrote instead, with what it
         *   said.
         * @throws IllegalArgumentException when one of them is not JSON of that shape.
         */
        public fun fromExports(
            languages: String,
            aliases: String,
            etymology: String,
            families: String,
            scripts: String,
            varieties: String,
        ): Catalog {
            val names = parse(exported(EXPORT_ALIASES, aliases))
            val kinds = parse(exported(EXPORT_VARIETIES, varieties))
            val full =
                parse(exported(EXPORT_LANGUAGES, languages)).mapNotNull { [code, value] ->
                    val fields = value as? JsonObject ?: return@mapNotNull null
                    language(code, fields, LanguageType.REGULAR, list(names[code]), varieties(kinds[code]))
                }
            val varieties =
                parse(exported(EXPORT_ETYMOLOGY, etymology)).mapNotNull { [code, value] ->
                    val fields = value as? JsonObject ?: return@mapNotNull null
                    language(
                        code,
                        fields,
                        LanguageType.ETYMOLOGY_ONLY,
                        list(fields["aliases"]),
                        varieties(fields["varieties"]),
                    )
                }
            val groups =
                parse(exported(EXPORT_FAMILIES, families)).mapNotNull { [code, value] ->
                    val fields = value as? JsonObject ?: return@mapNotNull null
                    family(code, fields)
                }
            val writing =
                parse(exported(EXPORT_SCRIPTS, scripts)).mapNotNull { [code, value] ->
                    val fields = value as? JsonObject ?: return@mapNotNull null
                    script(code, fields)
                }
            return Catalog(full + varieties, groups, writing)
        }

        /**
         * The data `Module:JSON data` exported, one text for each of [EXPORTS] in its order, as [fromExports]
         * takes them one by one.
         *
         * @param exports one text for each of [EXPORTS], in its order.
         * @param date the day they were exported, as `2026-11-05`, kept as the catalog's [date].
         * @throws IllegalArgumentException when there are not as many texts as [EXPORTS].
         */
        public fun fromExports(exports: List<String>, date: String? = null): Catalog {
            require(exports.size == EXPORTS.size) {
                "one text for each of the ${EXPORTS.size} exports, not ${exports.size}"
            }
            val [languages, aliases, etymology] = exports
            val [families, scripts, varieties] = exports.drop(FIRST_EXPORTS)
            val catalog = fromExports(languages, aliases, etymology, families, scripts, varieties)
            return Catalog(catalog.languages, catalog.families, catalog.scripts, date)
        }

        /**
         * The data [toTsv] wrote.
         *
         * @throws IllegalArgumentException when [tsv] is not what [toTsv] writes.
         */
        public fun fromTsv(tsv: String): Catalog = CatalogTable.read(tsv)

        private fun exported(invocation: String, expanded: String): String {
            val text = expanded.trim()
            check(!text.startsWith("<")) {
                val error = text.replace(Regex("<[^>]+>"), "").replace("&#039;", "'").replace("&quot;", "\"")
                "Module:JSON data could not export $invocation: $error"
            }
            return text
        }

        private fun parse(json: String): JsonObject =
            try {
                Json.parseToJsonElement(json).jsonObject
            } catch (e: SerializationException) {
                throw IllegalArgumentException("not a JSON object: ${e.message}", e)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("not a JSON object: ${e.message}", e)
            }

        private fun language(
            code: String,
            fields: JsonObject,
            kind: LanguageType,
            aliases: List<String>,
            varieties: List<Variety>,
        ): Language? {
            val name = text(fields["1"]) ?: return null
            val type =
                when (text(fields["type"])) {
                    "reconstructed" -> LanguageType.RECONSTRUCTED
                    "appendix-constructed" -> LanguageType.APPENDIX_CONSTRUCTED
                    else -> kind
                }
            return Language(
                code = code,
                name = name,
                type = type,
                wikidata = item(fields["2"]),
                familyCode = text(fields["3"]),
                scriptCodes = codes(fields["4"]),
                ancestorCodes = codes(fields["ancestors"]),
                aliases = aliases,
                parentCode = text(fields["parent"]),
                varieties = varieties,
            )
        }

        /** The varieties [element] lists: each a name, or a list of a name and the other names it goes by. */
        private fun varieties(element: JsonElement?): List<Variety> =
            (element as? JsonArray).orEmpty().mapNotNull { variety ->
                when (variety) {
                    is JsonArray -> {
                        val names = variety.mapNotNull { text(it) }
                        names.firstOrNull()?.let { Variety(it, names.drop(1)) }
                    }
                    else -> text(variety)?.let { Variety(it) }
                }
            }

        private fun family(code: String, fields: JsonObject): LanguageFamily? {
            val name = text(fields["1"]) ?: return null
            return LanguageFamily(
                code = code,
                name = name,
                wikidata = item(fields["2"]),
                parentCode = text(fields["3"]),
                protoLanguageCode = text(fields["protoLanguage"]),
                aliases = list(fields["aliases"]),
            )
        }

        private fun script(code: String, fields: JsonObject): Script? {
            val names = fields["1"] as? JsonObject
            val name = text(names?.get(DEFAULT_NAME) ?: fields["1"]) ?: return null
            val localNames =
                names
                    .orEmpty()
                    .filterKeys { it != DEFAULT_NAME }
                    .mapNotNull { [language, local] ->
                        text(local)?.let { language to it }
                    }
            return Script(
                code = code,
                name = name,
                wikidata = item(fields["2"]),
                system = text(fields["3"]),
                aliases = list(fields["aliases"]),
                parentCode = text(fields["parent"]),
                direction = direction(text(fields["direction"])),
                localNameCodes = localNames.toMap(),
                ranges = ranges(fields["ranges"]),
            )
        }

        /** The ranges [element] lists as code points, first and last of each in turn, sorted and joined. */
        private fun ranges(element: JsonElement?): List<IntRange> =
            (element as? JsonArray)
                .orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.longOrNull?.toInt() }
                .chunked(2)
                .filter { it.size == 2 }
                .map { [first, last] -> first..last }
                .let(::joined)

        /** [ranges] sorted, with those that overlap or touch made one. */
        internal fun joined(ranges: List<IntRange>): List<IntRange> {
            val joined = mutableListOf<IntRange>()
            for (range in ranges.filterNot { it.isEmpty() }.sortedBy { it.first }) {
                val last = joined.lastOrNull()
                if (last != null && range.first <= last.last + 1) {
                    joined[joined.lastIndex] = last.first..maxOf(last.last, range.last)
                } else {
                    joined += range
                }
            }
            return joined
        }

        internal fun direction(written: String?): ScriptDirection =
            when (written) {
                "rtl" -> ScriptDirection.RIGHT_TO_LEFT
                "vertical-ltr" -> ScriptDirection.VERTICAL_LEFT_TO_RIGHT
                "vertical-rtl" -> ScriptDirection.VERTICAL_RIGHT_TO_LEFT
                else -> ScriptDirection.LEFT_TO_RIGHT
            }

        private fun text(element: JsonElement?): String? =
            (element as? JsonPrimitive)
                ?.takeIf { element !is JsonNull }
                ?.contentOrNull
                ?.takeIf { it.isNotEmpty() }

        private fun item(element: JsonElement?): String? {
            val primitive = element as? JsonPrimitive ?: return null
            primitive.longOrNull?.let {
                return "Q$it"
            }
            return text(primitive)?.let { if (it.startsWith("Q")) it else "Q$it" }
        }

        private fun codes(element: JsonElement?): List<String> =
            when (element) {
                is JsonArray -> list(element)
                else -> text(element)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            }

        private fun list(element: JsonElement?): List<String> =
            when (element) {
                is JsonArray -> element.mapNotNull { text(it) }
                else -> text(element)?.let(::listOf).orEmpty()
            }
    }
}
