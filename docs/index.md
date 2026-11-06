---
hide:
  - navigation
---

# ![polyglossa](brand/logo-dark.svg#only-light){ width="260" } ![polyglossa](brand/logo-light.svg#only-dark){ width="260" }

English Wiktionary's languages, language families and scripts, for Kotlin Multiplatform, Java, Swift,
Python, TypeScript, Go, Rust and SQL. Every code Wiktionary uses, what it calls each one and how they
relate, built into the library, so nothing is asked of the wiki.

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

=== "Java"

    ```java
    import com.fenakhay.polyglossa.Language;

    Language english = Language.of("en");
    english.getName();                                  // "English"
    english.getFamily().getName();                      // "Anglic"
    english.getAncestors().get(0).getName();            // "Early Modern English"
    Language.named("Old English").getCode();            // "ang"
    Language.of("sh").detectScript("Москва");           // Cyrillic (Cyrl)
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

=== "TypeScript"

    ```ts
    import { Language } from "polyglossa";

    const english = Language.of("en");
    english.name                                       // "English"
    english.family?.name                               // "Anglic"
    english.ancestors.map((a) => a.name)               // ["Early Modern English"]
    Language.named("Old English")?.code                // "ang"
    String(Language.of("sh").detectScript("Москва"))   // "Cyrillic (Cyrl)"
    ```

=== "Go"

    ```go
    import "fenakhay.com/polyglossa/go"

    english := polyglossa.LookupLanguage("en")
    english.Name()                                                   // "English"
    english.Family().Name()                                          // "Anglic"
    english.Ancestors()[0].Name()                                    // "Early Modern English"
    polyglossa.LanguageNamed("Old English").Code()                   // "ang"
    polyglossa.LookupLanguage("sh").DetectScript("Москва").String()  // "Cyrillic (Cyrl)"
    ```

=== "Rust"

    ```rust
    use polyglossa::Language;

    let english = Language::of("en")?;
    english.name()                                                     // "English"
    english.family().map(|f| f.name())                                 // Some("Anglic")
    english.ancestors().next().map(|a| a.name())                       // Some("Early Modern English")
    Language::named("Old English").map(|l| l.code())                   // Some("ang")
    Language::of("sh")?.detect_script("Москва").map(|s| s.to_string())  // Some("Cyrillic (Cyrl)")
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

=== "Java"

    ```xml
    <dependency>
        <groupId>com.fenakhay.polyglossa</groupId>
        <artifactId>polyglossa-jvm</artifactId>
        <version>{{ version }}</version>
    </dependency>
    ```

=== "Swift"

    ```swift
    .package(url: "https://github.com/fenakhay/polyglossa", from: "{{ version }}")
    ```

=== "Python"

    ```bash
    pip install polyglossa=={{ version }}
    ```

=== "TypeScript"

    ```bash
    npm install polyglossa@{{ version }}
    ```

=== "Go"

    ```bash
    go get fenakhay.com/polyglossa/go@{{ go_version }}
    ```

=== "Rust"

    ```bash
    cargo add polyglossa@{{ version }}
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
