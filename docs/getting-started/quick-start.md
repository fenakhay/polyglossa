# Quick start

This page looks up a language, follows its links to its family and ancestors, and tells which script a
word is written in. It assumes polyglossa is installed: [Installing](installing.md) has the coordinates.

## Look up a language

Every code Wiktionary uses is there, and a lookup that may miss gives nothing instead of failing:

=== "Kotlin"

    ```kotlin
    import com.fenakhay.polyglossa.Language

    fun main() {
        val english = Language.of("en")
        println(english)                       // English (en)
        println(english.wikidata)              // Q1860
        println(english.aliases)               // [Modern English, New English]
        println(Language["xx"])                // null
        println("ang" in Language)             // true
        println(Language.named("Old English")) // Old English (ang)
    }
    ```

=== "Java"

    ```java
    import com.fenakhay.polyglossa.Language;

    public class Main {
        public static void main(String[] args) {
            Language english = Language.of("en");
            System.out.println(english);                        // English (en)
            System.out.println(english.getWikidata());          // Q1860
            System.out.println(english.getAliases());           // [Modern English, New English]
            System.out.println(Language.get("xx"));             // null
            System.out.println(Language.contains("ang"));       // true
            System.out.println(Language.named("Old English"));  // Old English (ang)
        }
    }
    ```

=== "Swift"

    ```swift
    import Polyglossa

    let english = try Language.of("en")
    print(english)                             // English (en)
    print(english.wikidata ?? "")              // Q1860
    print(english.aliases)                     // ["Modern English", "New English"]
    print(Language["xx"] as Any)               // nil
    print(Language.contains("ang"))            // true
    print(Language.named("Old English")!)      // Old English (ang)
    ```

=== "Python"

    ```python
    from polyglossa import Language

    english = Language["en"]
    print(english)                             # English (en)
    print(english.wikidata)                    # Q1860
    print(english.aliases)                     # ('Modern English', 'New English')
    print(Language.get("xx"))                  # None
    print("ang" in Language)                   # True
    print(Language.named("Old English"))       # Old English (ang)
    ```

=== "TypeScript"

    ```ts
    import { Language } from "polyglossa";

    const english = Language.of("en");
    console.log(String(english));                       // English (en)
    console.log(english.wikidata);                      // Q1860
    console.log(english.aliases);                       // [ 'Modern English', 'New English' ]
    console.log(Language.get("xx"));                    // null
    console.log(Language.has("ang"));                   // true
    console.log(String(Language.named("Old English"))); // Old English (ang)
    ```

=== "Go"

    ```go
    import (
        "fmt"

        "fenakhay.com/polyglossa/go"
    )

    english := polyglossa.LookupLanguage("en")
    fmt.Println(english)                                 // English (en)
    fmt.Println(english.Wikidata())                      // Q1860
    fmt.Println(english.Aliases())                       // [Modern English New English]
    fmt.Println(polyglossa.LookupLanguage("xx"))         // <nil>
    fmt.Println(polyglossa.LookupLanguage("ang") != nil) // true
    fmt.Println(polyglossa.LanguageNamed("Old English")) // Old English (ang)
    ```

=== "Rust"

    ```rust
    use polyglossa::Language;

    let english = Language::of("en")?;
    println!("{english}");                              // English (en)
    println!("{:?}", english.wikidata());               // Some("Q1860")
    println!("{:?}", english.aliases());                // ["Modern English", "New English"]
    println!("{:?}", Language::get("xx"));              // None
    println!("{}", Language::get("ang").is_some());     // true
    println!("{:?}", Language::named("Old English"));   // Some(Language(ang))
    ```

`of` fails for a code Wiktionary doesn't have: it throws `IllegalArgumentException` in Kotlin and Java, where
`Language.get` gives `null`, `PolyglossaError.unknownLanguage` in Swift, and `UnknownLanguageError` in Python,
where `Language["xx"]` fails the same way and `Language.get` gives `None`. In TypeScript it throws
`UnknownLanguageError` and `Language.get` gives `null`; in Go `LanguageOf` returns an error and
`LookupLanguage` nil; in Rust `Language::of` returns `Err(Error::UnknownLanguage)` and `Language::get` `None`.

## Follow the links

A language leads to its family, its ancestors and its scripts, and a family to its parent and its
proto-language. The links are the objects themselves:

=== "Kotlin"

    ```kotlin
    val english = Language.of("en")
    english.family                             // Anglic (gmw-ang)
    english.ancestors                          // [Early Modern English (en-ear)]
    english.ancestors.first().ancestors        // [Middle English (enm)]
    english.scripts.map { it.code }            // [Latn, Brai, Shaw, Dsrt]
    english.family?.parent                     // Anglo-Frisian (gmw-afr)
    ```

=== "Java"

    ```java
    Language english = Language.of("en");
    english.getFamily();                                            // Anglic (gmw-ang)
    english.getAncestors();                                         // [Early Modern English (en-ear)]
    english.getAncestors().get(0).getAncestors();                   // [Middle English (enm)]
    english.getScripts().stream().map(Script::getCode).toList();    // [Latn, Brai, Shaw, Dsrt]
    english.getFamily().getParent();                                // Anglo-Frisian (gmw-afr)
    ```

=== "Swift"

    ```swift
    let english = try Language.of("en")
    english.family                             // Anglic (gmw-ang)
    english.ancestors                          // [Early Modern English (en-ear)]
    english.ancestors.first?.ancestors         // [Middle English (enm)]
    english.scripts.map(\.code)                // ["Latn", "Brai", "Shaw", "Dsrt"]
    english.family?.parent                     // Anglo-Frisian (gmw-afr)
    ```

