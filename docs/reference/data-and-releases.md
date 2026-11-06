# The data and releases

## Where the data comes from

polyglossa's data is a copy of what English Wiktionary's
[`Module:JSON data`](https://en.wiktionary.org/wiki/Module:JSON_data) exports from `Module:languages`,
`Module:etymology languages`, `Module:families` and `Module:scripts`: every language, etymology-only
variety, family and script, with their names, links and characters. The marks that tell Traditional from
Simplified Han come from Unicode's Unihan database, version 18.0.0.

It is kept in [`data/catalog.tsv`](https://github.com/fenakhay/polyglossa/blob/main/data/catalog.tsv),
a line to each language, family or script, so a change reads as a diff. The Kotlin library compiles it in,
and the Swift, Python, TypeScript, Go and Rust packages carry copies of it, so no reader asks the wiki for
anything.

## Releases

The [Catalog workflow](https://github.com/fenakhay/polyglossa/blob/main/.github/workflows/catalog.yml)
runs every Thursday:

1. **Fetch.** It fetches Wiktionary's exports and Unihan. If an export can't be read, comes back as a Lua
   error, or loses more than a tenth of the languages, families or scripts, the run fails and nothing is
   written.
2. **Compare.** If the data is what `catalog.tsv` already holds, it stops: no release. Run by hand with
   `release` ticked, it goes on anyway, to release a change to the code that `CHANGELOG.md` lists as
   unreleased.
3. **Check.** It runs the whole of CI against the new catalog.
4. **Tag.** It commits the catalog with a `CHANGELOG.md` section, and tags the commit with the version and
   with the Go module's version.
5. **Publish.** It publishes to Maven Central, PyPI, npm and crates.io, and makes a GitHub release with the
   catalog as TSV and as SQL and its notes. Swift Package Manager and the Go proxy read the tags themselves,
   and this site is rebuilt.

When the data changes, the changelog says how much, and the release notes list each language, family and
script added, removed or changed.

## Versions

A version is the day the data was exported, `year.month.day`: `2026.11.6` holds Wiktionary's data as it was
on 6 November 2026. There is at most one release a day. The bundled catalog's `date` says the same at run
time.

The Go module's versions are `v1.` and the same day written whole, `v1.20261106.0`, tagged
`go/v1.20261106.0`: Go takes a first number above 1 for a new import path, so `v2026` would change the path
every year.

The API changes only through deprecation: what a release deprecates stays until a release at least a year
later. Taking the newest version therefore brings newer data and never breaks code that compiled, though a
language Wiktionary has since removed or renamed will no longer be found.

## Licences

The code is under the MIT licence. The data is English Wiktionary's, under
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/); its authors are in the page histories of
the modules it comes from. The marks of Han come from Unihan, under the
[Unicode License V3](https://www.unicode.org/license.txt). The
[NOTICE](https://github.com/fenakhay/polyglossa/blob/main/NOTICE) file has the full text.
