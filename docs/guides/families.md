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

=== "Java"

    ```java
    import com.fenakhay.polyglossa.LanguageFamily;

    LanguageFamily germanic = LanguageFamily.of("gmw");
    germanic.getName();                      // "West Germanic"
    germanic.getParent();                    // Germanic (gem)
    germanic.getProtoLanguage();             // Proto-West Germanic (gmw-pro)
    LanguageFamily.of("gmw-ang").getLanguages().subList(0, 3);   // [Old English (ang), English (en), Middle English (enm)]
    LanguageFamily.get("xx");                // null
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

=== "TypeScript"

    ```ts
    import { LanguageFamily } from "polyglossa";

    const germanic = LanguageFamily.of("gmw");
    germanic.name                      // "West Germanic"
    germanic.parent?.code              // "gem"
    germanic.protoLanguage?.code       // "gmw-pro"
    LanguageFamily.of("gmw-ang").languages.slice(0, 3).map((l) => l.code)   // ["ang", "en", "enm"]
    LanguageFamily.get("xx")           // null
    ```

=== "Go"

    ```go
    import "fenakhay.com/polyglossa/go"

    germanic := polyglossa.LookupFamily("gmw")
    germanic.Name()                    // "West Germanic"
    germanic.Parent().Code()           // "gem"
    germanic.ProtoLanguage().Code()    // "gmw-pro"
    polyglossa.LookupFamily("gmw-ang").Languages()[:3]   // [Old English (ang) English (en) Middle English (enm)]
    polyglossa.LookupFamily("xx")      // nil
    ```

=== "Rust"

    ```rust
    use polyglossa::LanguageFamily;

    let germanic = LanguageFamily::of("gmw")?;
    germanic.name()                                   // "West Germanic"
    germanic.parent().map(|f| f.code())               // Some("gem")
    germanic.proto_language().map(|l| l.code())       // Some("gmw-pro")
    LanguageFamily::of("gmw-ang")?.languages().take(3).map(Language::code).collect::<Vec<_>>()   // ["ang", "en", "enm"]
    LanguageFamily::get("xx")                         // None
    ```

| Kotlin | Java | Swift | Python | TypeScript | Go | Rust | What it is |
|---|---|---|---|---|---|---|---|
| `code`, `name`, `wikidata`, `aliases` | `getCode()`, `getName()`, `getWikidata()`, `getAliases()` | the same | the same | the same | `Code()`, `Name()`, `Wikidata()`, `Aliases()` | `code()`, `name()`, `wikidata()`, `aliases()` | As for a language |
| `parent` | `getParent()` | `parent` | `parent` | `parent` | `Parent()` | `parent()` | The family it belongs to, or none |
| `protoLanguage` | `getProtoLanguage()` | `protoLanguage` | `proto_language` | `protoLanguage` | `ProtoLanguage()` | `proto_language()` | The language it descends from, or none |
| `languages` | `getLanguages()` | `languages` | `languages` | `languages` | `Languages()` | `languages()` | The languages directly in it, not those of the families under it |

## The proto-language

Wiktionary names a family's proto-language when it has one. When it doesn't, polyglossa looks for a
language with the family's code and `-pro`, as `ine` has `ine-pro`. Some families descend from an attested
language instead: Arabic the family (`sem-arb`) descends from Arabic the language (`ar`).

=== "Kotlin"

    ```kotlin
    LanguageFamily.of("ine").protoLanguage      // Proto-Indo-European (ine-pro)
    LanguageFamily.of("sem-arb").protoLanguage  // Arabic (ar)
    ```

=== "Java"

    ```java
    LanguageFamily.of("ine").getProtoLanguage();      // Proto-Indo-European (ine-pro)
    LanguageFamily.of("sem-arb").getProtoLanguage();  // Arabic (ar)
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

=== "TypeScript"

    ```ts
    LanguageFamily.of("ine").protoLanguage?.code      // "ine-pro"
    LanguageFamily.of("sem-arb").protoLanguage?.code  // "ar"
    ```

=== "Go"

    ```go
    polyglossa.LookupFamily("ine").ProtoLanguage().Code()       // "ine-pro"
    polyglossa.LookupFamily("sem-arb").ProtoLanguage().Code()   // "ar"
    ```

=== "Rust"

    ```rust
    LanguageFamily::of("ine")?.proto_language().map(|l| l.code())       // Some("ine-pro")
    LanguageFamily::of("sem-arb")?.proto_language().map(|l| l.code())   // Some("ar")
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

=== "Java"

    ```java
    static boolean within(LanguageFamily family, LanguageFamily other) {
        Set<String> seen = new HashSet<>();
        for (; family != null && seen.add(family.getCode()); family = family.getParent()) {
            if (family.equals(other)) return true;
        }
        return false;
    }

    LanguageFamily germanic = LanguageFamily.of("gem");
    Language.getAll().stream().filter(language -> within(language.getFamily(), germanic)).toList();
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

=== "TypeScript"

    ```ts
    function within(family: LanguageFamily | null, other: LanguageFamily): boolean {
      const seen = new Set<string>();
      for (; family && !seen.has(family.code); family = family.parent) {
        if (family === other) return true;
        seen.add(family.code);
      }
      return false;
    }

    const germanic = LanguageFamily.of("gem");
    Language.all.filter((language) => within(language.family, germanic));
    ```

=== "Go"

    ```go
    func within(family, other *polyglossa.LanguageFamily) bool {
        seen := map[string]bool{}
        for ; family != nil && !seen[family.Code()]; family = family.Parent() {
            if family == other {
                return true
            }
            seen[family.Code()] = true
        }
        return false
    }

    germanic := polyglossa.LookupFamily("gem")
    var members []*polyglossa.Language
    for _, language := range polyglossa.Languages() {
        if within(language.Family(), germanic) {
            members = append(members, language)
        }
    }
    ```

=== "Rust"

    ```rust
    fn within(family: Option<LanguageFamily<'_>>, other: LanguageFamily<'_>) -> bool {
        let mut seen = Vec::new();
        let mut family = family;
        while let Some(current) = family.filter(|f| !seen.contains(&f.code())) {
            if current == other {
                return true;
            }
            seen.push(current.code());
            family = current.parent();
        }
        false
    }

    let germanic = LanguageFamily::of("gem")?;
    Language::all().filter(|language| within(language.family(), germanic)).collect::<Vec<_>>();
    ```
