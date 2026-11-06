# Families

A family is one entry of Wiktionary's `Module:families`: Indo-European (`ine`), West Germanic (`gmw`),
Anglic (`gmw-ang`). There are 1,246 of them. Families nest through `parent`, and a family leads to the
languages directly in it and to the language it descends from.

=== "Kotlin"

    ```kotlin
    import com.fenakhay.polyglossa.LanguageFamily

    val germanic = LanguageFamily.of("gmw")
    germanic.name                      // "West Germanic"
    germanic.parent                    // Germanic (gem)
    germanic.protoLanguage             // Proto-West Germanic (gmw-pro)
    LanguageFamily.of("gmw-ang").languages.take(3)   // [Old English (ang), English (en), Middle English (enm)]
    LanguageFamily["xx"]               // null
    ```

=== "Swift"

    ```swift
    import Polyglossa

    let germanic = try LanguageFamily.of("gmw")
    germanic.name                      // "West Germanic"
    germanic.parent                    // Germanic (gem)
    germanic.protoLanguage             // Proto-West Germanic (gmw-pro)
    try LanguageFamily.of("gmw-ang").languages.prefix(3)   // [Old English (ang), English (en), Middle English (enm)]
    LanguageFamily["xx"]               // nil
    ```

=== "Python"

    ```python
    from polyglossa import LanguageFamily

    germanic = LanguageFamily["gmw"]
    germanic.name                      # 'West Germanic'
    germanic.parent.code               # 'gem'
    germanic.proto_language.code       # 'gmw-pro'
    [l.code for l in LanguageFamily["gmw-ang"].languages[:3]]   # ['ang', 'en', 'enm']
    LanguageFamily.get("xx")           # None
    ```

| Kotlin | Swift | Python | What it is |
|---|---|---|---|
| `code`, `name`, `wikidata`, `aliases` | the same | the same | As for a language |
| `parent` | `parent` | `parent` | The family it belongs to, or none |
| `protoLanguage` | `protoLanguage` | `proto_language` | The language it descends from, or none |
| `languages` | `languages` | `languages` | The languages directly in it, not those of the families under it |

## The proto-language

Wiktionary names a family's proto-language when it has one. When it doesn't, polyglossa looks for a
language with the family's code and `-pro`, as `ine` has `ine-pro`. Some families descend from an attested
language instead: Arabic the family (`sem-arb`) descends from Arabic the language (`ar`).

=== "Kotlin"

    ```kotlin
    LanguageFamily.of("ine").protoLanguage      // Proto-Indo-European (ine-pro)
    LanguageFamily.of("sem-arb").protoLanguage  // Arabic (ar)
    ```

=== "Swift"

    ```swift
    try LanguageFamily.of("ine").protoLanguage      // Proto-Indo-European (ine-pro)
    try LanguageFamily.of("sem-arb").protoLanguage  // Arabic (ar)
    ```

=== "Python"

    ```python
    LanguageFamily["ine"].proto_language.code      # 'ine-pro'
    LanguageFamily["sem-arb"].proto_language.code  # 'ar'
    ```

## Every language of a family, at every depth

`languages` holds a family's own languages. Those of the families under it take a walk up from every
language's family. Stop at a family already seen: Wiktionary makes "not a family" (`qfa-not`), which
holds isolates and constructed languages, its own parent.

=== "Kotlin"

    ```kotlin
    fun LanguageFamily.within(other: LanguageFamily): Boolean {
        val seen = mutableSetOf<String>()
        return generateSequence(this) { it.parent }.takeWhile { seen.add(it.code) }.any { it == other }
    }

    val germanic = LanguageFamily.of("gem")
    Language.all.filter { language -> language.family?.within(germanic) == true }
    ```

=== "Swift"

    ```swift
    func within(_ family: LanguageFamily?, _ other: LanguageFamily) -> Bool {
        var current = family
        var seen: Set<String> = []
        while let next = current, seen.insert(next.code).inserted {
            if next == other { return true }
            current = next.parent
        }
        return false
    }

    let germanic = try LanguageFamily.of("gem")
    Language.all.filter { within($0.family, germanic) }
    ```

=== "Python"

    ```python
    def within(family, other):
        seen = set()
        while family is not None and family.code not in seen:
            if family == other:
                return True
            seen.add(family.code)
            family = family.parent
        return False

    germanic = LanguageFamily["gem"]
    [l for l in Language.all() if within(l.family, germanic)]
    ```
