# Building

Polyglossa builds with Gradle and JDK 25 (the build gives Gradle its own JDK through the Foojay toolchain
resolver). The Swift package needs Swift 5.9 or later, and the Python package Python 3.11 or later.

## Checking

```bash
./gradlew check
```

`check` builds and tests the Kotlin library on every target the machine can run, runs detekt and spotless,
checks the public API against `polyglossa/api/` (`./gradlew updateKotlinAbi` records a change on purpose),
and checks that the Swift and Python copies of the catalog are the same as `polyglossa/data/catalog.tsv`.

The Swift and Python packages test themselves:

```bash
swift test
cd python && PYTHONPATH=src python -m unittest discover -s tests -v
```

## One catalog, four readers

Every reader takes the same `catalog.tsv`. `spec/expectations.json`, written from the Kotlin library with the
catalog, lists what a sample of languages, families and scripts must answer, and the texts whose script
each must detect. The Kotlin, Swift and Python tests all read it, so a change that makes one answer
differently fails the others' tests. A change to the code that changes an answer writes the spec again:

```bash
./gradlew specDump
```

## The data

```bash
./gradlew catalogCheck   # says what Wiktionary has changed since catalog.tsv, and fails if anything has
./gradlew catalogDump    # records it again, dated today, if it has changed, with a CHANGELOG.md section
./gradlew sqlDump        # writes the catalog as SQL to polyglossa-tools/build/sql/polyglossa.sql
```

`catalogDump` asks English Wiktionary's API to expand `Module:JSON data`'s exports, pausing between requests,
and downloads Unihan from unicode.org. `-Pexports=<dir>` reads the exports from files instead, as the tests
do. `catalogDump` also copies the catalog into the Swift and Python packages and rewrites the spec.
Releases are the [Catalog workflow](../reference/data-and-releases.md#releases)'s to make, so a
`catalogDump` by hand is for trying a change, not for committing.

## The documentation

This site is built with [Zensical](https://zensical.org) and the API reference with Dokka:

```bash
pip install -r .github/docs-requirements.txt
bash .github/scripts/build_docs.sh build/site/polyglossa
python3 -m http.server 8020 -d build/site
```

The pages are in `docs/`. The build script writes the version of the release each page is built for into
its install lines.
The site's root documents the latest release, and `/dev/` the `main` branch.
