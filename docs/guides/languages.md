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

=== "Java"

    ```java
    Language.of("en");                   // English (en); throws IllegalArgumentException for an unknown code
    Language.get("xx");                  // null
    Language.contains("ang");            // true
    Language.named("Old English");       // Old English (ang)
    Language.getAll().size();            // 8970
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

=== "TypeScript"

    ```ts
    Language.of("en").code              // "en"; throws UnknownLanguageError for "xx"
    Language.get("xx")                  // null
    Language.has("ang")                 // true
    Language.named("Old English")?.code // "ang"
    Language.all.length                 // 8970
    ```

=== "Go"

    ```go
    english, err := polyglossa.LanguageOf("en")   // English (en); err wraps ErrUnknownLanguage for "xx"
    polyglossa.LookupLanguage("xx")               // nil
    polyglossa.LookupLanguage("ang") != nil       // true
    polyglossa.LanguageNamed("Old English").Code()   // "ang"
    len(polyglossa.Languages())                   // 8970
    ```

=== "Rust"

    ```rust
    Language::of("en")?.code()                          // "en"; Err(Error::UnknownLanguage("xx")) for "xx"
    Language::get("xx")                                 // None
    Language::get("ang").is_some()                      // true
    Language::named("Old English").map(|l| l.code())    // Some("ang")
    Language::all().len()                               // 8970
    ```

`named` takes the name exactly as Wiktionary writes it, with spaces around it trimmed. When a full language
and an etymology-only variety share a name, the full language wins.

## What a language holds

| Kotlin | Java | Swift | Python | TypeScript | Go | Rust | What it is |
|---|---|---|---|---|---|---|---|
| `code` | `getCode()` | `code` | `code` | `code` | `Code()` | `code()` | Its code: `en`, `ine-pro`, `oc-lim` |
| `name` | `getName()` | `name` | `name` | `name` | `Name()` | `name()` | Its name, the heading of its section in an entry |
| `type` | `getType()` | `type` | `type` | `type` | `Type()` | `language_type()` | Regular, reconstructed, appendix-constructed or etymology-only |
| `wikidata` | `getWikidata()` | `wikidata` | `wikidata` | `wikidata` | `Wikidata()` | `wikidata()` | Its Wikidata item, `Q1860`, or none |
| `aliases` | `getAliases()` | `aliases` | `aliases` | `aliases` | `Aliases()` | `aliases()` | Other names it goes by |
| `varieties` | `getVarieties()` | `varieties` | `varieties` | `varieties` | `Varieties()` | `varieties()` | Varieties Wiktionary names without a code |
| `family` | `getFamily()` | `family` | `family` | `family` | `Family()` | `family()` | Its family, or none |
| `scripts` | `getScripts()` | `scripts` | `scripts` | `scripts` | `Scripts()` | `scripts()` | Its scripts, most usual first |
| `ancestors` | `getAncestors()` | `ancestors` | `ancestors` | `ancestors` | `Ancestors()` | `ancestors()` | The languages it descends from |
| `parent` | `getParent()` | `parent` | `parent` | `parent` | `Parent()` | `parent()` | For an etymology-only variety, the language it belongs to |
| `full` | `getFull()` | `full` | `full` | `full` | `Full()` | `full()` | Itself, or the full language an etymology-only variety stands for |
| `isEtymologyOnly` | `isEtymologyOnly()` | `isEtymologyOnly` | `is_etymology_only` | `isEtymologyOnly` | `IsEtymologyOnly()` | `is_etymology_only()` | Whether it is named in etymologies only |

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
    Language.of("en-US").isEtymologyOnly // true
    ```

