# Polyglossa

English Wiktionary's languages, language families and scripts, for Kotlin Multiplatform, Swift and
Python, and as SQL: every code Wiktionary uses, what it calls each one, and how they relate, built into
the library so nothing is asked of the wiki.

[![Maven Central](https://img.shields.io/maven-central/v/com.fenakhay.polyglossa/polyglossa)](https://central.sonatype.com/artifact/com.fenakhay.polyglossa/polyglossa)
[![PyPI](https://img.shields.io/pypi/v/polyglossa)](https://pypi.org/project/polyglossa/)
[![Kotlin](https://img.shields.io/badge/kotlin-2.5.0--Beta2-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Swift](https://img.shields.io/badge/swift-5.9%2B-F05138.svg?logo=swift)](#swift)
[![Python](https://img.shields.io/badge/python-3.11%2B-3776AB.svg?logo=python&logoColor=white)](#python)
[![CI](https://github.com/fenakhay/polyglossa/actions/workflows/ci.yml/badge.svg)](https://github.com/fenakhay/polyglossa/actions/workflows/ci.yml)
[![Catalog](https://github.com/fenakhay/polyglossa/actions/workflows/catalog.yml/badge.svg)](https://github.com/fenakhay/polyglossa/actions/workflows/catalog.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Data](https://img.shields.io/badge/data-CC%20BY--SA%204.0-lightgrey.svg)](NOTICE)

```kotlin
val english = Language.of("en")
english.name                                  // "English"
english.family?.name                          // "Anglic"
english.ancestors.map { it.name }             // [Early Modern English]
english.scripts.first().name                  // "Latin"
Language["xx"]                                // null: no such language
Language.named("Old English")?.code           // "ang"
"ang" in Language                             // true
Language.of("en-ear").full                    // English, for an etymology-only variety
Language.of("de").varieties.map { it.name }   // [Alsatian German, American German, …]

LanguageFamily.of("ine").protoLanguage?.name  // "Proto-Indo-European"
LanguageFamily.of("gmw-ang").languages        // the languages directly in it

Script.named("Arabic")?.direction             // RIGHT_TO_LEFT
Script.of("Hans").parent?.name                // "Han"
Script.of("Deva").nameFor(Language.of("mr"))  // "Balbodh"
```

Links between languages, families and scripts are the objects themselves: `family`, `ancestors`,
`parent`, `full`, `scripts`, `protoLanguage`. Lookups that may miss return `null`; `of` throws for a code
Wiktionary does not have.

## Install

```kotlin
dependencies {
    implementation("com.fenakhay.polyglossa:polyglossa:2026.11.6")
}
```

It runs on the JVM (21 and up), JavaScript and natively on macOS, Linux and Windows.

## Swift

A native Swift package, for macOS 12, iOS 15, tvOS 15, watchOS 8 and Linux:

```swift
.package(url: "https://github.com/fenakhay/polyglossa", from: "2026.11.6")
```

```swift
import Polyglossa

let english = try Language.of("en")
english.name                          // "English"
english.family?.name                  // Optional("Anglic")
Language["xx"]                        // nil
Language.named("Old English")?.code   // Optional("ang")
Catalog.bundled.date                  // Optional("2026-11-06")
```

## Python

Python 3.11 and up, with nothing but the standard library:

```bash
pip install polyglossa
```

```python
from polyglossa import Language, LanguageFamily

english = Language["en"]
english.name                                      # 'English'
english.family.name                               # 'Anglic'
Language.get("xx")                                # None
"ang" in Language                                 # True
LanguageFamily.of("ine").proto_language.name      # 'Proto-Indo-European'
```

## SQL

The catalog as plain SQL, which SQLite, PostgreSQL and MySQL all read: `languages`, `families` and
`scripts`, with each list (a language's scripts, ancestors, aliases and varieties) a table of its own
ordered by `position`.

```bash
./gradlew sqlDump
sqlite3 polyglossa.sqlite < polyglossa-tools/build/sql/polyglossa.sql
```

```sql
SELECT s.name FROM language_scripts ls JOIN scripts s ON s.code = ls.script
WHERE ls.language = 'ar' ORDER BY ls.position;
```

## One catalog, four readers

Every reader takes the same `catalog.tsv`: `catalogDump` copies it into the Swift package
(`Sources/Polyglossa/Resources`) and the Python package (`python/src/polyglossa`), and `check` fails if a
copy differs. [`spec/expectations.json`](spec/expectations.json), written from the Kotlin library with
the catalog, lists what a sample of languages, families and scripts must answer; the Kotlin, Swift and
Python tests all read it, so the three libraries answer alike.

Polyglossa is versioned by the day its data was exported from Wiktionary: `2026.11.6` holds Wiktionary's
languages as they were on 6 November 2026, and `Catalog.BUNDLED.date` says so at run time. There is at most
one release a day, made when the data changes or by hand (see [The data](#the-data)). The API changes only
through deprecation: what a release deprecates stays until a release at least a year later.

## The data

Polyglossa's data is a copy of what English Wiktionary's
[`Module:JSON data`](https://en.wiktionary.org/wiki/Module:JSON_data) exports from `Module:languages`,
`Module:etymology languages`, `Module:families` and `Module:scripts`. It is kept as tab-separated lines
in [`polyglossa/data/catalog.tsv`](polyglossa/data/catalog.tsv), a language, a family or a script to a line, so a
change to it reads as a diff, and compiled into the library.

```bash
./gradlew catalogCheck   # says what Wiktionary has changed since, and fails if anything has
./gradlew catalogDump    # records it again, dated today, if it has changed: the next release's version
./gradlew specDump       # writes spec/expectations.json again from catalog.tsv as it is
```

Releases are made by the [Catalog workflow](.github/workflows/catalog.yml), every Thursday:

1. It fetches Wiktionary's exports with `catalogDump`. The run fails if an export cannot be read, comes
   back as a Lua error, or loses more than a tenth of the languages, families or scripts, and nothing is
   written.
2. If the data is what `catalog.tsv` already has, it stops there: no release. Run by hand with `release`
   ticked, it goes on, records the catalog dated today and releases what `CHANGELOG.md` has unreleased, such
   as a change to the code; with nothing unreleased, the run fails.
3. When there is a release, it runs the whole of CI against the new catalog, then commits it with its
   `CHANGELOG.md` section and tags the commit with the version, the day of the export.
4. From that tag it publishes to Maven Central and PyPI and makes a GitHub release with the catalog as
   TSV and as SQL and its `CHANGELOG.md` section, with what changed in the data, as its notes. Swift Package Manager reads the tag itself.

## Licence

The code is under the MIT licence. The data is English Wiktionary's, under
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/); see [NOTICE](NOTICE).
