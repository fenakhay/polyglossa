package com.fenakhay.polyglossa.tools

import com.fenakhay.polyglossa.Catalog
import com.fenakhay.polyglossa.Language
import com.fenakhay.polyglossa.LanguageFamily
import com.fenakhay.polyglossa.Script
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.exitProcess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Writes or checks the catalog polyglossa is built with, `data/catalog.tsv`, from what English Wiktionary's
 * `Module:JSON data` exports.
 *
 * The first argument is the file. `--check` compares what Wiktionary has now with the file instead of writing
 * it, prints what changed, and exits 1 when anything did. `--report <file>` keeps the report as well as
 * printing it. `--exports <dir>` reads the six exports from `languages.json`, `aliases.json`,
 * `etymology.json`, `families.json`, `scripts.json` and `varieties.json` there, as `Module:JSON data` wrote
 * them, instead of asking the wiki. The marks that tell Traditional from Simplified Han come from Unihan,
 * downloaded from unicode.org, or with `--unihan <file>` from a Unihan.zip or `Unihan_Variants.txt` there.
 * `--spec <file>` writes what every implementation must answer from the catalog, and `--sql <file>` the
 * catalog as SQL; with `--offline`, they are written from the file as it is, which is left alone.
 *
 * Without `--check`, the file is rewritten only when what Wiktionary exports differs from it, so its date,
 * and so the version, moves only with the data. Then `--report <file>` gets the release notes, and
 * `--changelog <file>` a section for the new version. `--release` rewrites the file, dated today, even when
 * the data is the same, to release what the changelog's unreleased section holds; it fails when there is no
 * such section. A fetch that fails, or one that loses more than a tenth of the languages, families or scripts
 * the file has, fails without writing anything. On GitHub Actions, the step gets the outputs `changed`,
 * whether the data changed, `release`, whether the file was rewritten for a release, and `version`, the
 * release the file now makes.
 */
public fun main(args: Array<String>) {
    exitProcess(writeOrCheck(args, Environment.SYSTEM))
}

/**
 * What the tool reads from and writes to, which a test replaces.
 *
 * @property expand what English Wiktionary expands a wikitext to.
 * @property today the day it is, in UTC, as `2026-11-05`: the day the exports are recorded as made.
 * @property output where the report and what was written go.
 * @property log where failures go.
 * @property variables the value of an environment variable, or `null`.
 * @property workingDirectory the directory a relative path is taken from.
 * @property unihan Unihan's `Unihan_Variants.txt`, downloaded.
 */
internal class Environment(
    val expand: (String) -> String,
    val today: () -> String,
    val output: Appendable,
    val log: Appendable,
    val variables: (String) -> String?,
    val workingDirectory: Path,
    val unihan: () -> String,
) {
    companion object {
        val SYSTEM: Environment =
            Environment(
                expand = Wiktionary()::expand,
                today = { LocalDate.now(ZoneOffset.UTC).toString() },
                output = System.out,
                log = System.err,
                variables = System::getenv,
                workingDirectory = Path(""),
                unihan = Unihan::download,
            )
    }
}

/**
 * English Wiktionary's API, asked politely slowly, since it is somebody else's production wiki, and only to
 * expand a module's exports, which change nothing.
 */
internal class Wiktionary {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS)).build()
    private var last = 0L

    fun expand(text: String): String {
        val wait = last + PAUSE_MILLIS - System.currentTimeMillis()
        if (wait > 0) Thread.sleep(wait)
        last = System.currentTimeMillis()
        val form =
            mapOf(
                    "action" to "expandtemplates",
                    "prop" to "wikitext",
                    "format" to "json",
                    "formatversion" to "2",
                    "text" to text,
                )
                .entries
                .joinToString("&") { [key, value] -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
        val request =
            HttpRequest.newBuilder(URI(API))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != HTTP_OK) throw IOException("$API answered ${response.statusCode()}")
        val body = Json.parseToJsonElement(response.body()).jsonObject
        return (body["expandtemplates"] as? JsonObject)?.get("wikitext")?.jsonPrimitive?.content
            ?: throw IOException("$API sent no expansion: ${body["error"] ?: body}")
    }

    private companion object {
        const val API = "https://en.wiktionary.org/w/api.php"
        const val USER_AGENT = "polyglossa/0.1 (https://github.com/fenakhay/polyglossa)"
        const val PAUSE_MILLIS = 500L
        const val TIMEOUT_SECONDS = 60L
        const val HTTP_OK = 200
    }
}

