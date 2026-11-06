package com.fenakhay.polyglossa

/**
 * How Wiktionary tells which script a text is in: `Language:findBestScript` in `Module:languages` for a
 * language, and `findBestScriptWithoutLang` in `Module:scripts/charToScript` for none.
 *
 * @param catalog the catalog whose scripts are looked through.
 */
internal class ScriptDetection(private val catalog: Catalog) {
    private val scripts = catalog.scripts.filter { it.ranges.isNotEmpty() && it.code !in COMBINED }
    private val starts: IntArray
    private val holders: Array<List<Script>>
    private val sizes: Map<Script, Int> = scripts.associateWith { script ->
        script.ranges.sumOf { it.count() }
    }
    private val supersets: Set<Script> =
        scripts.filterTo(mutableSetOf()) { outer ->
            scripts.any { inner -> inner.ranges != outer.ranges && outer.covers(inner) }
        }
    private val combinations: List<Combination> = COMBINED.map { [code, parts] ->
        Combination(catalog.script(code), parts.map { catalog.script(it) })
    }
    private val han: Script? = catalog.script(HAN[0])
    private val traditional: Script? = catalog.script(HAN[1])
    private val simplified: Script? = catalog.script(HAN[2])

    init {
        val bounds = scripts.flatMap { script -> script.ranges.flatMap { listOf(it.first, it.last + 1) } }
        starts = bounds.distinct().sorted().toIntArray()
        val index = starts.withIndex().associate { [at, start] -> start to at }
        val found = Array(starts.size) { mutableListOf<Script>() }
        for (script in scripts) {
            for (range in script.ranges) {
                for (at in index.getValue(range.first) until index.getValue(range.last + 1)) found[at] +=
                    script
            }
        }
        holders = Array(found.size) { found[it].toList() }
    }