=== "Java"

    ```java
    Language.of("ine-pro").getType();          // RECONSTRUCTED
    Language.of("en-US").isEtymologyOnly();    // true
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

=== "TypeScript"

    ```ts
    Language.of("ine-pro").type            // "reconstructed"
    Language.of("en-US").isEtymologyOnly   // true
    ```

=== "Go"

    ```go
    polyglossa.LookupLanguage("ine-pro").Type()            // polyglossa.Reconstructed, "reconstructed"
    polyglossa.LookupLanguage("en-US").IsEtymologyOnly()   // true
    ```

=== "Rust"

    ```rust
    Language::of("ine-pro")?.language_type()     // LanguageType::Reconstructed
    Language::of("en-US")?.is_etymology_only()   // true
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

=== "Java"

    ```java
    Language american = Language.of("en-US");
    american.getParent();                // North American English (en-NNN)
    american.getFull();                  // English (en)
    american.getFamily();                // Anglic (gmw-ang), its language's
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

=== "TypeScript"

    ```ts
    const american = Language.of("en-US");
    american.parent?.code              // "en-NNN"
    american.full.code                 // "en"
    american.family?.code              // "gmw-ang", its language's
    ```

=== "Go"

    ```go
    american := polyglossa.LookupLanguage("en-US")
    american.Parent().Code()           // "en-NNN"
    american.Full().Code()             // "en"
    american.Family().Code()           // "gmw-ang", its language's
    ```

=== "Rust"

    ```rust
    let american = Language::of("en-US")?;
    american.parent().map(|l| l.code())   // Some("en-NNN")
    american.full().code()                // "en"
    american.family().map(|f| f.code())   // Some("gmw-ang"), its language's
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

=== "Java"

    ```java
    List<String> line = new ArrayList<>();
    for (Language language = Language.of("en"); language != null;
         language = language.getAncestors().isEmpty() ? null : language.getAncestors().get(0)) {
        line.add(language.getName());
    }
    // line is [English, Early Modern English, Middle English]
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

=== "TypeScript"

    ```ts
    const line: string[] = [];
    for (let language: Language | undefined = Language.of("en"); language; language = language.ancestors[0]) {
      line.push(language.name);
    }
    line
    // ["English", "Early Modern English", "Middle English"]
    ```

=== "Go"

    ```go
    var line []string
    for language := polyglossa.LookupLanguage("en"); language != nil; {
        line = append(line, language.Name())
        ancestors := language.Ancestors()
        if len(ancestors) == 0 {
            break
        }
        language = ancestors[0]
    }
    line
    // [English Early Modern English Middle English]
    ```

=== "Rust"

    ```rust
    let mut line = Vec::new();
    let mut language = Language::get("en");
    while let Some(current) = language {
        line.push(current.name());
        language = current.ancestors().next();
    }
    line
    // ["English", "Early Modern English", "Middle English"]
    ```

## Varieties without a code

Wiktionary names some varieties of a language, such as dialects, without giving them a code. They are
`varieties`, each a name and the other names it goes by:

=== "Kotlin"

    ```kotlin
    Language.of("de").varieties.take(3).map { it.name }   // [Alsatian German, American German, Bavarian German]
    ```

=== "Java"

    ```java
    Language.of("de").getVarieties().subList(0, 3).stream().map(Variety::getName).toList();   // [Alsatian German, American German, Bavarian German]
    ```

=== "Swift"

    ```swift
    try Language.of("de").varieties.prefix(3).map(\.name)   // ["Alsatian German", "American German", "Bavarian German"]
    ```

=== "Python"

    ```python
    [v.name for v in Language["de"].varieties[:3]]   # ['Alsatian German', 'American German', 'Bavarian German']
    ```

=== "TypeScript"

    ```ts
    Language.of("de").varieties.slice(0, 3).map((v) => v.name)   // ["Alsatian German", "American German", "Bavarian German"]
    ```

=== "Go"

    ```go
    polyglossa.LookupLanguage("de").Varieties()[0].Name   // "Alsatian German", then American and Bavarian German
    ```

=== "Rust"

    ```rust
    Language::of("de")?.varieties()[..3].iter().map(|v| v.name.as_str()).collect::<Vec<_>>()   // ["Alsatian German", "American German", "Bavarian German"]
    ```
