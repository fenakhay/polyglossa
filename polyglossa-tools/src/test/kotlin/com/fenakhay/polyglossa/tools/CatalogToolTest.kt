package com.fenakhay.polyglossa.tools

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test

/** Recording English Wiktionary's catalog, and telling when it changes. */
class CatalogToolTest {

    private var english = "English"
    private var latinEnd = 122
    private var broken = false
    private var truncated = false
    private var today = "2026-11-05"
    private val asked = mutableListOf<String>()
    private val output = StringBuilder()
    private val log = StringBuilder()
    private val directory = Files.createTempDirectory("polyglossa-tools")

    private fun expand(text: String): String {
        asked += text
        return when {
            broken -> "<strong class=\"error\">Lua error: no schema given for key &#039;Latn&#039;</strong>"
            truncated && "export_scripts" in text -> "{}"
            "export_scripts" in text ->
                """{"Latn":{"1":"Latin","2":8229,"3":"alphabet","ranges":[65,90,97,$latinEnd]},""" +
                    """"Hant":{"1":"Traditional Han","ranges":[19968,40959]}}"""
            "varieties" in text -> """{"en":["Polari",["Yinglish","Yiddish English"]]}"""
            "aliases" in text -> """{"en":["Modern English"]}"""
            "export_languages" in text -> """{"en":{"1":"$english","2":1860,"4":"Latn","type":"regular"}}"""
            "etymology" in text -> """{"en-ear":{"1":"Early Modern English","parent":"en"}}"""
            else -> """{"ine":{"1":"Indo-European","2":19860}}"""
        }
    }

    private val variables = mapOf("GITHUB_OUTPUT" to "github-output", "GITHUB_STEP_SUMMARY" to "summary.md")

    private var unihan = "U+4E7E\tkSimplifiedVariant\tU+5E72\nU+5E72\tkTraditionalVariant\tU+4E7E U+5E79\n"

    private val environment =
        Environment(::expand, { today }, output, log, variables::get, directory) { unihan }

    private fun read(file: String): String = directory.resolve(file).readText()

    @Test
    fun `the catalog is written then checked, and a change is reported and fails the check`() {
        writeOrCheck(arrayOf("catalog.tsv"), environment) shouldBe 0
        directory.resolve("catalog.tsv").readText() shouldContain "L\ten\tEnglish"
        directory.resolve("catalog.tsv").readText().lines().first() shouldBe "polyglossa\t1\t2026-11-05"
        asked.size shouldBe 6
        writeOrCheck(arrayOf("catalog.tsv", "--check"), environment) shouldBe 0
        output.toString() shouldContain "are as catalog.tsv has them"

        english = "Anglish"
        writeOrCheck(arrayOf("catalog.tsv", "--check", "--report", "report.md"), environment) shouldBe 1
        directory.resolve("report.md").readText() shouldContain "`en`: name English → Anglish"

        broken = true
        writeOrCheck(arrayOf("catalog.tsv", "--check"), environment) shouldBe 1
        log.toString() shouldContain "no schema given for key 'Latn'"
    }

    @Test
    fun `Unihan marks Han and the script's characters and marks are recorded and reported`() {
        writeOrCheck(arrayOf("catalog.tsv"), environment) shouldBe 0
        read("catalog.tsv") shouldContain "S\tLatn\tLatin\t8229\talphabet\t\t\t\t\t41-5A,61-7A"
        read("catalog.tsv") shouldContain "S\tHant\tTraditional Han\t\t\t\t\t\t\t4E00-9FFF\t4E7E"

        latinEnd = 121
        unihan =
            "U+4E7E\tkSimplifiedVariant\tU+5E72\nU+4E7F\tkSimplifiedVariant\tU+5E73\n" +
                "U+5E72\tkTraditionalVariant\tU+4E7E U+5E79\n"
        writeOrCheck(arrayOf("catalog.tsv", "--check", "--report", "report.md"), environment) shouldBe 1
        read("report.md") shouldContain "script `Latn`: characters 52 code points → 51 code points"
        read("report.md") shouldContain "script `Hant`: marks 1 code points → 2 code points"
    }

