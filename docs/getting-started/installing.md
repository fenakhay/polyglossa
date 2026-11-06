# Installing

Polyglossa is one library with four readers of the same data. Each holds a copy of the catalog, so none
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

=== "Swift"

    ```swift
    Catalog.bundled.date   // "{{ date }}"
    ```

=== "Python"

    ```python
    Catalog.bundled().date   # '{{ date }}'
    ```

A release comes when Wiktionary's data changes, at most once a day, or by hand for a change to the code.
The API changes only through deprecation, so taking the newest version is always safe: see
[The data and releases](../reference/data-and-releases.md).
