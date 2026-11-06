# Changelog

Notable changes to polyglossa, newest first.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). A version is the day the
data was exported from Wiktionary, `year.month.day`; the API changes only through deprecation.

## [Unreleased]

### Added

- Script detection, as Wiktionary's `findBestScript` does it: `Language.detectScript(text)` among the
  language's scripts, and `Script.detect(text)` and `Catalog.detectScript(text)` for any language. In Swift,
  `detectScript(_:)` and `Script.detect(_:)`; in Python, `detect_script(text)` and `Script.detect(text)`.
- `Script.ranges`, the code points of a script from `Module:scripts/data`, with `contains` and `count`, and
  `Script.marks`, the characters that tell Traditional from Simplified Han, from Unicode 18.0.0's Unihan.
- `script_ranges` and `script_marks` in the SQL dump.
- Documentation at [docs.fenakhay.com/polyglossa](https://docs.fenakhay.com/polyglossa/), with every example
  in Kotlin, Swift and Python and the Kotlin API reference, and a logo.

### Fixed

- `family` of an etymology-only variety whose parent is another variety, such as American English, is
  that of the nearest parent with one, here English's, instead of none.

## [2026.11.6] — 2026-11-06

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

[2026.11.6]: https://github.com/fenakhay/polyglossa/releases/tag/2026.11.6