    @Test
    fun `Unihan marks a character only when every other form is another character`() {
        val variants =
            """
            # Unihan_Variants.txt
            U+4E7E	kSimplifiedVariant	U+5E72
            U+51FA	kTraditionalVariant	U+51FA U+9F63
            U+5E72	kTraditionalVariant	U+4E7E U+5E79
            U+8A9E	kSimplifiedVariant	U+8BED
            U+8BED	kTraditionalVariant	U+8A9E<kMatthews
            U+4E00	kSemanticVariant	U+5F0C
            """
                .trimIndent()
        Unihan.marks(variants) shouldBe
            mapOf("Hant" to setOf(0x4E7E, 0x8A9E), "Hans" to setOf(0x5E72, 0x8BED))
    }

    @Test
    fun `a dump fails, writing nothing, when Unihan gives no marks`() {
        unihan = "# nothing\n"
        writeOrCheck(arrayOf("catalog.tsv"), environment) shouldBe 1
        log.toString() shouldContain "refusing what looks like a broken download"
        directory.resolve("catalog.tsv").exists() shouldBe false
    }

    @Test
    fun `the spec and the SQL are written from the catalog as it is, offline`() {
        writeOrCheck(arrayOf("catalog.tsv"), environment) shouldBe 0
        asked.clear()

        writeOrCheck(
            arrayOf("catalog.tsv", "--offline", "--spec", "spec.json", "--sql", "out.sql"),
            environment,
        ) shouldBe 0

        asked.size shouldBe 0
        val spec = directory.resolve("spec.json").readText()
        spec shouldContain "\"code\": \"en\""
        spec shouldContain "\"date\": \"2026-11-05\""
        val sql = directory.resolve("out.sql").readText()
        sql shouldContain "CREATE TABLE languages"
        sql shouldContain "INSERT INTO languages VALUES\n('en', 'English', 'regular', 'Q1860', NULL, NULL)"
        sql shouldContain "INSERT INTO varieties VALUES\n('en', 0, 'Polari'),\n('en', 1, 'Yinglish')"
        sql shouldContain "INSERT INTO metadata VALUES\n('format', '1'),\n('date', '2026-11-05')"
        sql shouldContain
            "INSERT INTO script_ranges VALUES\n('Hant', 19968, 40959),\n('Latn', 65, 90),\n('Latn', 97, 122)"
        sql shouldContain "INSERT INTO script_marks VALUES\n('Hant', 20094, 20094)"
        Sql.literal("O'Brien") shouldBe "'O''Brien'"
        Sql.literal(null) shouldBe "NULL"
    }

    @Test
    fun `a dump writes the catalog only when the data has changed, and says which version it makes`() {
        directory.resolve("CHANGELOG.md").writeText(CHANGELOG)
        writeOrCheck(arrayOf("catalog.tsv"), environment) shouldBe 0
        directory.resolve("github-output").writeText("")

        today = "2026-11-12"
        val dump = arrayOf("catalog.tsv", "--report", "notes.md", "--changelog", "CHANGELOG.md")
        writeOrCheck(dump, environment) shouldBe 0
        read("catalog.tsv").lines().first() shouldBe "polyglossa\t1\t2026-11-05"
        read("github-output") shouldBe "changed=false\nrelease=false\nversion=2026.11.5\n"
        read("CHANGELOG.md") shouldBe CHANGELOG
        directory.resolve("notes.md").exists() shouldBe false

        english = "Anglish"
        directory.resolve("github-output").writeText("")
        writeOrCheck(dump, environment) shouldBe 0
        read("catalog.tsv").lines().first() shouldBe "polyglossa\t1\t2026-11-12"
        read("github-output") shouldBe "changed=true\nrelease=true\nversion=2026.11.12\n"
        read("notes.md") shouldContain "as exported on 2026-11-12: 0 added, 0 removed, 1 changed."
        read("notes.md") shouldContain "- `en`: name English → Anglish"
        read("summary.md") shouldContain "- `en`: name English → Anglish"
        read("CHANGELOG.md") shouldContain "## [2026.11.12] — 2026-11-12"
    }

