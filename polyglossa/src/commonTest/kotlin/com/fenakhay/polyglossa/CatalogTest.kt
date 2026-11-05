package com.fenakhay.polyglossa

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** A catalog read from what `Module:JSON data` exports, and the links between its languages. */
class CatalogTest {

    private val languages =
        """{"en":{"1":"English","2":1860,"3":"gmw-ang","4":"Latn,Brai","ancestors":"en-ear","type":"regular"},
           "enm":{"1":"Middle English","3":"gmw-ang","type":"regular"},
           "ine-pro":{"1":"Proto-Indo-European","2":37178,"3":"ine","4":"Latn","type":"reconstructed"},
           "art-top":{"1":"Toki Pona","type":"appendix-constructed"},"broken":null}"""
    private val aliases = """{"en":["Modern English","New English"],"ine-pro":null}"""
    private val etymology =
        """{"en-ear":{"1":"Early Modern English","2":1472196,"parent":"en","ancestors":"enm"},
           "oc-lim":{"1":"Limousin","parent":"oc","aliases":["Lemosin"]}}"""
    private val scripts =
        """{"Latn":{"1":"Latin","2":8229,"3":"alphabet","aliases":["Roman"]},"Brai":{"1":"Braille"},
           "Latf":{"1":"Fraktur","parent":"Latn"},"Arab":{"1":"Arabic","direction":"rtl"},
           "Deva":{"1":{"default":"Devanagari","en":"Balbodh"},"3":"abugida"}}"""
    private val varieties = """{"en":["Polari",["Yinglish","Yiddish English"]],"enm":null}"""
    private val families =
        """{"ine":{"1":"Indo-European","2":19860,"aliases":["Indo-Germanic"]},
           "gmw-ang":{"1":"Anglo-Frisian","3":"ine"}}"""

    private val catalog = Catalog.fromExports(languages, aliases, etymology, families, scripts, varieties)

    @Test
    fun `languages varieties and families are read with their fields`() {
        catalog["en"] shouldBe
            Language(
                code = "en",
                name = "English",
                wikidata = "Q1860",
                familyCode = "gmw-ang",
                scriptCodes = listOf("Latn", "Brai"),
                ancestorCodes = listOf("en-ear"),
                aliases = listOf("Modern English", "New English"),
                varieties = listOf(Variety("Polari"), Variety("Yinglish", listOf("Yiddish English"))),
            )
        catalog["ine-pro"]?.type shouldBe LanguageType.RECONSTRUCTED
        catalog["art-top"]?.type shouldBe LanguageType.APPENDIX_CONSTRUCTED
        catalog["broken"] shouldBe null
        ("oc-lim" in catalog) shouldBe true
        catalog["oc-lim"]?.aliases shouldBe listOf("Lemosin")
        catalog.languages.size shouldBe 6
    }

    @Test
    fun `links lead to languages and families in the same catalog`() {
        val english = checkNotNull(catalog["en"])

        english.family?.name shouldBe "Anglo-Frisian"
        english.family?.parent?.name shouldBe "Indo-European"
        english.family?.parent?.protoLanguage?.name shouldBe "Proto-Indo-European"
        english.ancestors.map { it.name } shouldBe listOf("Early Modern English")
        english.ancestors.single().ancestors.map { it.code } shouldBe listOf("enm")
        checkNotNull(catalog["en-ear"]).full.code shouldBe "en"
        checkNotNull(catalog["en-ear"]).family?.code shouldBe "gmw-ang"
        checkNotNull(catalog["oc-lim"]).full.code shouldBe "oc-lim"
        catalog.family("gmw-ang")?.languages?.map { it.code }?.sorted() shouldBe listOf("en", "enm")
        catalog.family("gmw-ang")?.protoLanguage shouldBe null
    }

    @Test
    fun `scripts are read with their links and a language's scripts are the objects`() {
        checkNotNull(catalog["en"]).scripts.map { it.name } shouldBe listOf("Latin", "Braille")
        catalog.script("Latn")?.aliases shouldBe listOf("Roman")
        catalog.script("Latn")?.wikidata shouldBe "Q8229"
        catalog.script("Latn")?.system shouldBe "alphabet"
        catalog.script("Latf")?.parent?.code shouldBe "Latn"
        catalog.script("Arab")?.direction shouldBe ScriptDirection.RIGHT_TO_LEFT
        catalog.scriptNamed("Latin")?.code shouldBe "Latn"
        val devanagari = checkNotNull(catalog.script("Deva"))
        devanagari.name shouldBe "Devanagari"
        devanagari.nameFor(checkNotNull(catalog["en"])) shouldBe "Balbodh"
        devanagari.nameFor(checkNotNull(catalog["enm"])) shouldBe "Devanagari"
        devanagari.localNames shouldBe mapOf(checkNotNull(catalog["en"]) to "Balbodh")
        Catalog.fromTsv(catalog.toTsv()).scripts.sortedBy { it.code } shouldBe
            catalog.scripts.sortedBy { it.code }
    }

    @Test
    fun `names find full languages before varieties`() {
        val both =
            Catalog(
                listOf(
                    Language("x", "Same"),
                    Language("x-ety", "Same", LanguageType.ETYMOLOGY_ONLY, parentCode = "x"),
                )
            )

        both.named("Same")?.code shouldBe "x"
        catalog.named(" English ")?.code shouldBe "en"
    }

    @Test
    fun `a catalog written as tab-separated lines reads back the same with its escapes`() {
        val odd =
            Catalog(
                listOf(
                    Language(
                        "x",
                        "Tab\tand; semi\\colon",
                        aliases = listOf("a;b", "c\nd"),
                        varieties = listOf(Variety("bar|name", listOf("other;one", "x"))),
                    )
                )
            )

        Catalog.fromTsv(odd.toTsv())["x"] shouldBe odd["x"]
        val copy = Catalog.fromTsv(catalog.toTsv())
        copy.languages.sortedBy { it.code } shouldBe catalog.languages.sortedBy { it.code }
        copy.families.sortedBy { it.code } shouldBe catalog.families.sortedBy { it.code }
        assertFailsWith<IllegalArgumentException> { Catalog.fromTsv("{}") }
        val dated =
            Catalog.fromExports(
                listOf(languages, aliases, etymology, families, scripts, varieties),
                "2026-11-05",
            )
        dated.date shouldBe "2026-11-05"
        Catalog.fromTsv(dated.toTsv()).date shouldBe "2026-11-05"
        Catalog.fromTsv(catalog.toTsv()).date shouldBe null
        assertFailsWith<IllegalArgumentException> {
            Catalog.fromExports("[", aliases, etymology, families, scripts, varieties)
        }
    }

    @Test
    fun `an export that is the module's error is refused with what it said`() {
        val error =
            "<strong class=\"error\"><span class=\"scribunto-error\">" +
                "Lua error in Module:JSON_data: no schema given for key &#039;Latn&#039;</span></strong>"

        assertFailsWith<IllegalStateException> {
                Catalog.fromExports(error, aliases, etymology, families, scripts, varieties)
            }
            .message shouldContain "no schema given for key 'Latn'"
        Catalog.EXPORTS.size shouldBe 6
    }
}
