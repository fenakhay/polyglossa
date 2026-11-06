<h1>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="brand/logo-light.svg">
    <img alt="polyglossa" src="brand/logo-dark.svg" height="48">
  </picture>
</h1>

English Wiktionary's languages, language families and scripts, for Kotlin Multiplatform, Swift and
Python, and as SQL: every code Wiktionary uses, what it calls each one, and how they relate, built into
the library so nothing is asked of the wiki.

[![Maven Central](https://img.shields.io/maven-central/v/com.fenakhay.polyglossa/polyglossa)](https://central.sonatype.com/artifact/com.fenakhay.polyglossa/polyglossa)
[![PyPI](https://img.shields.io/pypi/v/polyglossa)](https://pypi.org/project/polyglossa/)
[![Kotlin](https://img.shields.io/badge/kotlin-2.5.0--Beta2-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Swift](https://img.shields.io/badge/swift-5.9%2B-F05138.svg?logo=swift)](https://docs.fenakhay.com/polyglossa/getting-started/installing/)
[![Python](https://img.shields.io/badge/python-3.11%2B-3776AB.svg?logo=python&logoColor=white)](https://docs.fenakhay.com/polyglossa/getting-started/installing/)
[![CI](https://github.com/fenakhay/polyglossa/actions/workflows/ci.yml/badge.svg)](https://github.com/fenakhay/polyglossa/actions/workflows/ci.yml)
[![Catalog](https://github.com/fenakhay/polyglossa/actions/workflows/catalog.yml/badge.svg)](https://github.com/fenakhay/polyglossa/actions/workflows/catalog.yml)
[![Docs](https://img.shields.io/badge/docs-docs.fenakhay.com%2Fpolyglossa-0A74B8.svg)](https://docs.fenakhay.com/polyglossa/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Data](https://img.shields.io/badge/data-CC%20BY--SA%204.0-lightgrey.svg)](NOTICE)

```kotlin
val english = Language.of("en")
english.name                                  // "English"
english.family?.name                          // "Anglic"
english.ancestors.map { it.name }             // [Early Modern English]
Language.named("Old English")?.code           // "ang"
LanguageFamily.of("ine").protoLanguage?.name  // "Proto-Indo-European"
Script.of("Deva").nameFor(Language.of("mr"))  // "Balbodh"
Language.of("zh").detectScript("語言")         // Traditional Han (Hant)
```

## Install

```kotlin
dependencies {
    implementation("com.fenakhay.polyglossa:polyglossa:2026.11.6")
}
```

```swift
.package(url: "https://github.com/fenakhay/polyglossa", from: "2026.11.6")
```

```bash
pip install polyglossa
```

The Kotlin library runs on the JVM (21 and up), JavaScript and natively on macOS, Linux and Windows; the
Swift package on macOS 12, iOS 15, tvOS 15, watchOS 8 and Linux; the Python package on Python 3.11 and up.
Each release also has the catalog as plain SQL for SQLite, PostgreSQL and MySQL.

## Documentation

The documentation is at **[docs.fenakhay.com/polyglossa](https://docs.fenakhay.com/polyglossa/)**, with
every example in Kotlin, Swift and Python:

- [Quick start](https://docs.fenakhay.com/polyglossa/getting-started/quick-start/) and
  [Installing](https://docs.fenakhay.com/polyglossa/getting-started/installing/)
- [Languages](https://docs.fenakhay.com/polyglossa/guides/languages/),
  [Families](https://docs.fenakhay.com/polyglossa/guides/families/) and
  [Scripts](https://docs.fenakhay.com/polyglossa/guides/scripts/)
- [Detecting scripts](https://docs.fenakhay.com/polyglossa/guides/detecting-scripts/)
- [Catalogs](https://docs.fenakhay.com/polyglossa/guides/catalogs/) and
  [SQL](https://docs.fenakhay.com/polyglossa/guides/sql/)
- [The data and releases](https://docs.fenakhay.com/polyglossa/reference/data-and-releases/)
- [API reference](https://docs.fenakhay.com/polyglossa/api/) and
  [Swift and Python](https://docs.fenakhay.com/polyglossa/reference/swift-and-python/)
- [Building](https://docs.fenakhay.com/polyglossa/contributing/building/)

## Licence

The code is under the MIT licence. The data is English Wiktionary's, under
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/), with the marks of Han from Unihan, under
the [Unicode License V3](https://www.unicode.org/license.txt); see [NOTICE](NOTICE).