    @Test
    fun `a release of the same data dates the catalog today and releases the unreleased section`() {
        directory.resolve("CHANGELOG.md").writeText(CHANGELOG)
        writeOrCheck(arrayOf("catalog.tsv"), environment) shouldBe 0
        directory.resolve("github-output").writeText("")
        today = "2026-11-12"

        val release =
            arrayOf("catalog.tsv", "--release", "--report", "notes.md", "--changelog", "CHANGELOG.md")
        writeOrCheck(release, environment) shouldBe 0

        read("catalog.tsv").lines().first() shouldBe "polyglossa\t1\t2026-11-12"
        read("github-output") shouldBe "changed=false\nrelease=true\nversion=2026.11.12\n"
        read("CHANGELOG.md") shouldContain
            "## [2026.11.12] — 2026-11-12\n\n### Added\n\n- Everything.\n\n[2026.11.12]:"
        read("notes.md") shouldBe "### Added\n\n- Everything.\n"

        today = "2026-11-19"
        directory.resolve("github-output").writeText("")
        writeOrCheck(release, environment) shouldBe 1
        log.toString() shouldContain "has no unreleased section; there is nothing to release"
        read("catalog.tsv").lines().first() shouldBe "polyglossa\t1\t2026-11-12"
        read("github-output") shouldBe ""
    }

    @Test
    fun `a dump fails, writing nothing, when an export cannot be read or comes back cut short`() {
        writeOrCheck(arrayOf("catalog.tsv"), environment) shouldBe 0
        val recorded = read("catalog.tsv")
        directory.resolve("github-output").writeText("")
        today = "2026-11-12"
        english = "Anglish"

        broken = true
        writeOrCheck(arrayOf("catalog.tsv"), environment) shouldBe 1
        log.toString() shouldContain "no schema given for key 'Latn'"
        broken = false

        truncated = true
        writeOrCheck(arrayOf("catalog.tsv"), environment) shouldBe 1
        log.toString() shouldContain "Wiktionary exported 0 scripts where the catalog has 2"

        read("catalog.tsv") shouldBe recorded
        read("github-output") shouldBe ""
    }

    @Test
    fun `the section not released yet becomes the release of the new data`() {
        val recorded =
            Changelog.record(CHANGELOG, "2026.11.12", "2026-11-12", "1 added, 0 removed, 2 changed")
        recorded shouldBe
            """
            # Changelog

            Intro.

            ## [2026.11.12] — 2026-11-12

            ### Added

            - Everything.

            ### Changed

            - English Wiktionary's languages, families and scripts as exported on 2026-11-12: 1 added, 0 removed, 2 changed.

            [2026.11.12]: https://github.com/fenakhay/polyglossa/releases/tag/2026.11.12
            """
                .trimIndent() + "\n"
    }

    @Test
    fun `after a release, the new data gets a section of its own`() {
        val released =
            Changelog.record(CHANGELOG, "2026.11.12", "2026-11-12", "0 added, 0 removed, 1 changed").orEmpty()
        Changelog.record(released, "2026.11.19", "2026-11-19", "3 added, 0 removed, 0 changed") shouldBe
            """
            # Changelog

            Intro.

            ## [2026.11.19] — 2026-11-19

            ### Changed

            - English Wiktionary's languages, families and scripts as exported on 2026-11-19: 3 added, 0 removed, 0 changed.

            ## [2026.11.12] — 2026-11-12

            ### Added

            - Everything.

            ### Changed

            - English Wiktionary's languages, families and scripts as exported on 2026-11-12: 0 added, 0 removed, 1 changed.

            [2026.11.19]: https://github.com/fenakhay/polyglossa/releases/tag/2026.11.19
            [2026.11.12]: https://github.com/fenakhay/polyglossa/releases/tag/2026.11.12
            """
                .trimIndent() + "\n"
    }

    @Test
    fun `a release's section is read back without its heading`() {
        val released = Changelog.record(CHANGELOG, "2026.11.12", "2026-11-12", null).orEmpty()
        Changelog.section(released, "2026.11.12") shouldBe "### Added\n\n- Everything."
        Changelog.section(released, "2027.1.1") shouldBe null
        Changelog.record(released, "2026.11.19", "2026-11-19", null) shouldBe null
    }

    @Test
    fun `a version is the export's date`() {
        calendarVersion("2027-01-05") shouldBe "2027.1.5"
        calendarVersion(null) shouldBe null
    }

    @Test
    fun `a workflow command in what the wiki sent is shown, not run`() {
        withoutWorkflowCommands("::error::boom\nfine") shouldBe ":\u200B:error::boom\nfine"
    }

    private companion object {
        val CHANGELOG =
            """
            # Changelog

            Intro.

            ## [2026.11.5] — unreleased

            ### Added

            - Everything.

            [2026.11.5]: https://github.com/fenakhay/polyglossa/commits/main
            """
                .trimIndent() + "\n"
    }
}
