# Other languages

The [API reference](../api/) documents the Kotlin library. The Swift, Python, TypeScript, Go and Rust
packages read the same `catalog.tsv` and answer as the Kotlin library does, checked against
`spec/expectations.json`; this page lists what each one calls things.

## Looking things up

| Kotlin | Swift | Python | TypeScript | Go | Rust |
|---|---|---|---|---|---|
| `Language.of("en")` | `try Language.of("en")` | `Language.of("en")`, `Language["en"]` | `Language.of("en")` | `LanguageOf("en")` | `Language::of("en")?` |
| `Language["en"]` (null when missing) | `Language["en"]` (nil) | `Language.get("en")` (None) | `Language.get("en")` (null) | `LookupLanguage("en")` (nil) | `Language::get("en")` (None) |
| `"en" in Language` | `Language.contains("en")` | `"en" in Language` | `Language.has("en")` | `LookupLanguage("en") != nil` | `Language::get("en").is_some()` |
| `Language.named("English")` | `Language.named("English")` | `Language.named("English")` | `Language.named("English")` | `LanguageNamed("English")` | `Language::named("English")` |
| `Language.all` | `Language.all` | `Language.all()` | `Language.all` | `Languages()` | `Language::all()` |
| `LanguageFamily.of`, `[]`, `in`, `all` | `LanguageFamily.of`, `[]`, `contains`, `all` | `LanguageFamily.of`, `[]`, `get`, `in`, `all()` | `LanguageFamily.of`, `get`, `has`, `all` | `FamilyOf`, `LookupFamily`, `Families` | `LanguageFamily::of`, `get`, `all` |
| `Script.of`, `[]`, `in`, `named`, `all` | `Script.of`, `[]`, `contains`, `named`, `all` | `Script.of`, `[]`, `get`, `in`, `named`, `all()` | `Script.of`, `get`, `has`, `named`, `all` | `ScriptOf`, `LookupScript`, `ScriptNamed`, `Scripts` | `Script::of`, `get`, `named`, `all` |
| `Script.detect(text)` | `Script.detect(text)` | `Script.detect(text)` | `Script.detect(text)` | `DetectScript(text)` | `Script::detect(text)` |

A lookup that must succeed fails with:

- `IllegalArgumentException` in Kotlin and Java;
- a `PolyglossaError` (`.unknownLanguage`, `.unknownFamily`, `.unknownScript`) in Swift, and a catalog that
  cannot be read throws `.notACatalog` with a reason that starts with the line;
- `UnknownLanguageError`, `UnknownFamilyError` or `UnknownScriptError`, all `KeyError`s, in Python, and a
  catalog that cannot be read raises a `NotACatalogError`, a `ValueError`, with the `line`; every one is a
  `PolyglossaError`;
- an `UnknownLanguageError`, `UnknownFamilyError` or `UnknownScriptError` with the `code` it was given in
  TypeScript, all `PolyglossaError`s, and a catalog that cannot be read throws a `NotACatalogError` with the
  `line`; what is missing is `null`, never `undefined`, and what a lookup leads to is a `readonly` array;
- an error wrapping `ErrUnknownLanguage`, `ErrUnknownFamily` or `ErrUnknownScript` in Go, for `errors.Is`;
  a catalog that cannot be read gives a `*ParseError` with the line, for `errors.As`, which wraps
  `ErrNotACatalog`;
- `Error::UnknownLanguage`, `Error::UnknownFamily` or `Error::UnknownScript` with the code in Rust.

In Swift a language, a family or a script is a `Sendable` value that points into its catalog, so a catalog
and all it gives can be shared between tasks; two are equal when they say the same. In Go the package's
functions are on the module `fenakhay.com/polyglossa/go`, imported as
`polyglossa`, and a missing text, such as a language without a Wikidata item, is `""`. In Rust every lookup
gives a `Copy` handle that borrows its catalog, `Language<'static>` for the bundled one, and what a
language, family or script leads to, such as `ancestors()` or `languages()`, comes as an iterator.

## From Java

The Kotlin library is a Java library as well, from the same Maven coordinates. Lookups are static, a boolean
reads as `is…()`, and what is missing is `null`, which the bytecode marks `@Nullable` for IDEs and NullAway:

```java
import com.fenakhay.polyglossa.Catalog;
import com.fenakhay.polyglossa.Language;
import com.fenakhay.polyglossa.Script;

Language english = Language.of("en");          // throws IllegalArgumentException when there is none
Language.get("xx");                            // null
Language.named("Old English").getCode();       // "ang"
english.getFamily().getName();                 // "Anglic"
Language.of("en-US").isEtymologyOnly();        // true
Script.detect("Ελληνικά").getCode();           // "Grek"
Catalog.getBUNDLED().getDate();                // "{{ date }}"
```

Properties are getters, as Kotlin writes them for Java: `getName()`, `getScripts()`, `getAncestors()`.
Lists and maps are `java.util.List` and `java.util.Map`, which Java should not change.

## Language

