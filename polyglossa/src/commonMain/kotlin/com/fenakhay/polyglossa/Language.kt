package com.fenakhay.polyglossa

/** What kind of language Wiktionary takes one to be, which decides where its entries may live. */
public enum class LanguageType {
    /** Attested, with entries in the main namespace. */
    REGULAR,

    /** Reconstructed, such as Proto-Indo-European, with entries in the Reconstruction namespace. */
    RECONSTRUCTED,

    /** Constructed and kept in the Appendix namespace, such as Klingon. */
    APPENDIX_CONSTRUCTED,

    /** A variety named in etymologies only, such as Limousin, which has no entries of its own. */
    ETYMOLOGY_ONLY,
}

/**
 * One language as English Wiktionary's `Module:languages` and `Module:etymology languages` describe it.
 *
 * The languages Wiktionary has are looked up on the type itself, from the copy built into the library:
 * ```
 * val english = Language.of("en")
 * Language["xx"]                       // null: no such language
 * Language.named("Old English")
 * "ang" in Language
 * english.family?.name                 // "West Germanic"
 * english.ancestors.map { it.name }
 * english.scripts.first().name           // "Latin"
 * Language.of("de").varieties.map { it.name }    // [Alsatian German, American German, …]
 * Language.of("en-ear").full.name      // "English"
 * Language.of("sh").detectScript("Москва")      // Cyrillic (Cyrl)
 * ```
 *
 * The links, [family], [ancestors], [parent] and [full], lead to the languages and families of the [Catalog]
 * the language came from, the built-in one unless another was read. A language is made only by a catalog, so
 * every link it has leads somewhere the catalog knows of or nowhere.
 *
 * @property code its code, such as `en`, `ine-pro` or the etymology-only `oc-lim`.
 * @property name its name as Wiktionary writes it, which is the heading of its section in an entry.
 * @property type what kind of language it is.
 * @property wikidata its Wikidata item, such as `Q1860`, or `null`.
 * @param familyCode the code of the family [family] leads to.
 * @param scriptCodes the codes of the scripts [scripts] lead to.
 * @param ancestorCodes the codes of the languages [ancestors] lead to.
 * @property aliases other names it goes by.
 * @param parentCode the code of the language [parent] leads to.
 * @property varieties the varieties of it Wiktionary names, such as dialects, which have no code of their
 *   own.
 */
public class Language
internal constructor(
    public val code: String,
    public val name: String,
    public val type: LanguageType = LanguageType.REGULAR,
    public val wikidata: String? = null,
    internal val familyCode: String? = null,
    internal val scriptCodes: List<String> = emptyList(),
    internal val ancestorCodes: List<String> = emptyList(),
    public val aliases: List<String> = emptyList(),
    internal val parentCode: String? = null,
    public val varieties: List<Variety> = emptyList(),
) {
    /** The catalog this language was read into, whose languages and families its links lead to. */
    internal var catalog: Catalog? = null

    private val links: Catalog
        get() = catalog ?: Catalog.BUNDLED

    /** Whether it is named in etymologies only, with no section of its own in an entry. */
    public val etymologyOnly: Boolean
        get() = type == LanguageType.ETYMOLOGY_ONLY

    /**
     * The family it belongs to, or `null`; an etymology-only variety with none of its own has that of the
     * nearest of its parents that has one, as American English has English's through North American English.
     */
    public val family: LanguageFamily?
        get() {
            var language: Language? = this
            val seen = mutableSetOf<String>()
            while (language != null && seen.add(language.code)) {
                language.familyCode?.let {
                    return links.family(it)
                }
                language = language.parent
            }
            return null
        }

    /** The scripts it is written in, most usual first, those the catalog knows. */
    public val scripts: List<Script>
        get() = scriptCodes.mapNotNull { links.script(it) }

    /** The languages it descends from, those the catalog knows. */
    public val ancestors: List<Language>
        get() = ancestorCodes.mapNotNull { links[it] }

    /** The language an etymology-only variety belongs to, or `null` for any other. */
    public val parent: Language?
        get() = parentCode?.let { links[it] }

    /** The full language this stands for: itself, or for an etymology-only variety the one it belongs to. */
    public val full: Language
        get() {
            var language = this
            val seen = mutableSetOf(code)
            while (language.etymologyOnly) {
                val parent = language.parent?.takeIf { seen.add(it.code) } ?: return language
                language = parent
            }
            return language
        }

    /**
     * The script [text] is written in, among this language's [scripts], as Wiktionary's `findBestScript`
     * finds it, or `null` when none of them has any of its characters.
     *
     * Spaces and ASCII punctuation are left out, and the scripts are tried in order: the first that has every
     * character wins, or else the one with the most of them, the earlier on a tie. Chinese and the other
     * languages written in Han give Traditional or Simplified Han when more of the characters mark one than
     * the other, as Unihan tells them apart, and Han when as many mark each. A language written in every
     * script, as Translingual is, finds it as [Catalog.detectScript] does, and an etymology-only variety with
     * no scripts of its own uses its language's. Pass plain text: wiki markup counts as characters.
     *
     * ```
     * Language.of("sh").detectScript("Москва")     // Cyrillic (Cyrl)
     * Language.of("zh").detectScript("語言")        // Traditional Han (Hant)
     * Language.of("ja").detectScript("ひらがな")    // Japanese (Jpan)
     * Language.of("en").detectScript("123")        // null
     * ```
     */
    public fun detectScript(text: String): Script? = links.detectScript(this, text)

    /** A copy for another catalog, linked to nothing yet. */
    internal fun copy(): Language =
        Language(
            code,
            name,
            type,
            wikidata,
            familyCode,
            scriptCodes,
            ancestorCodes,
            aliases,
            parentCode,
            varieties,
        )

    override fun equals(other: Any?): Boolean =
        other is Language &&
            code == other.code &&
            name == other.name &&
            type == other.type &&
            wikidata == other.wikidata &&
            familyCode == other.familyCode &&
            scriptCodes == other.scriptCodes &&
            ancestorCodes == other.ancestorCodes &&
            aliases == other.aliases &&
            parentCode == other.parentCode &&
            varieties == other.varieties

    override fun hashCode(): Int = code.hashCode() * HASH + name.hashCode()

    /** Its name and code, as `English (en)`. */
    override fun toString(): String = "$name ($code)"

    /** The languages English Wiktionary has, as the library was built with them. */
    public companion object {
        /** The language with [code], or `null` when Wiktionary has none. */
        public operator fun get(code: String): Language? = Catalog.BUNDLED[code]

        /**
         * The language with [code].
         *
         * @throws IllegalArgumentException when Wiktionary has none.
         */
        public fun of(code: String): Language =
            Catalog.BUNDLED[code] ?: throw IllegalArgumentException("Wiktionary has no language $code")

        /** The language called [name], as its section in an entry is headed, or `null`. */
        public fun named(name: String): Language? = Catalog.BUNDLED.named(name)

        /** Whether Wiktionary has a language with [code]. */
        public operator fun contains(code: String): Boolean = code in Catalog.BUNDLED

        /** Every language and etymology-only variety. */
        public val all: Collection<Language>
            get() = Catalog.BUNDLED.languages
    }
}

