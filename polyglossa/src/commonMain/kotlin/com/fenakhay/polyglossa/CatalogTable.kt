package com.fenakhay.polyglossa

/**
 * The tab-separated form [Catalog.toTsv] writes: a header, `polyglossa`, the format and the day the data was
 * exported, then a language (`L`) or a family (`F`) to a line, sorted by code, so two copies differ line by
 * line where the data does and nothing is spelled out twice.
 *
 * A language is `L`, code, name, type (empty for regular, `r` reconstructed, `a` appendix-constructed, `e`
 * etymology-only), Wikidata item number, family, scripts, ancestors, aliases, parent and varieties, each a
 * name and its other names joined by `|`. A family is `F`, code, name, Wikidata item number, parent,
 * proto-language and aliases. A script is `S`, code, name, Wikidata item number, writing system, aliases,
 * parent, direction (empty for left to right), the names some languages give it, as `mr=Balbodh`, its
 * characters and its marks, both as ranges of hexadecimal code points such as `41-5A,61-7A,AA`. Codes are
 * joined by commas, names by semicolons; a tab, a line break, a backslash, a semicolon or a bar in a name is
 * escaped by a backslash. Empty columns at the end of a line are left off.
 */
internal object CatalogTable {
    const val HEADER: String = "polyglossa\t1"

    private const val LANGUAGE = "L"
    private const val FAMILY = "F"
    private const val SCRIPT = "S"
    private const val HEX = 16
    private val DIRECTIONS =
        mapOf(
            ScriptDirection.LEFT_TO_RIGHT to "",
            ScriptDirection.RIGHT_TO_LEFT to "rtl",
            ScriptDirection.VERTICAL_LEFT_TO_RIGHT to "vertical-ltr",
            ScriptDirection.VERTICAL_RIGHT_TO_LEFT to "vertical-rtl",
        )
    private val TYPES =
        mapOf(
            LanguageType.REGULAR to "",
            LanguageType.RECONSTRUCTED to "r",
            LanguageType.APPENDIX_CONSTRUCTED to "a",
            LanguageType.ETYMOLOGY_ONLY to "e",
        )
    private val TYPE_OF = TYPES.entries.associate { [type, mark] -> mark to type }

    fun write(
        languages: Collection<Language>,
        families: Collection<LanguageFamily>,
        scripts: Collection<Script>,
        date: String?,
    ): String = buildString {
        appendLine(if (date == null) HEADER else "$HEADER\t$date")
        for (language in languages.sortedBy { it.code }) appendLine(row(language))
        for (family in families.sortedBy { it.code }) appendLine(row(family))
        for (script in scripts.sortedBy { it.code }) appendLine(row(script))
    }

    fun read(text: String): Catalog {
        val lines = text.lines()
        val header = lines.firstOrNull().orEmpty()
        require(header == HEADER || header.startsWith("$HEADER\t")) { "not a catalog Polyglossa wrote" }
        val date = header.removePrefix(HEADER).removePrefix("\t").ifEmpty { null }
        val languages = mutableListOf<Language>()
        val families = mutableListOf<LanguageFamily>()
        val scripts = mutableListOf<Script>()
        for (line in lines.drop(1)) {
            if (line.isEmpty()) continue
            val cells = Cells(line.split('\t'))
            when (cells.next()) {
                LANGUAGE -> languages += language(cells)
                FAMILY -> families += family(cells)
                SCRIPT -> scripts += script(cells)
                else -> throw IllegalArgumentException("not a language, a family or a script: $line")
            }
        }
        return Catalog(languages, families, scripts, date)
    }

    private fun row(language: Language): String =
        cells(
            LANGUAGE,
            language.code,
            escape(language.name),
            TYPES.getValue(language.type),
            language.wikidata?.removePrefix("Q").orEmpty(),
            language.familyCode.orEmpty(),
            language.scriptCodes.joinToString(","),
            language.ancestorCodes.joinToString(","),
            language.aliases.joinToString(";") { escape(it) },
            language.parentCode.orEmpty(),
            language.varieties.joinToString(";") { variety ->
                (listOf(variety.name) + variety.otherNames).joinToString("|") { escape(it) }
            },
        )

    private fun row(family: LanguageFamily): String =
        cells(
            FAMILY,
            family.code,
            escape(family.name),
            family.wikidata?.removePrefix("Q").orEmpty(),
            family.parentCode.orEmpty(),
            family.protoLanguageCode.orEmpty(),
            family.aliases.joinToString(";") { escape(it) },
        )

