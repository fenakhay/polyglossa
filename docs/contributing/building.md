# Building

polyglossa builds with Gradle and JDK 25 (the build gives Gradle its own JDK through the Foojay toolchain
resolver). The Swift package needs Swift 5.9 or later, and its tests, written with Swift Testing, Swift 6; the
Python package needs Python 3.11 or later, the TypeScript package Node 22 or later, the Go module Go 1.23 or
later, and the Rust crate Rust 1.85 or later.

## The repository

| Folder | What it holds |
|---|---|
| `data/` | `catalog.tsv`, the data every package reads |
| `spec/` | `expectations.json`, what every package must answer |
| `kotlin/` | The Kotlin library |
| `tools/` | The tools, in Kotlin, that record the data and write the spec and the SQL |
| `swift/` | The Swift package's sources and tests, which `Package.swift`, at the root as SwiftPM needs, points at |
| `python/`, `typescript/`, `go/`, `rust/` | The other packages, each with its own copy of the catalog |
| `docs/`, `brand/` | This site and the logo |

The Gradle build that builds the Kotlin library and runs the tools is at the root, with `build-logic/`,
`gradle/` and `config/`.

Each package keeps a copy of `data/catalog.tsv` because each is published from its own folder: SwiftPM and
the Go proxy read the tagged repository, and npm, PyPI and crates.io take only what is in the folder. Git
stores the copies once, as one blob.

## Checking

```bash
./gradlew check
```

`check` builds and tests the Kotlin library on every target the machine can run, runs detekt and spotless,
checks the public API against `kotlin/api/` (`./gradlew updateKotlinAbi` records a change on
purpose), and checks that every package's copy of the catalog is the same as `data/catalog.tsv`.

`check` builds every target, so after an edit it takes about twenty seconds where the JVM tests alone take
five. Run one target's tests while working, and `check` before pushing:

```bash
./gradlew :polyglossa:jvmTest
./gradlew :polyglossa:macosArm64Test
```

The other packages test themselves:

```bash
swift test
cd python && pip install --group dev --editable . && ruff check src tests && mypy && pytest
cd typescript && npm ci && npm test
cd go && go test ./...
cd rust && cargo test
```

## One catalog, six readers

Every reader takes the same `catalog.tsv`. `spec/expectations.json`, written from the Kotlin library with the
catalog, lists what a sample of languages, families and scripts must answer, and the texts whose script
each must detect. The tests of all six read it, so a change that makes one answer differently fails the
others' tests. A change to the code that changes an answer writes the spec again:

```bash
./gradlew specDump
```

## The data

The tools in `tools/`, a Kotlin program on the Kotlin library, record it:

```bash
./gradlew catalogCheck   # says what Wiktionary has changed since catalog.tsv, and fails if anything has
./gradlew catalogDump    # records it again, dated today, if it has changed, with a CHANGELOG.md section
./gradlew sqlDump        # writes the catalog as SQL to tools/build/sql/polyglossa.sql
```

`catalogDump` asks English Wiktionary's API to expand `Module:JSON data`'s exports, pausing between requests,
and downloads Unihan from unicode.org. `-Pexports=<dir>` reads the exports from files instead, as the tests
do. `catalogDump` also copies the catalog into the other packages and rewrites the spec.
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
