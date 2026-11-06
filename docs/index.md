---
hide:
  - navigation
---

# ![Polyglossa](brand/logo-dark.svg#only-light){ width="260" } ![Polyglossa](brand/logo-light.svg#only-dark){ width="260" }

English Wiktionary's languages, language families and scripts, for Kotlin Multiplatform, Swift, Python and
SQL. Every code Wiktionary uses, what it calls each one and how they relate, built into the library, so
nothing is asked of the wiki.

## A first look

=== "Kotlin"

    ```kotlin
    import com.fenakhay.polyglossa.Language

    val english = Language.of("en")
    english.name                                  // "English"
    english.family?.name                          // "Anglic"
    english.ancestors.map { it.name }             // [Early Modern English]
    Language.named("Old English")?.code           // "ang"
    Language.of("sh").detectScript("Москва")      // Cyrillic (Cyrl)
    ```

=== "Swift"

    ```swift
    import Polyglossa

    let english = try Language.of("en")
    english.name                                  // "English"
    english.family?.name                          // "Anglic"
    english.ancestors.map(\.name)                 // ["Early Modern English"]
    Language.named("Old English")?.code           // "ang"
    try Language.of("sh").detectScript("Москва")  // Cyrillic (Cyrl)
    ```

=== "Python"

    ```python
    from polyglossa import Language

    english = Language["en"]
    english.name                                  # 'English'
    english.family.name                           # 'Anglic'
    [a.name for a in english.ancestors]           # ['Early Modern English']
    Language.named("Old English").code            # 'ang'
    str(Language["sh"].detect_script("Москва"))   # 'Cyrillic (Cyrl)'
    ```

=== "SQL"

    ```sql
    SELECT f.name FROM languages l JOIN families f ON f.code = l.family
    WHERE l.code = 'en';                          -- Anglic
    ```

## Installing

=== "Kotlin"

    ```kotlin
    dependencies {
        implementation("com.fenakhay.polyglossa:polyglossa:{{ version }}")
    }
    ```

=== "Swift"

    ```swift
    .package(url: "https://github.com/fenakhay/polyglossa", from: "{{ version }}")
    ```

=== "Python"

    ```bash
    pip install polyglossa=={{ version }}
    ```

=== "SQL"

    ```bash
    gh release download {{ version }} --repo fenakhay/polyglossa --pattern polyglossa.sql
    sqlite3 polyglossa.sqlite < polyglossa.sql
    ```

The version is the day the data was exported from Wiktionary: {{ version }}. [Installing](getting-started/installing.md)
has the platforms each one runs on.

## Where to go next

<div class="grid cards" markdown>

-   **[Quick start](getting-started/quick-start.md)**

    Look up a language, walk its family and tell which script a word is in.

-   **[Languages](guides/languages.md)**

    Codes, names, types, etymology-only varieties, ancestors and the varieties Wiktionary names.

-   **[Detecting scripts](guides/detecting-scripts.md)**

    Which script a text is in, as Wiktionary's `findBestScript` tells it.

-   **[The data and releases](reference/data-and-releases.md)**

    Where the data comes from, how often it changes, and what a version means.

</div>
