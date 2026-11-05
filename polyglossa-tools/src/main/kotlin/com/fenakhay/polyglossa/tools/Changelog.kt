package com.fenakhay.polyglossa.tools

/**
 * Adds a release of new Wiktionary data to a changelog in the form of
 * [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), as Polyglossa's `CHANGELOG.md` is written.
 *
 * The section on top that is not released yet, headed `## [Unreleased]` or `## [2026.11.5] — unreleased`,
 * becomes the release; without one, a section is added on top. Either way it says what the data brought under
 * `### Changed`, and its link points at the release's tag.
 */
internal object Changelog {
    private const val REPOSITORY = "https://github.com/fenakhay/polyglossa"
    private val LINK = Regex("""^\[[^\]]+]: .+""")

    /** [text] with the release [version] of the data exported on [date], which brought [summary]. */
    fun record(text: String, version: String, date: String, summary: String): String {
        val lines = text.lines().toMutableList()
        val bullet = "- English Wiktionary's languages, families and scripts as exported on $date: $summary."
        val heading = "## [$version] — $date"
        val link = "[$version]: $REPOSITORY/releases/tag/$version"
        val top = lines.indexOfFirst { it.startsWith("## [") }
        val current = lines.getOrNull(top).orEmpty()
        if (current == "## [Unreleased]" || current.endsWith(" — unreleased")) {
            val label = current.substringAfter("[").substringBefore("]")
            lines[top] = heading
            val end = sectionEnd(lines, top)
            val changed = (top until end).firstOrNull { lines[it] == "### Changed" }
            if (changed != null) lines.add(changed + 2, bullet)
            else lines.addAll(end, listOf("", "### Changed", "", bullet))
            val old = lines.indexOfFirst { it.startsWith("[$label]: ") }
            if (old >= 0) lines[old] = link else addLink(lines, link)
        } else {
            lines.addAll(
                if (top >= 0) top else lines.size,
                listOf(heading, "", "### Changed", "", bullet, ""),
            )
            addLink(lines, link)
        }
        return lines.joinToString("\n")
    }

    /** Where the section headed at [top] ends: after its last line that is not blank. */
    private fun sectionEnd(lines: List<String>, top: Int): Int {
        var end =
            (top + 1 until lines.size).firstOrNull { lines[it].startsWith("## [") || LINK.matches(lines[it]) }
                ?: lines.size
        while (end - 1 > top && lines[end - 1].isBlank()) end--
        return end
    }

    private fun addLink(lines: MutableList<String>, link: String) {
        val first = lines.indexOfFirst(LINK::matches)
        if (first >= 0) {
            lines.add(first, link)
        } else {
            while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.lastIndex)
            lines.addAll(listOf("", link, ""))
        }
    }
}
