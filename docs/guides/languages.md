# Languages

A language is one entry of Wiktionary's `Module:languages`, or one etymology-only variety from
`Module:etymology languages`. There are 8,970 of them in the bundled catalog: 7,921 regular, 295
reconstructed, 31 appendix-constructed and 723 etymology-only.

## Looking one up

By code, which is what Wiktionary's templates take, or by name, which is the heading of a language's
section in an entry:

=== "Kotlin"

    ```kotlin
    Language.of("en")                  // English (en); throws IllegalArgumentException for an unknown code
    Language["xx"]                     // null
    "ang" in Language                  // true
    Language.named("Old English")      // Old English (ang)
    Language.all.size                  // 8970
    ```

=== "Swift"

    ```swift
    try Language.of("en")              // English (en); throws PolyglossaError.unknownLanguage
    Language["xx"]                     // nil
    Language.contains("ang")           // true
    Language.named("Old English")      // Old English (ang)
    Language.all.count                 // 8970
    ```

=== "Python"

    ```python
    Language.of("en").code             # 'en'; raises UnknownLanguageError, as Language["xx"] does
    Language.get("xx")                 # None
    "ang" in Language                  # True
    Language.named("Old English").code # 'ang'
    len(Language.all())                # 8970
    ```

`named` takes the name exactly as Wiktionary writes it, with spaces around it trimmed. When a full language
and an etymology-only variety share a name, the full language wins.

## What a language holds

| Kotlin | Swift | Python | What it is |
|---|---|---|---|
| `code` | `code` | `code` | Its code: `en`, `ine-pro`, `oc-lim` |
| `name` | `name` | `name` | Its name, the heading of its section in an entry |
| `type` | `type` | `type` | Regular, reconstructed, appendix-constructed or etymology-only |
| `wikidata` | `wikidata` | `wikidata` | Its Wikidata item, `Q1860`, or none |
| `aliases` | `aliases` | `aliases` | Other names it goes by |
| `varieties` | `varieties` | `varieties` | Varieties Wiktionary names without a code |
| `family` | `family` | `family` | Its family, or none |
| `scripts` | `scripts` | `scripts` | Its scripts, most usual first |
| `ancestors` | `ancestors` | `ancestors` | The languages it descends from |
| `parent` | `parent` | `parent` | For an etymology-only variety, the language it belongs to |
| `full` | `full` | `full` | Itself, or the full language an etymology-only variety stands for |
| `etymologyOnly` | `isEtymologyOnly` | `is_etymology_only` | Whether it is named in etymologies only |

## Kinds of language

The type decides where a language's entries live on Wiktionary:

| Type | Example | Entries |
|---|---|---|
| Regular | English (`en`) | The main namespace |
| Reconstructed | Proto-Indo-European (`ine-pro`) | `Reconstruction:Proto-Indo-European/…` |
| Appendix-constructed | Belter Creole (`art-bel`) | `Appendix:Belter Creole/…` |
| Etymology-only | American English (`en-US`) | None: it is named in etymologies only |

=== "Kotlin"

    ```kotlin
    Language.of("ine-pro").type        // RECONSTRUCTED
    Language.of("en-US").etymologyOnly // true
    ```

=== "Swift"

    ```swift
    try Language.of("ine-pro").type    // .reconstructed
    try Language.of("en-US").isEtymologyOnly   // true
    ```

=== "Python"

    ```python
    Language["ine-pro"].type           # <LanguageType.RECONSTRUCTED: 'reconstructed'>
    Language["en-US"].is_etymology_only   # True
    ```

## Etymology-only varieties

An etymology-only variety, such as American English or Limousin, belongs to a language through its
`parent`, which may itself be another variety. `full` follows the parents to the language whose section the
term would go in:

=== "Kotlin"

    ```kotlin
    val american = Language.of("en-US")
    american.parent                    // North American English (en-NNN)
    american.full                      // English (en)
    american.family                    // Anglic (gmw-ang), its language's
    ```

=== "Swift"

    ```swift
    let american = try Language.of("en-US")
    american.parent                    // North American English (en-NNN)
    american.full                      // English (en)
    american.family                    // Anglic (gmw-ang), its language's
    ```

=== "Python"

    ```python
    american = Language["en-US"]
    american.parent.code               # 'en-NNN'
    american.full.code                 # 'en'
    american.family.code               # 'gmw-ang', its language's
    ```

A variety with no family of its own has that of the nearest of its parents that has one, and one with no
scripts of its own is detected in its language's scripts.

## Ancestors

`ancestors` are the languages Wiktionary says a language comes from directly. Following them walks the
history back as far as Wiktionary records it, which for English is Middle English; the family's
proto-language takes it further:

=== "Kotlin"

    ```kotlin
    generateSequence(Language.of("en")) { it.ancestors.firstOrNull() }.map { it.name }.toList()
    // [English, Early Modern English, Middle English]
    ```

=== "Swift"

    ```swift
    var language: Language? = try Language.of("en")
    var line: [String] = []
    while let current = language {
        line.append(current.name)
        language = current.ancestors.first
    }
    // ["English", "Early Modern English", "Middle English"]
    ```

=== "Python"

    ```python
    line, language = [], Language["en"]
    while language:
        line.append(language.name)
        language = language.ancestors[0] if language.ancestors else None
    line
    # ['English', 'Early Modern English', 'Middle English']
    ```

## Varieties without a code

Wiktionary names some varieties of a language, such as dialects, without giving them a code. They are
`varieties`, each a name and the other names it goes by:

=== "Kotlin"

    ```kotlin
    Language.of("de").varieties.take(3).map { it.name }   // [Alsatian German, American German, Bavarian German]
    ```

=== "Swift"

    ```swift
    try Language.of("de").varieties.prefix(3).map(\.name)   // ["Alsatian German", "American German", "Bavarian German"]
    ```

=== "Python"

    ```python
    [v.name for v in Language["de"].varieties[:3]]   # ['Alsatian German', 'American German', 'Bavarian German']
    ```