    private fun row(script: Script): String =
        cells(
            SCRIPT,
            script.code,
            escape(script.name),
            script.wikidata?.removePrefix("Q").orEmpty(),
            escape(script.system.orEmpty()),
            script.aliases.joinToString(";") { escape(it) },
            script.parentCode.orEmpty(),
            DIRECTIONS.getValue(script.direction),
            script.localNameCodes.entries
                .sortedBy { it.key }
                .joinToString(";") { [language, name] -> "$language=${escape(name)}" },
            hex(script.ranges),
            hex(script.marks),
        )

    private fun cells(vararg values: String): String = values.joinToString("\t").trimEnd('\t')

    /** The cells of one line, read in the order [row] writes them; one left off the end reads as empty. */
    private class Cells(private val cells: List<String>) {
        private var at = 0

        fun next(): String = cells.getOrElse(at++) { "" }
    }

    private fun language(cells: Cells): Language {
        val code = cells.next()
        val name = unescape(cells.next())
        val mark = cells.next()
        return Language(
            code = code,
            name = name,
            type = TYPE_OF[mark] ?: throw IllegalArgumentException("no language type $mark"),
            wikidata = cells.next().ifEmpty { null }?.let { "Q$it" },
            familyCode = cells.next().ifEmpty { null },
            scriptCodes = codes(cells.next()),
            ancestorCodes = codes(cells.next()),
            aliases = names(cells.next()),
            parentCode = cells.next().ifEmpty { null },
            varieties =
                split(cells.next(), ';').map { variety ->
                    val names = split(variety, '|').map(::unescape)
                    Variety(names.first(), names.drop(1))
                },
        )
    }

    private fun family(cells: Cells): LanguageFamily =
        LanguageFamily(
            code = cells.next(),
            name = unescape(cells.next()),
            wikidata = cells.next().ifEmpty { null }?.let { "Q$it" },
            parentCode = cells.next().ifEmpty { null },
            protoLanguageCode = cells.next().ifEmpty { null },
            aliases = names(cells.next()),
        )

    private fun script(cells: Cells): Script =
        Script(
            code = cells.next(),
            name = unescape(cells.next()),
            wikidata = cells.next().ifEmpty { null }?.let { "Q$it" },
            system = unescape(cells.next()).ifEmpty { null },
            aliases = names(cells.next()),
            parentCode = cells.next().ifEmpty { null },
            direction = Catalog.direction(cells.next()),
            localNameCodes =
                names(cells.next()).associate { it.substringBefore('=') to it.substringAfter('=') },
            ranges = ranges(cells.next()),
            marks = ranges(cells.next()),
        )

    /** [ranges] as [ranges] reads them back: `41-5A,AA`. */
    private fun hex(ranges: List<IntRange>): String =
        ranges.joinToString(",") { range ->
            val first = range.first.toString(HEX).uppercase()
            if (range.first == range.last) first else "$first-${range.last.toString(HEX).uppercase()}"
        }

    private fun ranges(cell: String): List<IntRange> =
        codes(cell).map { range ->
            val first = range.substringBefore('-').toInt(HEX)
            first..(range.substringAfter('-', "").ifEmpty { null }?.toInt(HEX) ?: first)
        }

    private fun codes(cell: String): List<String> = if (cell.isEmpty()) emptyList() else cell.split(',')

    /** The names in [cell], split at each semicolon that is not escaped, each unescaped. */
    private fun names(cell: String): List<String> = split(cell, ';').map(::unescape)

    /**
     * [cell] split at each [separator] that is not escaped, the pieces left escaped; none for an empty cell.
     */
    private fun split(cell: String, separator: Char): List<String> {
        if (cell.isEmpty()) return emptyList()
        val pieces = mutableListOf<String>()
        var start = 0
        var at = 0
        while (at < cell.length) {
            when (cell[at]) {
                '\\' -> at++
                separator -> {
                    pieces += cell.substring(start, at)
                    start = at + 1
                }
            }
            at++
        }
        pieces += cell.substring(start)
        return pieces
    }

    private fun escape(text: String): String =
        text
            .replace("\\", "\\\\")
            .replace("\t", "\\t")
            .replace("\n", "\\n")
            .replace(";", "\\;")
            .replace("|", "\\|")

    /** [text] with the escapes [escape] writes undone. */
    private fun unescape(text: String): String {
        if ('\\' !in text) return text
        return buildString {
            var at = 0
            while (at < text.length) {
                val char = text[at]
                if (char == '\\' && at + 1 < text.length) {
                    when (val next = text[at + 1]) {
                        't' -> append('\t')
                        'n' -> append('\n')
                        else -> append(next)
                    }
                    at += 2
                } else {
                    append(char)
                    at++
                }
            }
        }
    }
}
