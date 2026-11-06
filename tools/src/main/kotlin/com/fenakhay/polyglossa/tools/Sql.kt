package com.fenakhay.polyglossa.tools

import com.fenakhay.polyglossa.Catalog

/**
 * The catalog as SQL: a schema and the rows to fill it, in plain SQL that SQLite, PostgreSQL and MySQL all
 * read. `sqlite3 polyglossa.sqlite < polyglossa.sql` makes a database of it.
 *
 * Every list a language, a family or a script holds is a table of its own with a `position`, so its order is
 * kept: a language's scripts, most usual first, are `language_scripts` ordered by `position`. A script's
 * characters are `script_ranges`, each a first and a last code point, so the scripts that hold U+0416 are
 * `SELECT script FROM script_ranges WHERE 1046 BETWEEN first AND last`; `script_marks` holds, the same way,
 * the characters that tell Traditional from Simplified Han.
 */
internal object Sql {
    private const val ROWS_PER_INSERT = 400

    private val SCHEMA =
        """
        CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE languages (
            code TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            type TEXT NOT NULL,
            wikidata TEXT,
            family TEXT,
            parent TEXT
        );
        CREATE TABLE language_scripts (language TEXT NOT NULL, position INTEGER NOT NULL, script TEXT NOT NULL,
            PRIMARY KEY (language, position));
        CREATE TABLE language_ancestors (language TEXT NOT NULL, position INTEGER NOT NULL, ancestor TEXT NOT NULL,
            PRIMARY KEY (language, position));
        CREATE TABLE language_aliases (language TEXT NOT NULL, position INTEGER NOT NULL, alias TEXT NOT NULL,
            PRIMARY KEY (language, position));
        CREATE TABLE varieties (language TEXT NOT NULL, position INTEGER NOT NULL, name TEXT NOT NULL,
            PRIMARY KEY (language, position));
        CREATE TABLE variety_other_names (language TEXT NOT NULL, variety INTEGER NOT NULL,
            position INTEGER NOT NULL, name TEXT NOT NULL, PRIMARY KEY (language, variety, position));
        CREATE TABLE families (
            code TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            wikidata TEXT,
            parent TEXT,
            proto_language TEXT
        );
        CREATE TABLE family_aliases (family TEXT NOT NULL, position INTEGER NOT NULL, alias TEXT NOT NULL,
            PRIMARY KEY (family, position));
        CREATE TABLE scripts (
            code TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            wikidata TEXT,
            system TEXT,
            parent TEXT,
            direction TEXT NOT NULL
        );
        CREATE TABLE script_aliases (script TEXT NOT NULL, position INTEGER NOT NULL, alias TEXT NOT NULL,
            PRIMARY KEY (script, position));
        CREATE TABLE script_local_names (script TEXT NOT NULL, language TEXT NOT NULL, name TEXT NOT NULL,
            PRIMARY KEY (script, language));
        CREATE TABLE script_ranges (script TEXT NOT NULL, first INTEGER NOT NULL, last INTEGER NOT NULL,
            PRIMARY KEY (script, first));
        CREATE TABLE script_marks (script TEXT NOT NULL, first INTEGER NOT NULL, last INTEGER NOT NULL,
            PRIMARY KEY (script, first));
        CREATE INDEX languages_by_name ON languages (name);
        CREATE INDEX languages_by_family ON languages (family);
        CREATE INDEX script_ranges_by_first ON script_ranges (first);
        """
            .trimIndent()

    fun write(catalog: Catalog): String = buildString {
        appendLine("BEGIN;")
        appendLine(SCHEMA)
        val tables = Tables()
        tables.add("metadata", listOf("format", "1"))
        catalog.date?.let { tables.add("metadata", listOf("date", it)) }
        tables.addLanguages(catalog)
        tables.addFamilies(catalog)
        tables.addScripts(catalog)
        tables.write(this)
        appendLine("COMMIT;")
    }

    private fun Tables.addLanguages(catalog: Catalog) {
        for (language in catalog.languages.sortedBy { it.code }) {
            add(
                "languages",
                listOf(
                    language.code,
                    language.name,
                    language.type.word,
                    language.wikidata,
                    language.family?.code,
                    language.parent?.code,
                ),
            )
            language.scripts.forEachIndexed { at, script ->
                add("language_scripts", listOf(language.code, at, script.code))
            }
            language.ancestors.forEachIndexed { at, ancestor ->
                add("language_ancestors", listOf(language.code, at, ancestor.code))
            }
            language.aliases.forEachIndexed { at, alias ->
                add("language_aliases", listOf(language.code, at, alias))
            }
            language.varieties.forEachIndexed { at, variety ->
                add("varieties", listOf(language.code, at, variety.name))
                variety.otherNames.forEachIndexed { other, name ->
                    add("variety_other_names", listOf(language.code, at, other, name))
                }
            }
        }
    }

    private fun Tables.addFamilies(catalog: Catalog) {
        for (family in catalog.families.sortedBy { it.code }) {
            add(
                "families",
                listOf(
                    family.code,
                    family.name,
                    family.wikidata,
                    family.parent?.code,
                    family.protoLanguage?.code,
                ),
            )
            family.aliases.forEachIndexed { at, alias ->
                add("family_aliases", listOf(family.code, at, alias))
            }
        }
    }

    private fun Tables.addScripts(catalog: Catalog) {
        for (script in catalog.scripts.sortedBy { it.code }) {
            add(
                "scripts",
                listOf(
                    script.code,
                    script.name,
                    script.wikidata,
                    script.system,
                    script.parent?.code,
                    script.direction.word,
                ),
            )
            script.aliases.forEachIndexed { at, alias ->
                add("script_aliases", listOf(script.code, at, alias))
            }
            script.localNames.entries
                .sortedBy { it.key.code }
                .forEach { [language, name] ->
                    add("script_local_names", listOf(script.code, language.code, name))
                }
            script.ranges.forEach { add("script_ranges", listOf(script.code, it.first, it.last)) }
            script.marks.forEach { add("script_marks", listOf(script.code, it.first, it.last)) }
        }
    }

    /** The rows of each table, in the order they were added, written as inserts of [ROWS_PER_INSERT] rows. */
    private class Tables {
        private val rows = linkedMapOf<String, MutableList<List<Any?>>>()

        fun add(table: String, row: List<Any?>) {
            rows.getOrPut(table) { mutableListOf() } += row
        }

        fun write(out: StringBuilder) {
            for ([table, values] in rows) {
                for (chunk in values.chunked(ROWS_PER_INSERT)) {
                    out.append("INSERT INTO ").append(table).append(" VALUES\n")
                    out.append(
                        chunk.joinToString(",\n") { row -> row.joinToString(", ", "(", ")") { literal(it) } }
                    )
                    out.append(";\n")
                }
            }
        }
    }

    /** [value] as an SQL literal: a number as it is, text quoted with its quotes doubled, `NULL` for none. */
    internal fun literal(value: Any?): String =
        when (value) {
            null -> "NULL"
            is Int -> value.toString()
            else -> "'" + value.toString().replace("'", "''") + "'"
        }
}
