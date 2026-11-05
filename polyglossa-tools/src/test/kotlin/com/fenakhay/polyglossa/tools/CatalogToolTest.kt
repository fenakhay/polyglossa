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
            "export_scripts" in text -> """{"Latn":{"1":"Latin","2":8229,"3":"alphabet"}}"""
            "varieties" in text -> """{"en":["Polari",["Yinglish","Yiddish English"]]}"""
            "aliases" in text -> """{"en":["Modern English"]}"""
            "export_languages" in text -> """{"en":{"1":"$english","2":1860,"4":"Latn","type":"regular"}}"""
            "etymology" in text -> """{"en-ear":{"1":"Early Modern English","parent":"en"}}"""
            else -> """{"ine":{"1":"Indo-European","2":19860}}"""
        }
    }

    private val variables = mapOf("GITHUB_OUTPUT" to "github-output", "GITHUB_STEP_SUMMARY" to "summary.md")

    private val environment = Environment(::expand, { today }, output, log, variables::get, directory)

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
        read("github-output") shouldBe "changed=false\nversion=2026.11.5\n"
        read("CHANGELOG.md") shouldBe CHANGELOG
        directory.resolve("notes.md").exists() shouldBe false

        english = "Anglish"
        directory.resolve("github-output").writeText("")
        writeOrCheck(dump, environment) shouldBe 0
        read("catalog.tsv").lines().first() shouldBe "polyglossa\t1\t2026-11-12"
        read("github-output") shouldBe "changed=true\nversion=2026.11.12\n"
        read("notes.md") shouldContain "as exported on 2026-11-12: 0 added, 0 removed, 1 changed."
        read("notes.md") shouldContain "- `en`: name English → Anglish"
        read("summary.md") shouldContain "- `en`: name English → Anglish"
        read("CHANGELOG.md") shouldContain "## [2026.11.12] — 2026-11-12"
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
        log.toString() shouldContain "Wiktionary exported 0 scripts where the catalog has 1"

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
            Changelog.record(CHANGELOG, "2026.11.12", "2026-11-12", "0 added, 0 removed, 1 changed")
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
