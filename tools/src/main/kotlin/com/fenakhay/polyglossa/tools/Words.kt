package com.fenakhay.polyglossa.tools

import com.fenakhay.polyglossa.LanguageType
import com.fenakhay.polyglossa.ScriptDirection

/** The word the spec and the SQL use for this type, as Wiktionary's modules spell it. */
internal val LanguageType.word: String
    get() =
        when (this) {
            LanguageType.REGULAR -> "regular"
            LanguageType.RECONSTRUCTED -> "reconstructed"
            LanguageType.APPENDIX_CONSTRUCTED -> "appendix-constructed"
            LanguageType.ETYMOLOGY_ONLY -> "etymology-only"
        }

/** The word the spec and the SQL use for this direction, as Wiktionary's modules spell it, `ltr` for none. */
internal val ScriptDirection.word: String
    get() =
        when (this) {
            ScriptDirection.LEFT_TO_RIGHT -> "ltr"
            ScriptDirection.RIGHT_TO_LEFT -> "rtl"
            ScriptDirection.VERTICAL_LEFT_TO_RIGHT -> "vertical-ltr"
            ScriptDirection.VERTICAL_RIGHT_TO_LEFT -> "vertical-rtl"
        }
