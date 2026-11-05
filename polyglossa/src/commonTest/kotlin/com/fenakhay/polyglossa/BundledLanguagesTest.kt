package com.fenakhay.polyglossa

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** The languages Wiktionary has, looked up on the types, from the copy built into the library. */
class BundledLanguagesTest {

    @Test
    fun `languages and families are looked up on their types on every platform`() {
        val english = Language.of("en")

        Language.all.size shouldBeGreaterThan 8000
        english.name shouldBe "English"
        Language["no-such-code"] shouldBe null
        assertFailsWith<IllegalArgumentException> { Language.of("no-such-code") }
        Language.named("Old English")?.code shouldBe "ang"
        ("ang" in Language) shouldBe true
        Language.of("en-ear").full shouldBe english
        english.family?.code shouldBe "gmw-ang"
        LanguageFamily.of("ine").protoLanguage?.code shouldBe "ine-pro"
        ("ine" in LanguageFamily) shouldBe true
        LanguageFamily["no-such-family"] shouldBe null
        LanguageFamily.of("ine").name shouldBe "Indo-European"
        Language.of("ang").family?.languages.orEmpty() shouldContain english
        english.scripts.first() shouldBe Script.of("Latn")
        Script.named("Arabic")?.direction shouldBe ScriptDirection.RIGHT_TO_LEFT
        Script.of("Hans").parent?.code shouldBe "Hani"
        ("Latn" in Script) shouldBe true
        Script["Xxxx-none"] shouldBe null
        Script.all.size shouldBeGreaterThan 200
        Catalog.BUNDLED.date?.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) shouldBe true
        Language.of("de").varieties.map { it.name } shouldContain "Bavarian German"
        Language.all.count { it.varieties.isNotEmpty() } shouldBeGreaterThan 400
    }
}
