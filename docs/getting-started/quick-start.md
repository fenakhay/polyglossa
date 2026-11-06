# Quick start

This page looks up a language, follows its links to its family and ancestors, and tells which script a
word is written in. It assumes Polyglossa is installed: [Installing](installing.md) has the coordinates.

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

`of` fails for a code Wiktionary doesn't have: it throws `IllegalArgumentException` in Kotlin,
`PolyglossaError.unknownLanguage` in Swift, and `UnknownLanguageError` in Python, where `Language["xx"]`
fails the same way and `Language.get` gives `None`.

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

## Next

- [Languages](../guides/languages.md): the kinds of language, etymology-only varieties and the varieties
  Wiktionary names.
- [Families](../guides/families.md) and [Scripts](../guides/scripts.md): the rest of the data.
- [Detecting scripts](../guides/detecting-scripts.md): how detection decides, and where it differs from
  Wiktionary.
