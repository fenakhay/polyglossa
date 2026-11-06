package com.fenakhay.polyglossa.tools

import com.fenakhay.polyglossa.Catalog
import com.fenakhay.polyglossa.Language
import com.fenakhay.polyglossa.LanguageFamily
import com.fenakhay.polyglossa.Script
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * What every implementation of Polyglossa must answer from the same `catalog.tsv`: a sample of languages,
 * families and scripts with every field and link, lookups by name, codes that must miss, the counts, and the
 * script detected for some texts, in a language or in none.
 *
 * Written from the Kotlin library whenever the catalog is, so the Swift and Python packages are held to what
 * it reads.
 */
internal object Spec {
    private val LANGUAGES =
        listOf("en", "en-ear", "en-US", "ang", "ine-pro", "art-top", "de", "ar", "xgm", "oc-lim", "mul")
    private val FAMILIES = listOf("ine", "gmw-ang", "gmw", "sem-arb")
    private val SCRIPTS = listOf("Latn", "Arab", "Aran", "Hans", "Deva", "Mong", "Hani")
    private val NAMES = listOf("English", "Old English", "Proto-Indo-European", "Arabic")
    private val MISSING = listOf("xx-none", "", "EN")
    private val DETECTIONS =
        listOf(
            "sh" to "Moskva",
            "sh" to "Москва",
            "sh" to "Москва Moskva",
            "ru" to "«Москва»!",
            "ru" to "Moskva",
            "en" to "123",
            "en" to "",
            "en-ear" to "word",
            "ur" to "کتاب",
            "pa" to "پنجاب",
            "zh" to "語言",
            "zh" to "语言",
            "zh" to "中文",
            "zh" to "中abc",
            "ja" to "ひらがな",
            "ko" to "한국어",
            "grc" to "λόγος",
            "el" to "λόγος",
            "got" to "𐌲𐌿𐍄𐌰",
            "mul" to "ab12345",
            null to "Ελληνικά",
            null to "東京",
            null to "ひらがな漢字",
            null to "ひらがな",
            null to "한국어",
            null to "アbc",
            null to "Москва 2026",
            null to "𞤀𞤁",
            null to "123",
        )

    private val pretty = Json { prettyPrint = true }

    fun write(catalog: Catalog): String =
        pretty.encodeToString(
            JsonElement.serializer(),
            buildJsonObject {
                put("date", text(catalog.date))
                put(
                    "counts",
                    buildJsonObject {
                        put("languages", JsonPrimitive(catalog.languages.size))
                        put("families", JsonPrimitive(catalog.families.size))
                        put("scripts", JsonPrimitive(catalog.scripts.size))
                    },
                )
                put("languages", JsonArray(LANGUAGES.mapNotNull { catalog[it] }.map(::language)))
                put("families", JsonArray(FAMILIES.mapNotNull { catalog.family(it) }.map(::family)))
                put("scripts", JsonArray(SCRIPTS.mapNotNull { catalog.script(it) }.map(::script)))
                put(
                    "named",
                    buildJsonObject { NAMES.forEach { name -> put(name, text(catalog.named(name)?.code)) } },
                )
                put("missing", strings(MISSING.filter { catalog[it] == null }))
                put(
                    "detections",
                    JsonArray(
                        DETECTIONS.map { [code, text] ->
                            val language = code?.let { catalog[it] }
                            val script =
                                if (language != null) language.detectScript(text)
                                else catalog.detectScript(text)
                            buildJsonObject {
                                put("language", text(code))
                                put("text", JsonPrimitive(text))
                                put("script", text(script?.code))
                            }
                        }
                    ),
                )
            },
        ) + "\n"

    private fun language(language: Language): JsonObject = buildJsonObject {
        put("code", JsonPrimitive(language.code))
        put("name", JsonPrimitive(language.name))
        put("type", JsonPrimitive(language.type.word))
        put("wikidata", text(language.wikidata))
        put("family", text(language.family?.code))
        put("scripts", strings(language.scripts.map { it.code }))
        put("ancestors", strings(language.ancestors.map { it.code }))
        put("aliases", strings(language.aliases))
        put("parent", text(language.parent?.code))
        put("full", JsonPrimitive(language.full.code))
        put(
            "varieties",
            buildJsonArray {
                language.varieties.forEach { variety ->
                    add(strings(listOf(variety.name) + variety.otherNames))
                }
            },
        )
    }

    private fun family(family: LanguageFamily): JsonObject = buildJsonObject {
        put("code", JsonPrimitive(family.code))
        put("name", JsonPrimitive(family.name))
        put("wikidata", text(family.wikidata))
        put("parent", text(family.parent?.code))
        put("protoLanguage", text(family.protoLanguage?.code))
        put("aliases", strings(family.aliases))
        put("languages", strings(family.languages.map { it.code }.sorted()))
    }

    private fun script(script: Script): JsonObject = buildJsonObject {
        put("code", JsonPrimitive(script.code))
        put("name", JsonPrimitive(script.name))
        put("wikidata", text(script.wikidata))
        put("system", text(script.system))
        put("aliases", strings(script.aliases))
        put("parent", text(script.parent?.code))
        put("direction", JsonPrimitive(script.direction.word))
        put("characters", JsonPrimitive(script.ranges.sumOf { it.count() }))
        put("ranges", JsonPrimitive(script.ranges.size))
        put("marks", JsonPrimitive(script.marks.sumOf { it.count() }))
        put(
            "localNames",
            buildJsonObject {
                script.localNames.entries
                    .sortedBy { it.key.code }
                    .forEach { [language, name] ->
                        put(language.code, JsonPrimitive(name))
                    }
            },
        )
    }

    private fun text(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

    private fun strings(values: List<String>): JsonArray = JsonArray(values.map(::JsonPrimitive))
}
