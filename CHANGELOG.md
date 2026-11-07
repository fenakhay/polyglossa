# Changelog

Notable changes to polyglossa, newest first.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). A version is the day the
data was exported from Wiktionary, `year.month.day`; the API changes only through deprecation.

## [2026.11.7]

### Added

- Script detection, matching Wiktionary's `findBestScript`. `Language.detectScript(text)` picks among the
  language's scripts; `Script.detect(text)` and `Catalog.detectScript(text)` work for any language. Swift
  has `detectScript(_:)` and `Script.detect(_:)`, Python `detect_script(text)` and `Script.detect(text)`.
- `Script.ranges`, a script's code points from `Module:scripts/data`, with `contains` and `count`.
- `Script.marks`, the characters that tell Traditional from Simplified Han, from Unicode 18.0.0's Unihan.
- `script_ranges` and `script_marks` in the SQL dump.
- Documentation at [docs.fenakhay.com/polyglossa](https://docs.fenakhay.com/polyglossa/), with examples in
  Kotlin, Java, Swift, Python, TypeScript, Go and Rust, and the Kotlin API reference.
- A logo.
- A TypeScript package, `polyglossa` on npm, with the same types: an ES module with no dependencies, for
  Node 22 and up, Deno, Bun and browsers.
- A Go module, `fenakhay.com/polyglossa/go`, for Go 1.23 and up. Its versions are `v1.` plus the
  day of the data, as in `v1.20261106.0`.
- A Rust crate, `polyglossa` on crates.io, for Rust 1.85 and up, with no dependencies.
- Static members for Java: `Language.of`, `Language.get`, `Script.detect`, `Catalog.getBUNDLED()` and the
  rest of the companion objects, plus the boolean getter `Language.isEtymologyOnly()`.

### Changed

- Each language has its own folder.
- Swift's `Language`, `LanguageFamily` and `Script` are `Sendable` structs. `===` no longer compiles; `==`
  works as before.
- `Language.etymologyOnly` is now `isEtymologyOnly`, which Java sees as `isEtymologyOnly()`.
- Swift's `Catalog.scriptNamed(_:)` is now `script(named:)`.
- Python's languages, families and scripts compare by identity.
- Python rejects an unknown script direction with `NotACatalogError` instead of reading it as `ltr`. The
  error has `line` and `reason`, all errors derive from `PolyglossaError`, and the enums are `StrEnum`s.
- Swift's `notACatalog` reason starts with the line number.

### Fixed

- An etymology-only variety whose parent is another variety, such as American English, had no `family`.
  It now takes the family of the nearest parent that has one, here English's.

## [2026.11.6]

### Added

- `Language`, `LanguageFamily`, `Script` and `Variety`: English Wiktionary's languages, etymology-only
  varieties, families and scripts, looked up with `Language.of("en")`, `Language["xx"]`,
  `Language.named("Old English")` and `"ang" in Language`, and linked to one another.
- `Catalog`, the set they come from: `Catalog.BUNDLED`, built in from `polyglossa/data/catalog.tsv`, its
  `date`, and `fromExports`, `fromTsv` and `toTsv` for others.
- `catalogDump` and `catalogCheck`, which record Wiktionary's data and say when it has changed.
  `catalogDump` rewrites the catalog only when the data has changed, adds the release to `CHANGELOG.md`,
  and fails without writing when an export cannot be read or comes back cut short.
- A weekly workflow that fetches Wiktionary's data and, only when it has changed, checks it, tags it and
  publishes the release. Run by hand with `release`, it releases what `CHANGELOG.md` has unreleased even when
  the data is the same.
- A Swift package, `Polyglossa`, with the same types, read from the same `catalog.tsv`.
- A Python package, `polyglossa`, for Python 3.11 and up, with the same types.
- `sqlDump`, which writes the catalog as SQL for SQLite, PostgreSQL and MySQL.
- `spec/expectations.json`, which the Kotlin, Swift and Python tests all check their answers against.

[2026.11.7]: https://github.com/fenakhay/polyglossa/releases/tag/2026.11.7
[2026.11.6]: https://github.com/fenakhay/polyglossa/releases/tag/2026.11.6
