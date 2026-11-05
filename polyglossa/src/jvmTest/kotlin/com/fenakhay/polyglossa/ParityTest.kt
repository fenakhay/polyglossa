package com.fenakhay.polyglossa

import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.test.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The bundled catalog answers as `spec/expectations.json` says, as the Swift and Python packages must too.
 */
class ParityTest {

    private val spec = Json.parseToJsonElement(File("../spec/expectations.json").readText()).jsonObject
    private val catalog = Catalog.BUNDLED

    private fun JsonElement.text(): String? =
        (this as? JsonPrimitive)?.takeIf { this !is JsonNull }?.contentOrNull

    private fun JsonElement.texts(): List<String> = jsonArray.mapNotNull { it.text() }

    @Test
    fun `the counts the date the misses and the names are as the spec says`() {
        val counts = spec.getValue("counts").jsonObject
        catalog.date shouldBe spec.getValue("date").text()
        catalog.languages.size shouldBe counts.getValue("languages").jsonPrimitive.int
        catalog.families.size shouldBe counts.getValue("families").jsonPrimitive.int
        catalog.scripts.size shouldBe counts.getValue("scripts").jsonPrimitive.int
        spec.getValue("missing").texts().forEach { catalog[it] shouldBe null }
        spec.getValue("named").jsonObject.forEach { [name, code] ->
            catalog.named(name)?.code shouldBe code.text()
        }
    }

    @Test
    fun `every sampled language has the fields and links the spec gives it`() {
        for (expected in spec.getValue("languages").jsonArray.map { it.jsonObject }) {
            val language = checkNotNull(catalog[checkNotNull(expected.getValue("code").text())])
            language.name shouldBe expected.getValue("name").text()
            word(language.type) shouldBe expected.getValue("type").text()
            language.wikidata shouldBe expected.getValue("wikidata").text()
            language.family?.code shouldBe expected.getValue("family").text()
            language.scripts.map { it.code } shouldBe expected.getValue("scripts").texts()
            language.ancestors.map { it.code } shouldBe expected.getValue("ancestors").texts()
            language.aliases shouldBe expected.getValue("aliases").texts()
            language.parent?.code shouldBe expected.getValue("parent").text()
            language.full.code shouldBe expected.getValue("full").text()
            language.varieties.map { listOf(it.name) + it.otherNames } shouldBe
                expected.getValue("varieties").jsonArray.map { it.texts() }
        }
    }

    @Test
    fun `every sampled family and script has the fields and links the spec gives it`() {
        for (expected in spec.getValue("families").jsonArray.map { it.jsonObject }) {
            val family = checkNotNull(catalog.family(checkNotNull(expected.getValue("code").text())))
            family.name shouldBe expected.getValue("name").text()
            family.wikidata shouldBe expected.getValue("wikidata").text()
            family.parent?.code shouldBe expected.getValue("parent").text()
            family.protoLanguage?.code shouldBe expected.getValue("protoLanguage").text()
            family.aliases shouldBe expected.getValue("aliases").texts()
            family.languages.map { it.code }.sorted() shouldBe expected.getValue("languages").texts()
        }
        for (expected in spec.getValue("scripts").jsonArray.map { it.jsonObject }) {
            val script = checkNotNull(catalog.script(checkNotNull(expected.getValue("code").text())))
            script.name shouldBe expected.getValue("name").text()
            script.wikidata shouldBe expected.getValue("wikidata").text()
            script.system shouldBe expected.getValue("system").text()
            script.aliases shouldBe expected.getValue("aliases").texts()
            script.parent?.code shouldBe expected.getValue("parent").text()
            word(script.direction) shouldBe expected.getValue("direction").text()
            script.localNames.mapKeys { it.key.code } shouldBe
                (expected.getValue("localNames") as JsonObject).mapValues { it.value.text() }
        }
    }

    private fun word(type: LanguageType): String =
        when (type) {
            LanguageType.REGULAR -> "regular"
            LanguageType.RECONSTRUCTED -> "reconstructed"
            LanguageType.APPENDIX_CONSTRUCTED -> "appendix-constructed"
            LanguageType.ETYMOLOGY_ONLY -> "etymology-only"
        }

    private fun word(direction: ScriptDirection): String =
        when (direction) {
            ScriptDirection.LEFT_TO_RIGHT -> "ltr"
            ScriptDirection.RIGHT_TO_LEFT -> "rtl"
            ScriptDirection.VERTICAL_LEFT_TO_RIGHT -> "vertical-ltr"
            ScriptDirection.VERTICAL_RIGHT_TO_LEFT -> "vertical-rtl"
        }
}