    /** The scripts with [codePoint] among their characters, those [COMBINED] makes up left out. */
    private fun holding(codePoint: Int): List<Script> {
        var low = 0
        var high = starts.size - 1
        var at = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (starts[mid] <= codePoint) {
                at = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return if (at < 0) emptyList() else holders[at]
    }

    /**
     * The script of [text] whatever its language, as `findBestScriptWithoutLang` finds it: the one most of
     * its characters belong to, or `null` when more of them belong to none.
     *
     * Each character counts for every script it belongs to. Japanese counts the characters that are Han,
     * Hiragana or Katakana, and Korean those that are Han or Hangul, each only when no one of them has them
     * all. A tie goes to a script that holds no other whole, as Katakana is beside Hiragana and Katakana and
     * Greek beside Polytonic Greek, then to a script over notation, as Latin is beside mathematical notation
     * (Unicode's codes in `Z`), then to the one with the most characters, as Han is beside Latin, then to one
     * that is no variety of another, as Latin is beside Fraktur, then by code.
     */
    fun detect(text: String): Script? {
        val counts = mutableMapOf<Script, Int>()
        val combined = IntArray(COMBINED.size)
        var unmatched = 0
        text.forEachCodePoint { codePoint ->
            val found = holding(codePoint)
            if (found.isEmpty()) unmatched++
            for (script in found) counts[script] = counts.getOrElse(script) { 0 } + 1
            combinations.forEachIndexed { at, combination ->
                if (found.any { it in combination.parts }) combined[at]++
            }
        }
        combinations.forEachIndexed { at, [script, parts] ->
            val onePartHasAll = parts.any { part -> (part?.let { counts[it] } ?: 0) == combined[at] }
            if (script != null && !onePartHasAll) counts[script] = combined[at]
        }
        val best =
            counts.entries.minWithOrNull(
                compareByDescending<Map.Entry<Script, Int>> { it.value }
                    .thenBy { it.key in supersets }
                    .thenBy { it.key.isNotation }
                    .thenByDescending { sizes[it.key] ?: combinedSize(it.key) }
                    .thenBy { it.key.parentCode != null }
                    .thenBy { it.key.code }
            )
        return best?.takeIf { it.value >= unmatched }?.key
    }

    /** Whether this is one of Unicode's codes for what is no script, such as `Zmth` or `Zsym`. */
    private val Script.isNotation: Boolean
        get() = code.length == CODE_LENGTH && code.startsWith('Z')

    private fun Script.covers(other: Script): Boolean =
        other.ranges.all { inner -> ranges.any { inner.first >= it.first && inner.last <= it.last } }

    private fun combinedSize(script: Script): Int =
        combinations
            .firstOrNull { it.script === script }
            ?.parts
            .orEmpty()
            .sumOf { part -> part?.let { sizes[it] } ?: 0 }

    /**
     * The script of [text] in [language], as `Language:findBestScript` finds it, or `null`.
     *
     * Spaces and the ASCII punctuation Wiktionary ignores are left out: Unicode's separators, tab to carriage
     * return, and `!"#%&'()*,-./:;?@[\]_{}`. The language's scripts are tried in order: the first that has
     * every character left wins, or else the one with the most of them, the earlier on a tie. Han,
     * Traditional Han and Simplified Han side by side, as `Hants` writes them, win as soon as there is a Han
     * character, as Traditional or Simplified Han when more characters mark one than the other. A language
     * written in every script is [detect]ed as if it had none, and an etymology-only variety with no scripts
     * of its own takes its language's.
     */
    fun detect(language: Language, text: String): Script? {
        val codes = scriptCodes(language)
        if (codes.isEmpty()) return null
        if (codes.size >= catalog.scripts.size && codes.containsAll(catalog.scriptCodes)) return detect(text)
        val points = codePointsOf(text)
        return if (points.isEmpty()) null else best(groups(codes), points)
    }

    /**
     * The first of [groups] that has every one of [points], or else the one with the most of them, the
     * earlier on a tie; `null` when none has any.
     */
    private fun best(groups: List<String>, points: IntArray): Script? {
        var best: Script? = null
        var bestCount = 0
        for (group in groups) {
            val candidate = candidate(group, points)
            if (candidate.decisive) return candidate.script
            if (candidate.count > bestCount) {
                best = candidate.script
                bestCount = candidate.count
            }
        }
        return best
    }

    /** The code points of [text] that are not [ignored]. */
    private fun codePointsOf(text: String): IntArray {
        var points = IntArray(text.length)
        var size = 0
        text.forEachCodePoint { if (!ignored(it)) points[size++] = it }
        if (size < points.size) points = points.copyOf(size)
        return points
    }

    private fun scriptCodes(language: Language): List<String> {
        var current: Language? = language
        val seen = mutableSetOf<String>()
        while (current != null && seen.add(current.code)) {
            if (current.scriptCodes.isNotEmpty()) return current.scriptCodes
            current = current.parent
        }
        return emptyList()
    }

    /** [codes] with Han, Traditional Han and Simplified Han side by side made one, [HANTS]. */
    private fun groups(codes: List<String>): List<String> {
        val groups = mutableListOf<String>()
        var at = 0
        while (at < codes.size) {
            val han = codes.subList(at, minOf(at + HAN.size, codes.size)) == HAN
            groups += if (han) HANTS else codes[at]
            at += if (han) HAN.size else 1
        }
        return groups
    }

    /**
     * How the script [code] stands for [points]: [Candidate.decisive] when it has them all, or for [HANTS]
     * when there is a Han character at all.
     */
    private fun candidate(code: String, points: IntArray): Candidate {
        if (code == HANTS) {
            val han = han(points)
            return Candidate(han, if (han == null) 0 else points.size, han != null)
        }
        val script = catalog.script(code) ?: return Candidate(null, 0, false)
        val count = points.count { it in script }
        return Candidate(script, count, count >= points.size)
    }

    /** Han, Traditional Han or Simplified Han for [points], or `null` when none of them is Han. */
    private fun han(points: IntArray): Script? {
        val han = han ?: return null
        var marked = 0
        var found = false
        for (point in points) {
            if (point !in han) continue
            found = true
            val isTraditional = traditional?.isMark(point) == true
            val isSimplified = simplified?.isMark(point) == true
            if (isTraditional != isSimplified) marked += if (isTraditional) 1 else -1
        }
        return when {
            !found -> null
            marked > 0 -> traditional ?: han
            marked < 0 -> simplified ?: han
            else -> han
        }
    }

    /**
     * A script that one group of a language's scripts gives for its text: [script], how many of the
     * characters it has, and whether it wins outright.
     */
    private class Candidate(val script: Script?, val count: Int, val decisive: Boolean)

    /**
     * A script made of others, as Japanese is of Han, Hiragana and Katakana, which a text is written in when
     * its characters are of more than one of them; a part the catalog lacks is `null`.
     */
    private data class Combination(val script: Script?, val parts: List<Script?>)

    private companion object {
        val HAN = listOf("Hani", "Hant", "Hans")
        const val HANTS = "Hants"
        const val CODE_LENGTH = 4
        const val IGNORED = "!\"#%&'()*,-./:;?@[\\]_{}"
        val COMBINED = mapOf("Jpan" to listOf("Hani", "Hira", "Kana"), "Kore" to listOf("Hani", "Hang"))

        val SPACES =
            listOf(0x9..0xD, 0x20..0x20, 0xA0..0xA0, 0x1680..0x1680, 0x2000..0x200A, 0x2028..0x2029) +
                listOf(0x202F..0x202F, 0x205F..0x205F, 0x3000..0x3000)

        /**
         * Whether [codePoint] is a space, a separator or tab to carriage return, or punctuation Lua ignores.
         */
        fun ignored(codePoint: Int): Boolean =
            SPACES.holds(codePoint) || (codePoint < SUPPLEMENTARY && codePoint.toChar() in IGNORED)
    }
}

/** [codePoints] as the fewest ranges that hold them, sorted. */
internal fun rangesOf(codePoints: Collection<Int>): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    for (point in codePoints.distinct().sorted()) {
        val last = ranges.lastOrNull()
        if (last != null && point == last.last + 1) ranges[ranges.lastIndex] = last.first..point
        else ranges += point..point
    }
    return ranges
}

/** Whether one of these ranges, sorted and apart, holds [codePoint]. */
internal fun List<IntRange>.holds(codePoint: Int): Boolean {
    var low = 0
    var high = size - 1
    while (low <= high) {
        val mid = (low + high) ushr 1
        val range = this[mid]
        when {
            codePoint < range.first -> high = mid - 1
            codePoint > range.last -> low = mid + 1
            else -> return true
        }
    }
    return false
}

/** [action] for each code point of this text, a surrogate pair as one and a lone surrogate as itself. */
internal fun String.forEachCodePoint(action: (Int) -> Unit) {
    var at = 0
    while (at < length) {
        val high = this[at]
        val low = getOrNull(at + 1)
        if (high.isHighSurrogate() && low != null && low.isLowSurrogate()) {
            action(codePoint(high, low))
            at += 2
        } else {
            action(high.code)
            at++
        }
    }
}

private fun codePoint(high: Char, low: Char): Int =
    ((high.code - Char.MIN_HIGH_SURROGATE.code) shl SURROGATE_BITS) +
        (low.code - Char.MIN_LOW_SURROGATE.code) +
        SUPPLEMENTARY

private const val SURROGATE_BITS = 10
private const val SUPPLEMENTARY = 0x10000