/**
 * A variety of a language Wiktionary names without giving it a code, such as a dialect.
 *
 * @property name its name, such as `Bavarian German`.
 * @property otherNames other names it goes by.
 */
public class Variety
internal constructor(public val name: String, public val otherNames: List<String> = emptyList()) {
    override fun equals(other: Any?): Boolean =
        other is Variety && name == other.name && otherNames == other.otherNames

    override fun hashCode(): Int = name.hashCode() * HASH + otherNames.hashCode()

    /** Its name, and its other names after it. */
    override fun toString(): String =
        if (otherNames.isEmpty()) name else "$name (${otherNames.joinToString()})"
}

/**
 * One language family as English Wiktionary's `Module:families` describes it.
 *
 * ```
 * val indoEuropean = LanguageFamily.of("ine")
 * indoEuropean.protoLanguage?.name     // "Proto-Indo-European"
 * LanguageFamily.of("gmw").parent?.name
 * ```
 *
 * @property code its code, such as `ine` or `gmw-fri`.
 * @property name its name, such as `Indo-European`.
 * @property wikidata its Wikidata item, or `null`.
 * @param parentCode the code of the family [parent] leads to.
 * @param protoLanguageCode the code of the language [protoLanguage] leads to, when Wiktionary names one.
 * @property aliases other names it goes by.
 */
public class LanguageFamily
internal constructor(
    public val code: String,
    public val name: String,
    public val wikidata: String? = null,
    internal val parentCode: String? = null,
    internal val protoLanguageCode: String? = null,
    public val aliases: List<String> = emptyList(),
) {
    /** The catalog this family was read into, whose languages and families its links lead to. */
    internal var catalog: Catalog? = null

    private val links: Catalog
        get() = catalog ?: Catalog.BUNDLED

    /** The family it belongs to, or `null`. */
    public val parent: LanguageFamily?
        get() = parentCode?.let { links.family(it) }

    /**
     * The language it descends from: the one Wiktionary names, or else `<code>-pro` when there is such a
     * language, as for `ine` and `ine-pro`. `null` when it has none.
     */
    public val protoLanguage: Language?
        get() = links[protoLanguageCode ?: "$code-pro"]

    /** The languages in it directly, not those of the families under it. */
    public val languages: List<Language>
        get() = links.inFamily(code)

    /** A copy for another catalog, linked to nothing yet. */
    internal fun copy(): LanguageFamily =
        LanguageFamily(code, name, wikidata, parentCode, protoLanguageCode, aliases)

    override fun equals(other: Any?): Boolean =
        other is LanguageFamily &&
            code == other.code &&
            name == other.name &&
            wikidata == other.wikidata &&
            parentCode == other.parentCode &&
            protoLanguageCode == other.protoLanguageCode &&
            aliases == other.aliases

    override fun hashCode(): Int = code.hashCode() * HASH + name.hashCode()

    /** Its name and code, as `Indo-European (ine)`. */
    override fun toString(): String = "$name ($code)"

    /** The language families English Wiktionary has, as the library was built with them. */
    public companion object {
        /** The family with [code], or `null` when Wiktionary has none. */
        public operator fun get(code: String): LanguageFamily? = Catalog.BUNDLED.family(code)

        /**
         * The family with [code].
         *
         * @throws IllegalArgumentException when Wiktionary has none.
         */
        public fun of(code: String): LanguageFamily =
            Catalog.BUNDLED.family(code) ?: throw IllegalArgumentException("Wiktionary has no family $code")

        /** Whether Wiktionary has a family with [code]. */
        public operator fun contains(code: String): Boolean = Catalog.BUNDLED.family(code) != null

        /** Every family. */
        public val all: Collection<LanguageFamily>
            get() = Catalog.BUNDLED.families
    }
}

private const val HASH = 31