/** What [main] does with [args], printing through [environment]: the status to exit with. */
internal fun writeOrCheck(args: Array<String>, environment: Environment): Int {
    val valued = setOf("--report", "--exports", "--spec", "--sql", "--changelog", "--unihan")
    val named = args.toList().windowed(2).filter { [key, _] -> key in valued }.associate { [k, v] -> k to v }
    val positional = args.filterIndexed { i, arg ->
        !arg.startsWith("--") && args.getOrNull(i - 1) !in valued
    }
    val target = environment.workingDirectory.resolve(positional.firstOrNull() ?: DEFAULT_FILE)
    val report = named["--report"]?.let(environment.workingDirectory::resolve)
    val offline = "--offline" in args
    val current =
        if (offline) Catalog.fromTsv(target.readText())
        else
            read(
                    named["--exports"]?.let(environment.workingDirectory::resolve),
                    named["--unihan"]?.let(environment.workingDirectory::resolve),
                    environment,
                )
                .getOrElse {
                    return failed(it.message.orEmpty(), environment, report)
                }

    if ("--check" !in args) {
        val kept =
            if (offline) current
            else
                dump(target, current, Dump(named["--changelog"], "--release" in args), environment, report)
                    .getOrElse {
                        return failed(it.message.orEmpty(), environment, report)
                    }
        named["--spec"]?.let { written(environment, it, Spec.write(kept)) }
        named["--sql"]?.let { written(environment, it, Sql.write(kept)) }
        return 0
    }

    val recorded = Catalog.fromTsv(if (target.exists()) target.readText() else EMPTY)
    val changes = CatalogChanges.between(recorded, current)
    val markdown =
        changes.render(target.fileName.toString()) +
            if (changes.isEmpty) "" else "\nReview the lines above, then run `./gradlew catalogDump`.\n"
    environment.output.appendLine(withoutWorkflowCommands(markdown))
    report?.createParentDirectories()?.writeText(markdown)
    environment.appendTo("GITHUB_STEP_SUMMARY", markdown + "\n")
    return if (changes.isEmpty) 0 else 1
}

/**
 * How a dump records what it fetched.
 *
 * @property changelog the changelog to add the release to, if any.
 * @property release whether to release even when the data is the same.
 */
private class Dump(val changelog: String?, val release: Boolean)

/**
 * Records [fetched] in [target] when it differs from what [target] holds, or when [dump] asks for a release,
 * with the release notes in [report] and a section in the changelog: the catalog the file now holds, or why
 * [fetched] was refused.
 */
private fun dump(
    target: Path,
    fetched: Catalog,
    dump: Dump,
    environment: Environment,
    report: Path?,
): Result<Catalog> {
    val recordedText = if (target.exists()) target.readText() else EMPTY
    val recorded = Catalog.fromTsv(recordedText)
    truncated(recorded, fetched)?.let {
        return Result.failure(IllegalStateException(it))
    }
    val changed = recordedText.substringAfter('\n') != fetched.toTsv().substringAfter('\n')
    if (!changed && !dump.release) {
        environment.output.appendLine(
            "English Wiktionary's languages, families and scripts are as $target has them, exported " +
                "${recorded.date}; nothing written"
        )
        environment.appendTo(
            "GITHUB_OUTPUT",
            "changed=false\nrelease=false\nversion=${calendarVersion(recorded.date).orEmpty()}\n",
        )
        return Result.success(recorded)
    }
    val date = fetched.date.orEmpty()
    val version = calendarVersion(date).orEmpty()
    val changes = CatalogChanges.between(recorded, fetched)
    val changelog = dump.changelog?.let(environment.workingDirectory::resolve)
    val updated = changelog?.let {
        Changelog.record(it.readText(), version, date, changes.summary.takeIf { changed })
            ?: return Result.failure(
                IllegalStateException(
                    "Wiktionary's data is as $target has it and $it has no unreleased section; " +
                        "there is nothing to release"
                )
            )
    }
    target.createParentDirectories().writeText(fetched.toTsv())
    environment.output.appendLine(
        "wrote $target: ${fetched.languages.size} languages, ${fetched.families.size} families, " +
            "${fetched.scripts.size} scripts, exported $date"
    )
    if (changelog != null && updated != null) {
        changelog.writeText(updated)
        environment.output.appendLine("added $version to $changelog")
    }
    val notes =
        releaseNotes(updated?.let { Changelog.section(it, version) }, changes.takeIf { changed }, date)
    report?.createParentDirectories()?.writeText(notes)
    environment.appendTo("GITHUB_STEP_SUMMARY", notes + "\n")
    environment.appendTo("GITHUB_OUTPUT", "changed=$changed\nrelease=true\nversion=$version\n")
    return Result.success(fetched)
}

/**
 * The notes of a release: its [section] of the changelog, if any, then what [changes] the data brought, if it
 * changed, as exported on [date].
 */