=== "Python"

    ```python
    english = Language["en"]
    str(english.family)                        # 'Anglic (gmw-ang)'
    [a.code for a in english.ancestors]        # ['en-ear']
    [a.code for a in english.ancestors[0].ancestors]   # ['enm']
    [s.code for s in english.scripts]          # ['Latn', 'Brai', 'Shaw', 'Dsrt']
    english.family.parent.name                 # 'Anglo-Frisian'

    ```

=== "TypeScript"

    ```ts
    const english = Language.of("en");
    String(english.family)                              // "Anglic (gmw-ang)"
    english.ancestors.map((a) => a.code)                // ["en-ear"]
    english.ancestors[0]?.ancestors.map((a) => a.code)  // ["enm"]
    english.scripts.map((s) => s.code)                  // ["Latn", "Brai", "Shaw", "Dsrt"]
    english.family?.parent?.name                        // "Anglo-Frisian"
    ```

=== "Go"

    ```go
    english := polyglossa.LookupLanguage("en")
    english.Family().String()                      // "Anglic (gmw-ang)"
    english.Ancestors()[0].Code()                  // "en-ear"
    english.Ancestors()[0].Ancestors()[0].Code()   // "enm"
    english.Scripts()                              // [Latin (Latn) Braille (Brai) Shavian (Shaw) Deseret (Dsrt)]
    english.Family().Parent().Name()               // "Anglo-Frisian"
    ```

=== "Rust"

    ```rust
    let english = Language::of("en")?;
    english.family().map(|f| f.to_string())                          // Some("Anglic (gmw-ang)")
    english.ancestors().map(Language::code).collect::<Vec<_>>()      // ["en-ear"]
    english.ancestors().next().and_then(|a| a.ancestors().next()).map(Language::code)   // Some("enm")
    english.scripts().map(Script::code).collect::<Vec<_>>()          // ["Latn", "Brai", "Shaw", "Dsrt"]
    english.family().and_then(|f| f.parent()).map(|f| f.name())      // Some("Anglo-Frisian")
    ```

## Tell the script of a word

Detection works as Wiktionary's own `findBestScript` does, from the characters each script has:

=== "Kotlin"

    ```kotlin
    import com.fenakhay.polyglossa.Language
    import com.fenakhay.polyglossa.Script

    Language.of("sh").detectScript("Москва")   // Cyrillic (Cyrl)
    Language.of("sh").detectScript("Moskva")   // Latin (Latn)
    Language.of("zh").detectScript("語言")      // Traditional Han (Hant)
    Script.detect("Ελληνικά")                  // Greek (Grek), whatever the language
    ```

=== "Java"

    ```java
    import com.fenakhay.polyglossa.Language;
    import com.fenakhay.polyglossa.Script;

    Language.of("sh").detectScript("Москва");   // Cyrillic (Cyrl)
    Language.of("sh").detectScript("Moskva");   // Latin (Latn)
    Language.of("zh").detectScript("語言");      // Traditional Han (Hant)
    Script.detect("Ελληνικά");                  // Greek (Grek), whatever the language
    ```

=== "Swift"

    ```swift
    try Language.of("sh").detectScript("Москва")   // Cyrillic (Cyrl)
    try Language.of("sh").detectScript("Moskva")   // Latin (Latn)
    try Language.of("zh").detectScript("語言")      // Traditional Han (Hant)
    Script.detect("Ελληνικά")                      // Greek (Grek), whatever the language
    ```

=== "Python"

    ```python
    from polyglossa import Language, Script

    Language["sh"].detect_script("Москва").code    # 'Cyrl'
    Language["sh"].detect_script("Moskva").code    # 'Latn'
    Language["zh"].detect_script("語言").code       # 'Hant'
    Script.detect("Ελληνικά").code                 # 'Grek', whatever the language
    ```

=== "TypeScript"

    ```ts
    import { Language, Script } from "polyglossa";

    Language.of("sh").detectScript("Москва")?.code   // "Cyrl"
    Language.of("sh").detectScript("Moskva")?.code   // "Latn"
    Language.of("zh").detectScript("語言")?.code      // "Hant"
    Script.detect("Ελληνικά")?.code                  // "Grek", whatever the language
    ```

=== "Go"

    ```go
    import "fenakhay.com/polyglossa/go"

    polyglossa.LookupLanguage("sh").DetectScript("Москва").Code()   // "Cyrl"
    polyglossa.LookupLanguage("sh").DetectScript("Moskva").Code()   // "Latn"
    polyglossa.LookupLanguage("zh").DetectScript("語言").Code()      // "Hant"
    polyglossa.DetectScript("Ελληνικά").Code()                      // "Grek", whatever the language
    ```

=== "Rust"

    ```rust
    use polyglossa::{Language, Script};

    Language::of("sh")?.detect_script("Москва").map(|s| s.code())   // Some("Cyrl")
    Language::of("sh")?.detect_script("Moskva").map(|s| s.code())   // Some("Latn")
    Language::of("zh")?.detect_script("語言").map(|s| s.code())      // Some("Hant")
    Script::detect("Ελληνικά").map(|s| s.code())                    // Some("Grek"), whatever the language
    ```

## Next

- [Languages](../guides/languages.md): the kinds of language, etymology-only varieties and the varieties
  Wiktionary names.
- [Families](../guides/families.md) and [Scripts](../guides/scripts.md): the rest of the data.
- [Detecting scripts](../guides/detecting-scripts.md): how detection decides, and where it differs from
  Wiktionary.
