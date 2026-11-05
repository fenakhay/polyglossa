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
 * families and scripts with every field and link, lookups by name, codes that must miss, and the counts.
 *
 * Written from the Kotlin library whenever the catalog is, so the Swift and Python packages are held to what
 * it reads.
 */
internal object Spec {
    private val LANGUAGES =
        listOf("en", "en-ear", "ang", "ine-pro", "art-top", "de", "ar", "xgm", "oc-lim", "mul")
    private val FAMILIES = listOf("ine", "gmw-ang", "gmw", "sem-arb")
    private val SCRIPTS = listOf("Latn", "Arab", "Aran", "Hans", "Deva", "Mong", "Hani")
    private val NAMES = listOf("English", "Old English", "Proto-Indo-European", "Arabic")
    private val MISSING = listOf("xx-none", "", "EN")

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
