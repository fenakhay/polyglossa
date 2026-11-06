# Installing

polyglossa is one library with eight readers of the same data. Each holds a copy of the catalog, so none
of them reaches the network.

=== "Kotlin"

    From Maven Central, for Kotlin Multiplatform or the JVM:

    ```kotlin
    dependencies {
        implementation("com.fenakhay.polyglossa:polyglossa:{{ version }}")
    }
    ```

    It runs on the JVM (21 and up), JavaScript in Node and browsers, and natively on macOS (Apple Silicon),
    Linux (x64) and Windows (x64).

=== "Java"

    From Maven Central, for Java 21 and up. With Gradle, use the same coordinates as Kotlin; Gradle picks the
    JVM build itself. With Maven, name the JVM build:

    ```xml
    <dependency>
        <groupId>com.fenakhay.polyglossa</groupId>
        <artifactId>polyglossa-jvm</artifactId>
        <version>{{ version }}</version>
    </dependency>
    ```

    Lookups are static, as `Language.of("en")`, a boolean reads as `isEtymologyOnly()`, and what is missing is
    `null`. [The other languages](../reference/other-platforms.md#from-java) has more.

=== "Swift"

    A native Swift package, for macOS 12, iOS 15, tvOS 15, watchOS 8 and Linux, with Swift 5.9 or later:

    ```swift
    .package(url: "https://github.com/fenakhay/polyglossa", from: "{{ version }}")
    ```

    and `"Polyglossa"` among the target's dependencies.

=== "Python"

    From PyPI, for Python 3.11 and up, with nothing but the standard library:

    ```bash
    pip install polyglossa=={{ version }}
    ```

=== "TypeScript"

    From npm, for Node 22 and up, Deno, Bun and browsers, with no dependencies. The package is an ES module
    with its types included:

    ```bash
    npm install polyglossa@{{ version }}
    ```

    A userscript or a gadget can load it from a CDN instead:

    ```js
    import { Language } from "https://cdn.jsdelivr.net/npm/polyglossa@{{ version }}/dist/index.js";
    ```

=== "Go"

    From the Go module proxy, for Go 1.23 and up, with nothing but the standard library:

    ```bash
    go get github.com/fenakhay/polyglossa/go@{{ go_version }}
    ```

    The package is `polyglossa`. Its versions are `v1.` and the day of the data, as `{{ go_version }}`, since Go
    would read a first number above 1 as a new import path.

=== "Rust"

    From crates.io, for Rust 1.85 and up, with no dependencies:

    ```bash
    cargo add polyglossa@{{ version }}
    ```

=== "SQL"

    Each release on GitHub has the catalog as `polyglossa.sql`, plain SQL that SQLite, PostgreSQL and MySQL
    read, and as `catalog.tsv`:

    ```bash
    gh release download {{ version }} --repo fenakhay/polyglossa --pattern polyglossa.sql
    sqlite3 polyglossa.sqlite < polyglossa.sql
    ```

    [SQL](../guides/sql.md) has the tables.

## Versions

A version is the day the data was exported from Wiktionary, `year.month.day`: {{ version }} holds
Wiktionary's languages as they were that day, and the catalog says so at run time:

=== "Kotlin"

    ```kotlin
    Catalog.BUNDLED.date   // "{{ date }}"
    ```

=== "Java"

    ```java
    Catalog.getBUNDLED().getDate();   // "{{ date }}"
    ```

=== "Swift"

    ```swift
    Catalog.bundled.date   // "{{ date }}"
    ```

=== "Python"

    ```python
    Catalog.bundled().date   # '{{ date }}'
    ```

=== "TypeScript"

    ```ts
    Catalog.bundled.date   // "{{ date }}"
    ```

=== "Go"

    ```go
    polyglossa.Bundled().Date()   // "{{ date }}"
    ```

=== "Rust"

    ```rust
    Catalog::bundled().date()   // Some("{{ date }}")
    ```

A release comes when Wiktionary's data changes, at most once a day, or by hand for a change to the code.
The API changes only through deprecation, so taking the newest version is always safe: see
[The data and releases](../reference/data-and-releases.md).
