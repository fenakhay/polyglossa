# Swift and Python

The [API reference](../api/) documents the Kotlin library. The Swift package and the Python package read
the same `catalog.tsv` and answer as the Kotlin library does, checked against `spec/expectations.json`; this
page lists what each one calls things.

## Looking things up

| Kotlin | Swift | Python |
|---|---|---|
| `Language.of("en")` | `try Language.of("en")` | `Language.of("en")`, `Language["en"]` |
| `Language["en"]` (null when missing) | `Language["en"]` (nil) | `Language.get("en")` (None) |
| `"en" in Language` | `Language.contains("en")` | `"en" in Language` |
| `Language.named("English")` | `Language.named("English")` | `Language.named("English")` |
| `Language.all` | `Language.all` | `Language.all()` |
| `LanguageFamily.of`, `[]`, `in`, `all` | `LanguageFamily.of`, `[]`, `contains`, `all` | `LanguageFamily.of`, `[]`, `get`, `in`, `all()` |
| `Script.of`, `[]`, `in`, `named`, `all` | `Script.of`, `[]`, `contains`, `named`, `all` | `Script.of`, `[]`, `get`, `in`, `named`, `all()` |
| `Script.detect(text)` | `Script.detect(text)` | `Script.detect(text)` |

A lookup that must succeed fails with `IllegalArgumentException` in Kotlin, a `PolyglossaError`
(`.unknownLanguage`, `.unknownFamily`, `.unknownScript`) in Swift, and `UnknownLanguageError`,
`UnknownFamilyError` or `UnknownScriptError`, all `KeyError`s, in Python.

## Language

| Kotlin | Swift | Python |
|---|---|---|
| `code`, `name`, `wikidata`, `aliases`, `varieties` | the same | the same |
| `type: LanguageType` | `type: LanguageType` | `type: LanguageType` |
| `etymologyOnly` | `isEtymologyOnly` | `is_etymology_only` |
| `family`, `scripts`, `ancestors`, `parent`, `full` | the same | the same |
| `detectScript(text)` | `detectScript(text)` | `detect_script(text)` |

`LanguageType` is `REGULAR`, `RECONSTRUCTED`, `APPENDIX_CONSTRUCTED` and `ETYMOLOGY_ONLY` in Kotlin and
Python, and `.regular`, `.reconstructed`, `.appendixConstructed` and `.etymologyOnly` in Swift. In Swift and
Python each case's raw value is Wiktionary's word for it: `regular`, `appendix-constructed`. A `Variety` has
`name` and `otherNames` (`other_names` in Python).

## LanguageFamily

| Kotlin | Swift | Python |
|---|---|---|
| `code`, `name`, `wikidata`, `aliases` | the same | the same |
| `parent`, `protoLanguage`, `languages` | the same | `parent`, `proto_language`, `languages` |

## Script

| Kotlin | Swift | Python |
|---|---|---|
| `code`, `name`, `wikidata`, `system`, `aliases`, `direction` | the same | the same |
| `parent` | `parent` | `parent` |
| `nameFor(language)` | `name(for: language)` | `name_for(language)` |
| `localNames` | `localNames` | `local_names` |
| `ranges: List<IntRange>` | `ranges: [ClosedRange<Int>]` | `ranges: tuple[range, ...]` |
| `marks` | `marks` | `marks` |
| `codePoint in script` | `script.contains(codePoint)` | `code_point in script` |
| `count(text)` | `count(text)` | `count(text)` |

`ScriptDirection` is `LEFT_TO_RIGHT`, `RIGHT_TO_LEFT`, `VERTICAL_LEFT_TO_RIGHT` and
`VERTICAL_RIGHT_TO_LEFT` in Kotlin and Python, and `.leftToRight` and so on in Swift, with the raw values
`ltr`, `rtl`, `vertical-ltr` and `vertical-rtl`.

## Catalog

| Kotlin | Swift | Python |
|---|---|---|
| `Catalog.BUNDLED` | `Catalog.bundled` | `Catalog.bundled()` |
| `Catalog.fromTsv(text)` | `try Catalog(tsv: text)` | `Catalog(text)` |
| `toTsv()`, `fromExports`, `withScriptMarks` | none | none |
| `date`, `languages`, `families`, `scripts` | the same | the same |
| `catalog[code]`, `code in catalog` | `catalog[code]`, `contains` | `catalog[code]`, `get`, `in` |
| `named`, `family`, `script`, `scriptNamed` | the same | `named`, `family`, `script`, `script_named` |
| `detectScript(text)` | `detectScript(text)` | `detect_script(text)` |

Writing a catalog and reading Wiktionary's exports are the Kotlin library's alone, as that is what records
the data; Swift and Python read what it writes.
