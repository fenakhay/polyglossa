package com.fenakhay.polyglossa

/** Which way a script runs, as Wiktionary's `Module:scripts/data` has it. */
public enum class ScriptDirection {
    /** Left to right, as most scripts run. */
    LEFT_TO_RIGHT,

    /** Right to left, as Arabic and Hebrew run. */
    RIGHT_TO_LEFT,

    /** Top to bottom, columns left to right, as Mongolian runs. */
    VERTICAL_LEFT_TO_RIGHT,

    /** Top to bottom, columns right to left. */
    VERTICAL_RIGHT_TO_LEFT,
}

/**
 * One script as English Wiktionary's `Module:scripts/data` describes it.
 *
 * ```
 * val latin = Script.of("Latn")
 * Script["Xxxx"]                         // null: no such script
 * Script.named("Arabic")?.direction      // RIGHT_TO_LEFT
 * Script.of("Hans").parent?.name         // "Han"
 * Language.of("en").scripts.first()      // Latin (Latn)
 * Script.of("Deva").nameFor(Language.of("mr"))   // "Balbodh"
 * Script.detect("Ελληνικά")              // Greek (Grek)
 * 0x416 in Script.of("Cyrl")             // true
 * ```
 *
 * @property code its code, such as `Latn`, `Arab` or `fa-Arab`.
 * @property name its name, such as `Latin`.
 * @property wikidata its Wikidata item, such as `Q8229`, or `null`.
 * @property system the kind of writing system it is, as Wiktionary words it, such as `alphabet` or `abjad`.
 * @property aliases other names it goes by.
 * @param parentCode the code of the script [parent] leads to.
 * @property direction which way it runs.
 * @param localNameCodes the names some languages give it instead of [name], by language code, which
 *   [localNames] and [nameFor] read.
 * @property ranges the code points it is made of, as `Module:scripts/data` lists them, in order; empty for a
 *   script Unicode does not encode.
 * @property marks the code points that tell it from the other scripts with the same [ranges]: for Traditional
 *   Han, the characters Unihan simplifies, and for Simplified Han, those it gives a traditional form; empty
 *   for most scripts.
 */
public class Script
internal constructor(
    public val code: String,
    public val name: String,
    public val wikidata: String? = null,
    public val system: String? = null,
    public val aliases: List<String> = emptyList(),
    internal val parentCode: String? = null,
    public val direction: ScriptDirection = ScriptDirection.LEFT_TO_RIGHT,
    internal val localNameCodes: Map<String, String> = emptyMap(),
    public val ranges: List<IntRange> = emptyList(),
    public val marks: List<IntRange> = emptyList(),
) {
    /** The catalog this script was read into, whose scripts its links lead to. */
    internal var catalog: Catalog? = null

    /** The script this is a variety of, such as Han for Simplified Han, or `null`. */
    public val parent: Script?
        get() = parentCode?.let { (catalog ?: Catalog.BUNDLED).script(it) }

    /**
     * The name [language] gives this script, which is [name] unless it has its own: Devanagari is Balbodh for
     * Marathi, and Arabic is Shahmukhi for Punjabi.
     */
    public fun nameFor(language: Language): String = localNameCodes[language.code] ?: name

    /** The names some languages give it instead of [name], such as Balbodh for Marathi's Devanagari. */
    public val localNames: Map<Language, String>
        get() =
            localNameCodes
                .mapNotNull { [code, local] ->
                    (catalog ?: Catalog.BUNDLED)[code]?.let { it to local }
                }
                .toMap()

    /** Whether [codePoint] is one of its characters. */
    public operator fun contains(codePoint: Int): Boolean = ranges.holds(codePoint)

    /** How many of the characters of [text] are its own, counting each code point once. */
    public fun count(text: String): Int {
        var count = 0
        text.forEachCodePoint { if (it in this) count++ }
        return count
    }

    /** A copy for another catalog with [marks], linked to nothing yet. */
    internal fun copy(marks: List<IntRange> = this.marks): Script =
        Script(code, name, wikidata, system, aliases, parentCode, direction, localNameCodes, ranges, marks)

    /** Whether [codePoint] is one of [marks]. */
    internal fun isMark(codePoint: Int): Boolean = marks.holds(codePoint)

    override fun equals(other: Any?): Boolean =
        other is Script &&
            code == other.code &&
            name == other.name &&
            wikidata == other.wikidata &&
            system == other.system &&
            aliases == other.aliases &&
            parentCode == other.parentCode &&
            direction == other.direction &&
            localNameCodes == other.localNameCodes &&
            ranges == other.ranges &&
            marks == other.marks

    override fun hashCode(): Int = code.hashCode() * HASH + name.hashCode()

    /** Its name and code, as `Latin (Latn)`. */
    override fun toString(): String = "$name ($code)"

    /** The scripts English Wiktionary has, as the library was built with them. */
    public companion object {
        /** The script with [code], or `null` when Wiktionary has none. */
        public operator fun get(code: String): Script? = Catalog.BUNDLED.script(code)

        /**
         * The script with [code].
         *
         * @throws IllegalArgumentException when Wiktionary has none.
         */
        public fun of(code: String): Script =
            Catalog.BUNDLED.script(code) ?: throw IllegalArgumentException("Wiktionary has no script $code")

        /**
         * The script called [name], such as `Latin`, or `null`; the one without a parent when several are.
         */
        public fun named(name: String): Script? = Catalog.BUNDLED.scriptNamed(name)

        /** Whether Wiktionary has a script with [code]. */
        public operator fun contains(code: String): Boolean = Catalog.BUNDLED.script(code) != null

        /** Every script. */
        public val all: Collection<Script>
            get() = Catalog.BUNDLED.scripts

        /** The script [text] is written in, whatever its language, or `null`; see [Catalog.detectScript]. */
        public fun detect(text: String): Script? = Catalog.BUNDLED.detectScript(text)
    }
}

private const val HASH = 31
