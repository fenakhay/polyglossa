package com.fenakhay.polyglossa

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** Telling which script a text is in, from the characters each script has. */
class ScriptDetectionTest {

    private val scripts =
        """{"Latn":{"1":"Latin","ranges":[97,122,65,90]},
           "Latf":{"1":"Fraktur","parent":"Latn","ranges":[65,90,97,122]},
           "Cyrl":{"1":"Cyrillic","ranges":[1024,1279]},
           "Grek":{"1":"Greek","ranges":[913,969]},
           "Polyt":{"1":"Polytonic Greek","parent":"Grek","ranges":[913,969,7936,8190]},
           "Hani":{"1":"Han","ranges":[19968,40959]},
           "Hant":{"1":"Traditional Han","parent":"Hani","ranges":[19968,40959]},
           "Hans":{"1":"Simplified Han","parent":"Hani","ranges":[19968,40959]},
           "Hira":{"1":"Hiragana","ranges":[12353,12438]},
           "Kana":{"1":"Katakana","ranges":[12449,12538]},
           "Jpan":{"1":"Japanese","ranges":[97,122,12353,12438,12449,12538,19968,40959]},
           "Adlm":{"1":"Adlam","ranges":[125184,125259]},
           "None":{"1":"No script"}}"""
    private val languages =
        """{"sh":{"1":"Serbo-Croatian","4":"Latn,Cyrl"},"ru":{"1":"Russian","4":"Cyrl"},
           "zh":{"1":"Chinese","4":"Hani,Hant,Hans,Latn"},"ja":{"1":"Japanese","4":"Jpan,Latn"},
           "grc":{"1":"Ancient Greek","4":"Polyt"},"en":{"1":"English","4":"Latn"},
           "mul":{"1":"Translingual","4":"Adlm,Cyrl,Grek,Hani,Hans,Hant,Hira,Jpan,Kana,Latf,Latn,None,Polyt"}}"""
    private val etymology = """{"en-ear":{"1":"Early Modern English","parent":"en"}}"""

    private val catalog =
        Catalog.fromExports(languages, "{}", etymology, "{}", scripts, "{}")
            .withScriptMarks(mapOf("Hant" to listOf(0x8A9E), "Hans" to listOf(0x8BED)))

    private fun detect(language: String, text: String): String? = catalog[language]?.detectScript(text)?.code

    @Test
    fun `ranges are read sorted and joined and survive the table`() {
        val latin = catalog.script("Latn")
        latin?.ranges shouldBe listOf(65..90, 97..122)
        Catalog.joined(listOf(5..9, 1..3, 4..4, 20..30, 25..26)) shouldBe listOf(1..9, 20..30)
        val again = Catalog.fromTsv(catalog.toTsv())
        again.script("Hant")?.marks shouldBe listOf(0x8A9E..0x8A9E)
        again.script("Adlm")?.ranges shouldBe listOf(0x1E900..0x1E94B)
        again.toTsv() shouldBe catalog.toTsv()
    }

    @Test
    fun `a script holds its characters to the edges of each range and astral ones too`() {
        val latin = catalog.script("Latn")!!
        (65 in latin) shouldBe true
        (90 in latin) shouldBe true
        (91 in latin) shouldBe false
        (64 in latin) shouldBe false
        catalog.script("Adlm")!!.count("𞤀𞤁 x") shouldBe 2
        latin.count("Ab1 é") shouldBe 2
    }

    @Test
    fun `the first script with every character wins or else the one with the most`() {
        detect("sh", "Moskva") shouldBe "Latn"
        detect("sh", "Москва") shouldBe "Cyrl"
        detect("sh", "Москва mo") shouldBe "Cyrl"
        detect("sh", "Mo Мо") shouldBe "Latn"
    }

    @Test
    fun `spaces and ASCII punctuation are left out and nothing left is no script`() {
        detect("ru", "«Москва»!") shouldBe "Cyrl"
        detect("ru", " -, ") shouldBe null
        detect("ru", "") shouldBe null
        detect("ru", "123") shouldBe null
        detect("ru", "Moskva") shouldBe null
    }

    @Test
    fun `Han is Traditional or Simplified as the marked characters lean`() {
        detect("zh", "語言") shouldBe "Hant"
        detect("zh", "语言") shouldBe "Hans"
        detect("zh", "語语") shouldBe "Hani"
        detect("zh", "言") shouldBe "Hani"
        detect("zh", "abc") shouldBe "Latn"
        detect("zh", "言abc") shouldBe "Hani"
    }

    @Test
    fun `a combined script and a variety are found by their characters`() {
        detect("ja", "ひらがな") shouldBe "Jpan"
        detect("grc", "λόγος") shouldBe "Polyt"
        detect("en-ear", "word") shouldBe "Latn"
    }

    @Test
    fun `a language written in every script and no language at all find the same`() {
        detect("mul", "Ελληνικά") shouldBe catalog.detectScript("Ελληνικά")?.code
        catalog.detectScript("αβγ")?.code shouldBe "Grek"
        catalog.detectScript("abc")?.code shouldBe "Latn"
        catalog.detectScript("言語")?.code shouldBe "Hani"
        catalog.detectScript("ひらがな言")?.code shouldBe "Jpan"
        catalog.detectScript("ひらがな")?.code shouldBe "Hira"
        catalog.detectScript("ab 12345") shouldBe null
        catalog.detectScript("abc 1") shouldBe catalog.script("Latn")
    }

    @Test
    fun `adding marks leaves the catalog it came from alone`() {
        val plain = Catalog.fromExports(languages, "{}", etymology, "{}", scripts, "{}")
        plain.withScriptMarks(mapOf("Hant" to listOf(0x8A9E)))
        plain.script("Hant")?.marks shouldBe emptyList()
        plain["zh"]?.detectScript("語言")?.code shouldBe "Hani"
    }
}
