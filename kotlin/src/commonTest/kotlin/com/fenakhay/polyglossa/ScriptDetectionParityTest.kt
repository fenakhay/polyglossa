package com.fenakhay.polyglossa

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * Script detection against what English Wiktionary answered, `{{#invoke:languages/templates|getByCode|…|
 * findBestScript|…}}`, on 6 November 2026, with the data bundled then.
 */
class ScriptDetectionParityTest {

    private class Case(val language: String, val text: String, val script: String?) {
        override fun toString(): String = "$language $text → $script"
    }

    private val cases =
        listOf(
            Case("ru", "Москва", "Cyrl"),
            Case("ru", "Moskva", null),
            Case("ru", "Москва!", "Cyrl"),
            Case("en", "hello world", "Latn"),
            Case("en", "123", null),
            Case("en", "—", null),
            Case("en", "naïve", "Latn"),
            Case("ar", "كتاب", "Arab"),
            Case("fa", "کتاب", "Arab"),
            Case("ur", "کتاب", "Aran"),
            Case("ps", "کتاب", "Arab"),
            Case("pa", "ਪੰਜਾਬ", "Guru"),
            Case("pa", "پنجاب", "Aran"),
            Case("hi", "हिन्दी", "Deva"),
            Case("hi", "hindi", null),
            Case("sa", "संस्कृतम्", "Deva"),
            Case("zh", "语言", "Hans"),
            Case("zh", "語言", "Hant"),
            Case("zh", "中文", "Hani"),
            Case("zh", "後", "Hant"),
            Case("zh", "中abc", "Hani"),
            Case("zh", "abc", "Latn"),
            Case("cmn", "汉语", "Hans"),
            Case("yue", "廣東話", "Hant"),
            Case("yue", "广东话", "Hans"),
            Case("ja", "ひらがな", "Jpan"),
            Case("ja", "カタカナ", "Jpan"),
            Case("ja", "東京", "Jpan"),
            Case("ja", "Tokyo", "Jpan"),
            Case("ko", "한국어", "Kore"),
            Case("ko", "韓國", "Kore"),
            Case("ko", "Seoul", "Kore"),
            Case("grc", "λόγος", "Polyt"),
            Case("el", "λόγος", "Grek"),
            Case("el", "logos", null),
            Case("yi", "ייִדיש", "Hebr"),
            Case("he", "עברית", "Hebr"),
            Case("mn", "монгол", "Cyrl"),
            Case("mn", "ᠮᠣᠩᠭᠣᠯ", "Mong"),
            Case("sh", "Moskva Москва", "Latn"),
            Case("sh", "Москва Moskva", "Latn"),
            Case("sh", "Москва Moskvaa", "Latn"),
            Case("mul", "Ελληνικά", "Grek"),
            Case("mul", "abc", "Latn"),
            Case("mul", "東京", "Hani"),
            Case("mul", "ひらがな漢字", "Jpan"),
            Case("mul", "123", null),
            Case("mul", "ab12345", null),
            Case("mul", "a b c d", "Latn"),
            Case("mul", "Москва 2026", "Cyrl"),
            Case("mul", "한국어", "Hang"),
            Case("mul", "アbc", "Latn"),
            Case("mul", "كتاب", "Arab"),
            Case("mul", "𞤀𞤁", "Adlm"),
            Case("mul", "∀x", "Latn"),
            Case("und", "Москва", "Cyrl"),
            Case("got", "𐌲𐌿𐍄𐌰", "Goth"),
            Case("ain", "アイヌ", "Kana"),
            Case("ady", "адыгабзэ", "Cyrl"),
            Case("kk", "қазақ", "Cyrl"),
            Case("tt", "татар", "Cyrl"),
        )

    @Test
    fun `every language gives the script Wiktionary gives`() {
        cases.filter { Language.of(it.language).detectScript(it.text)?.code != it.script } shouldBe
            emptyList()
    }

    @Test
    fun `Unihan marks fewer characters than Wiktionary's lists do`() {
        Language.of("zh").detectScript("后")?.code shouldBe "Hani"
        Language.of("zh").detectScript("干")?.code shouldBe "Hani"
        Language.of("zh").detectScript("乾")?.code shouldBe "Hani"
    }
}