private fun releaseNotes(section: String?, changes: CatalogChanges?, date: String): String =
    when {
        section != null && changes != null -> section + "\n" + changes.details()
        section != null -> section + "\n"
        changes != null -> changes.notes(date)
        else -> "English Wiktionary's languages, families and scripts as exported on $date, unchanged.\n"
    }

/**
 * Why [fetched] looks cut short beside [recorded], losing more than a tenth of its languages, families or
 * scripts, as an export that failed half way would; or `null`.
 */
internal fun truncated(recorded: Catalog, fetched: Catalog): String? =
    listOf(
            "languages" to (recorded.languages.size to fetched.languages.size),
            "families" to (recorded.families.size to fetched.families.size),
            "scripts" to (recorded.scripts.size to fetched.scripts.size),
        )
        .firstOrNull { [_, sizes] -> sizes.second * TENTHS < sizes.first * (TENTHS - 1) }
        ?.let { [kind, sizes] ->
            "Wiktionary exported ${sizes.second} $kind where the catalog has ${sizes.first}; " +
                "refusing what looks like a broken export"
        }

/** The version a catalog exported on [date], `2026-11-05`, is released as: `2026.11.5`. */
internal fun calendarVersion(date: String?): String? =
    date?.split('-')?.takeIf { it.size == DATE_PARTS }?.joinToString(".") { it.toInt().toString() }