| Kotlin | Swift | Python | TypeScript | Go | Rust |
|---|---|---|---|---|---|
| `code`, `name`, `wikidata`, `aliases`, `varieties` | the same | the same | the same | `Code()` and so on | `code()` and so on |
| `type: LanguageType` | `type: LanguageType` | `type: LanguageType` | `type: LanguageType` | `Type() LanguageType` | `language_type() -> LanguageType` |
| `isEtymologyOnly` | `isEtymologyOnly` | `is_etymology_only` | `isEtymologyOnly` | `IsEtymologyOnly()` | `is_etymology_only()` |
| `family`, `scripts`, `ancestors`, `parent`, `full` | the same | the same | the same | `Family()` and so on | `family()` and so on |
| `detectScript(text)` | `detectScript(text)` | `detect_script(text)` | `detectScript(text)` | `DetectScript(text)` | `detect_script(text)` |

`LanguageType` is `REGULAR`, `RECONSTRUCTED`, `APPENDIX_CONSTRUCTED` and `ETYMOLOGY_ONLY` in Kotlin and
Python, `.regular`, `.reconstructed`, `.appendixConstructed` and `.etymologyOnly` in Swift, `Regular`,
`Reconstructed`, `AppendixConstructed` and `EtymologyOnly` in Go and Rust, and in TypeScript Wiktionary's
words themselves, a union of strings, listed in `LANGUAGE_TYPES` and checked with `isLanguageType`. Swift and
Go give each one's word, `regular` or `appendix-constructed`, as its raw value, and Rust as `as_str()`; in
Python it is a `StrEnum`, so each one equals its word. A `Variety` has `name` and `otherNames`
(`other_names` in Python and Rust, `Name` and `OtherNames` in Go).

## LanguageFamily

| Kotlin | Swift | Python | TypeScript | Go | Rust |
|---|---|---|---|---|---|
| `code`, `name`, `wikidata`, `aliases` | the same | the same | the same | `Code()` and so on | `code()` and so on |
| `parent`, `protoLanguage`, `languages` | the same | `parent`, `proto_language`, `languages` | the same as Kotlin | `Parent()`, `ProtoLanguage()`, `Languages()` | `parent()`, `proto_language()`, `languages()` |

## Script

| Kotlin | Swift | Python | TypeScript | Go | Rust |
|---|---|---|---|---|---|
| `code`, `name`, `wikidata`, `system`, `aliases`, `direction` | the same | the same | the same | `Code()` and so on | `code()` and so on |
| `parent` | `parent` | `parent` | `parent` | `Parent()` | `parent()` |
| `nameFor(language)` | `name(for: language)` | `name_for(language)` | `nameFor(language)` | `NameFor(language)` | `name_for(language)` |
| `localNames` | `localNames` | `local_names` | `localNames`, a `Map` | `LocalNames()`, a map | `local_names()`, an iterator of pairs |
| `ranges: List<IntRange>` | `ranges: [ClosedRange<Int>]` | `ranges: tuple[range, ...]` | `ranges: CodePointRange[]`, each `{ first, last }` | `Ranges() []Range`, each `{First, Last}` | `ranges() -> &[RangeInclusive<u32>]` |
| `marks` | `marks` | `marks` | `marks` | `Marks()` | `marks()` |
| `codePoint in script` | `script.contains(codePoint)` | `code_point in script` | `script.contains(codePoint)` | `script.Contains(r)` | `script.contains(character)`, a `char` |
| `count(text)` | `count(in: text)` | `count(text)` | `count(text)` | `Count(text)` | `count(text)` |

`ScriptDirection` is `LEFT_TO_RIGHT`, `RIGHT_TO_LEFT`, `VERTICAL_LEFT_TO_RIGHT` and
`VERTICAL_RIGHT_TO_LEFT` in Kotlin and Python, `.leftToRight` and so on in Swift, and `LeftToRight` and so
on in Go and Rust, with the raw values `ltr`, `rtl`, `vertical-ltr` and `vertical-rtl`. In TypeScript it
is those raw values, listed in `SCRIPT_DIRECTIONS` and checked with `isScriptDirection`.

## Catalog

| Kotlin | Swift | Python | TypeScript | Go | Rust |
|---|---|---|---|---|---|
| `Catalog.BUNDLED` | `Catalog.bundled` | `Catalog.bundled()` | `Catalog.bundled` | `Bundled()` | `Catalog::bundled()` |
| `Catalog.fromTsv(text)` | `try Catalog(tsv: text)` | `Catalog(text)` | `Catalog.fromTsv(text)` | `ParseCatalog(text)` | `Catalog::from_tsv(text)?` |
| `toTsv()`, `fromExports`, `withScriptMarks` | none | none | none | none | none |
| `date`, `languages`, `families`, `scripts` | the same | the same | the same | `Date()` and so on | `date()` and so on |
| `catalog[code]`, `code in catalog` | `catalog[code]`, `contains` | `catalog[code]`, `get`, `in` | `catalog.of(code)`, `get`, `has` | `LanguageOf(code)`, `Language(code)` | `of(code)`, `get(code)` |
| `named`, `family`, `script`, `scriptNamed` | `named`, `family`, `script`, `script(named:)` | `named`, `family`, `script`, `script_named` | the same as Kotlin | `Named`, `Family`, `Script`, `ScriptNamed` | `named`, `family`, `script`, `script_named` |
| `detectScript(text)` | `detectScript(text)` | `detect_script(text)` | `detectScript(text)` | `DetectScript(text)` | `detect_script(text)` |

Writing a catalog and reading Wiktionary's exports are the Kotlin library's alone, as that is what records
the data; the other languages read what it writes.