private fun Environment.appendTo(variable: String, text: String) {
    variables(variable)
        ?.takeIf { it.isNotEmpty() }
        ?.let {
            Files.writeString(
                workingDirectory.resolve(it),
                text,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
}

/**
 * The catalog in [exports] when given, or else the one Wiktionary has now, with the marks of Han from
 * [unihan] or else Unihan as unicode.org has it, or why it could not be read.
 */
private fun read(exports: Path?, unihan: Path?, environment: Environment): Result<Catalog> =
    try {
        val catalog =
            Catalog.fromExports(
                if (exports != null) EXPORT_FILES.map { exports.resolve(it).readText() }
                else Catalog.EXPORTS.map(environment.expand),
                environment.today(),
            )
        val variants = if (unihan != null) Unihan.read(unihan) else environment.unihan()
        Result.success(catalog.withScriptMarks(Unihan.marks(variants)))
    } catch (e: IOException) {
        Result.failure(IllegalStateException("could not read Wiktionary's exports: ${e.message}", e))
    } catch (e: IllegalStateException) {
        Result.failure(e)
    } catch (e: IllegalArgumentException) {
        Result.failure(e)
    }

private fun written(environment: Environment, file: String, text: String) {
    val target = environment.workingDirectory.resolve(file)
    target.createParentDirectories().writeText(text)
    environment.output.appendLine("wrote $target")
}

private fun failed(message: String, environment: Environment, report: Path?): Int {
    environment.log.appendLine(withoutWorkflowCommands(message))
    report?.createParentDirectories()?.writeText("$message\n")
    return 1
}

/**
 * [text] with every line that GitHub Actions would read as a workflow command, one starting with `::`, made
 * plain text by a zero-width space between the colons, since what Wiktionary sends reaches the job's log.
 */
internal fun withoutWorkflowCommands(text: String): String =
    text.lines().joinToString("\n") { line ->
        if (line.trimStart().startsWith("::")) line.replaceFirst("::", ":\u200B:") else line
    }

/** How two copies of the language data differ, by code. */
internal class CatalogChanges(
    val added: List<String>,
    val removed: List<String>,
    val changed: List<String>,
) {
    val isEmpty: Boolean
        get() = added.isEmpty() && removed.isEmpty() && changed.isEmpty()

    /** How many were added, removed and changed, in a few words. */
    val summary: String
        get() = "${added.size} added, ${removed.size} removed, ${changed.size} changed"

    /** What changed, as the notes of the release made of an export on [date]. */
    fun notes(date: String): String =
        "English Wiktionary's languages, families and scripts as exported on $date: $summary.\n" + details()

    /** Each language, family or script added, removed or changed, under a heading for each. */
    fun details(): String = buildString {
        section("Added", added)
        section("Removed", removed)
        section("Changed", changed)
    }

    fun render(file: String): String = buildString {
        if (isEmpty) {
            appendLine("English Wiktionary's languages, families and scripts are as $file has them.")
            return@buildString
        }
        appendLine("English Wiktionary's languages, families and scripts differ from $file:")
        appendLine("$summary.")
        section("Added", added)
        section("Removed", removed)
        section("Changed", changed)
    }

    private fun StringBuilder.section(title: String, lines: List<String>) {
        if (lines.isEmpty()) return
        appendLine()
        appendLine("## $title")
        appendLine()
        lines.forEach { appendLine("- $it") }
    }

    companion object {
        fun between(recorded: Catalog, current: Catalog): CatalogChanges {
            val parts =
                listOf(
                    Part.of("", recorded.languages, current.languages, { it.code }, { it.name }, ::describe),
                    Part.of(
                        "family ",
                        recorded.families,
                        current.families,
                        { it.code },
                        { it.name },
                        ::describe,
                    ),
                    Part.of(
                        "script ",
                        recorded.scripts,
                        current.scripts,
                        { it.code },
                        { it.name },
                        ::describe,
                    ),
                )
            return CatalogChanges(
                added = parts.flatMap { it.added },
                removed = parts.flatMap { it.removed },
                changed = parts.flatMap { it.changed },
            )
        }

        /** What changed among one kind of thing: languages, families or scripts. */
        private class Part(val added: List<String>, val removed: List<String>, val changed: List<String>) {
            companion object {
                fun <T> of(
                    kind: String,
                    before: Collection<T>,
                    after: Collection<T>,
                    code: (T) -> String,
                    name: (T) -> String,
                    describe: (T, T) -> String?,
                ): Part {
                    val old = before.associateBy(code)
                    val new = after.associateBy(code)
                    return Part(
                        added = (new.keys - old.keys).sorted().map { "$kind`$it` ${name(new.getValue(it))}" },
                        removed =
                            (old.keys - new.keys).sorted().map { "$kind`$it` ${name(old.getValue(it))}" },
                        changed =
                            (old.keys intersect new.keys).sorted().mapNotNull {
                                describe(old.getValue(it), new.getValue(it))
                            },
                    )
                }
            }
        }

        private fun describe(before: Language, after: Language): String? {
            val fields =
                listOf(
                    "name" to (before.name to after.name),
                    "type" to (before.type to after.type),
                    "wikidata" to (before.wikidata to after.wikidata),
                    "family" to (before.family?.code to after.family?.code),
                    "scripts" to (before.scripts.map { it.code } to after.scripts.map { it.code }),
                    "ancestors" to (before.ancestors.map { it.code } to after.ancestors.map { it.code }),
                    "aliases" to (before.aliases to after.aliases),
                    "parent" to (before.parent?.code to after.parent?.code),
                    "varieties" to (before.varieties to after.varieties),
                )
            return differences("`${after.code}`", fields)
        }

        private fun describe(before: LanguageFamily, after: LanguageFamily): String? {
            val fields =
                listOf(
                    "name" to (before.name to after.name),
                    "wikidata" to (before.wikidata to after.wikidata),
                    "parent" to (before.parent?.code to after.parent?.code),
                    "protoLanguage" to (before.protoLanguage?.code to after.protoLanguage?.code),
                    "aliases" to (before.aliases to after.aliases),
                )
            return differences("family `${after.code}`", fields)
        }

        private fun describe(before: Script, after: Script): String? {
            val fields =
                listOf(
                    "name" to (before.name to after.name),
                    "wikidata" to (before.wikidata to after.wikidata),
                    "system" to (before.system to after.system),
                    "aliases" to (before.aliases to after.aliases),
                    "parent" to (before.parent?.code to after.parent?.code),
                    "direction" to (before.direction to after.direction),
                    "local names" to (localNames(before) to localNames(after)),
                    "characters" to (Characters(before.ranges) to Characters(after.ranges)),
                    "marks" to (Characters(before.marks) to Characters(after.marks)),
                )
            return differences("script `${after.code}`", fields)
        }

        /** Some characters, told apart by [ranges] and written as how many there are. */
        private data class Characters(val ranges: List<IntRange>) {
            override fun toString(): String = "${ranges.sumOf { it.count() }} code points"
        }

        private fun localNames(script: Script): List<String> =
            script.localNames.map { [language, name] -> "${language.code}=$name" }.sorted()

        private fun differences(what: String, fields: List<Pair<String, Pair<Any?, Any?>>>): String? {
            val changed = fields.filter { [_, values] -> values.first != values.second }
            if (changed.isEmpty()) return null
            return what +
                ": " +
                changed.joinToString("; ") { [name, values] -> "$name ${values.first} → ${values.second}" }
        }
    }
}

private const val DEFAULT_FILE = "data/catalog.tsv"
private val EXPORT_FILES =
    listOf(
        "languages.json",
        "aliases.json",
        "etymology.json",
        "families.json",
        "scripts.json",
        "varieties.json",
    )
private const val EMPTY = "polyglossa\t1\n"
private const val TENTHS = 10
private const val DATE_PARTS = 3
