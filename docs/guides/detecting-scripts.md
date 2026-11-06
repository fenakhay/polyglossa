# Detecting scripts

polyglossa tells which script a text is written in the way Wiktionary does, from the characters each
script has in `Module:scripts/data`, so a bot can fill `sc=`, or catch a term tagged with the wrong
script, without asking the wiki. There are two ways to ask: in a language, which is what Wiktionary's
`findBestScript` does, and in none, which is `findBestScriptWithoutLang`.

## In a language

=== "Kotlin"

    ```kotlin
    import com.fenakhay.polyglossa.Language

    Language.of("sh").detectScript("Москва")    // Cyrillic (Cyrl)
    Language.of("sh").detectScript("Moskva")    // Latin (Latn)
    Language.of("zh").detectScript("語言")       // Traditional Han (Hant)
    Language.of("zh").detectScript("语言")       // Simplified Han (Hans)
    Language.of("ja").detectScript("ひらがな")   // Japanese (Jpan)
    Language.of("grc").detectScript("λόγος")    // Greek (Polyt), Polytonic Greek
    Language.of("en").detectScript("123")       // null
    ```

=== "Java"

    ```java
    import com.fenakhay.polyglossa.Language;

    Language.of("sh").detectScript("Москва");    // Cyrillic (Cyrl)
    Language.of("sh").detectScript("Moskva");    // Latin (Latn)
    Language.of("zh").detectScript("語言");       // Traditional Han (Hant)
    Language.of("zh").detectScript("语言");       // Simplified Han (Hans)
    Language.of("ja").detectScript("ひらがな");   // Japanese (Jpan)
    Language.of("grc").detectScript("λόγος");    // Greek (Polyt), Polytonic Greek
    Language.of("en").detectScript("123");       // null
    ```

=== "Swift"

    ```swift
    import Polyglossa

    try Language.of("sh").detectScript("Москва")    // Cyrillic (Cyrl)
    try Language.of("sh").detectScript("Moskva")    // Latin (Latn)
    try Language.of("zh").detectScript("語言")       // Traditional Han (Hant)
    try Language.of("zh").detectScript("语言")       // Simplified Han (Hans)
    try Language.of("ja").detectScript("ひらがな")   // Japanese (Jpan)
    try Language.of("grc").detectScript("λόγος")    // Greek (Polyt), Polytonic Greek
    try Language.of("en").detectScript("123")       // nil
    ```

=== "Python"

    ```python
    from polyglossa import Language

    Language["sh"].detect_script("Москва").code    # 'Cyrl'
    Language["sh"].detect_script("Moskva").code    # 'Latn'
    Language["zh"].detect_script("語言").code       # 'Hant'
    Language["zh"].detect_script("语言").code       # 'Hans'
    Language["ja"].detect_script("ひらがな").code   # 'Jpan'
    Language["grc"].detect_script("λόγος").code    # 'Polyt'
    Language["en"].detect_script("123")            # None
    ```

=== "TypeScript"

    ```ts
    import { Language } from "polyglossa";

    Language.of("sh").detectScript("Москва")?.code    // "Cyrl"
    Language.of("sh").detectScript("Moskva")?.code    // "Latn"
    Language.of("zh").detectScript("語言")?.code       // "Hant"
    Language.of("zh").detectScript("语言")?.code       // "Hans"
    Language.of("ja").detectScript("ひらがな")?.code   // "Jpan"
    Language.of("grc").detectScript("λόγος")?.code    // "Polyt"
    Language.of("en").detectScript("123")             // null
    ```

=== "Go"

    ```go
    import "fenakhay.com/polyglossa/go"

    polyglossa.LookupLanguage("sh").DetectScript("Москва").Code()    // "Cyrl"
    polyglossa.LookupLanguage("sh").DetectScript("Moskva").Code()    // "Latn"
    polyglossa.LookupLanguage("zh").DetectScript("語言").Code()       // "Hant"
    polyglossa.LookupLanguage("zh").DetectScript("语言").Code()       // "Hans"
    polyglossa.LookupLanguage("ja").DetectScript("ひらがな").Code()   // "Jpan"
    polyglossa.LookupLanguage("grc").DetectScript("λόγος").Code()    // "Polyt"
    polyglossa.LookupLanguage("en").DetectScript("123")              // nil
    ```

=== "Rust"

    ```rust
    use polyglossa::Language;

    Language::of("sh")?.detect_script("Москва").map(|s| s.code())    // Some("Cyrl")
    Language::of("sh")?.detect_script("Moskva").map(|s| s.code())    // Some("Latn")
    Language::of("zh")?.detect_script("語言").map(|s| s.code())       // Some("Hant")
    Language::of("zh")?.detect_script("语言").map(|s| s.code())       // Some("Hans")
    Language::of("ja")?.detect_script("ひらがな").map(|s| s.code())   // Some("Jpan")
    Language::of("grc")?.detect_script("λόγος").map(|s| s.code())    // Some("Polyt")
    Language::of("en")?.detect_script("123")                         // None
    ```

Only the language's own scripts are candidates:

1. Spaces and ASCII punctuation don't count: Unicode's separators, tab to carriage return, and
   `` !"#%&'()*,-./:;?@[\]_{} ``. With nothing left, the answer is none.
2. The scripts are tried in the order the language lists them. The first that has every character left
   wins.
3. Otherwise, the one with the most of them wins, the earlier on a tie: Serbo-Croatian lists Latin
   first, so `Москва Moskva` is Latin.
4. With no character in any of them, the answer is none: Russian `Moskva` is none, as Russian is written
   in Cyrillic only.

Three kinds of language are handled apart:

- **Languages written in Han**, such as Chinese, list Han, Traditional Han and Simplified Han side by side
  (Wiktionary's `Hants`). Then any Han character decides: the text is Traditional or Simplified Han when
  more of its characters mark one than the other, and Han when as many mark each. `中文` is Han, since
  both characters are the same in either form.
- **A language written in every script**, as Translingual (`mul`) and Undetermined (`und`) are, is
  detected as if it had no language.
- **An etymology-only variety with no scripts of its own** uses its language's.

Pass plain text: Wiktionary strips wiki markup before it detects, and polyglossa counts whatever it is
given.

## In no language

=== "Kotlin"

    ```kotlin
    import com.fenakhay.polyglossa.Script

    Script.detect("Ελληνικά")          // Greek (Grek)
    Script.detect("東京")              // Han (Hani)
    Script.detect("ひらがな漢字")      // Japanese (Jpan)
    Script.detect("한국어")            // Hangul (Hang)
    Script.detect("Москва 2026")       // Cyrillic (Cyrl)
    Script.detect("ab 12345")          // null
    ```

=== "Java"

    ```java
    import com.fenakhay.polyglossa.Script;

    Script.detect("Ελληνικά");          // Greek (Grek)
    Script.detect("東京");              // Han (Hani)
    Script.detect("ひらがな漢字");      // Japanese (Jpan)
    Script.detect("한국어");            // Hangul (Hang)
    Script.detect("Москва 2026");       // Cyrillic (Cyrl)
    Script.detect("ab 12345");          // null
    ```

=== "Swift"

    ```swift
    Script.detect("Ελληνικά")          // Greek (Grek)
    Script.detect("東京")              // Han (Hani)
    Script.detect("ひらがな漢字")      // Japanese (Jpan)
    Script.detect("한국어")            // Hangul (Hang)
    Script.detect("Москва 2026")       // Cyrillic (Cyrl)
    Script.detect("ab 12345")          // nil
    ```

=== "Python"

    ```python
    from polyglossa import Script

    Script.detect("Ελληνικά").code     # 'Grek'
    Script.detect("東京").code         # 'Hani'
    Script.detect("ひらがな漢字").code # 'Jpan'
    Script.detect("한국어").code       # 'Hang'
    Script.detect("Москва 2026").code  # 'Cyrl'
    Script.detect("ab 12345")          # None
    ```

=== "TypeScript"

    ```ts
    import { Script } from "polyglossa";

    Script.detect("Ελληνικά")?.code      // "Grek"
    Script.detect("東京")?.code          // "Hani"
    Script.detect("ひらがな漢字")?.code  // "Jpan"
    Script.detect("한국어")?.code        // "Hang"
    Script.detect("Москва 2026")?.code   // "Cyrl"
    Script.detect("ab 12345")            // null
    ```

=== "Go"

    ```go
    import "fenakhay.com/polyglossa/go"

    polyglossa.DetectScript("Ελληνικά").Code()      // "Grek"
    polyglossa.DetectScript("東京").Code()          // "Hani"
    polyglossa.DetectScript("ひらがな漢字").Code()  // "Jpan"
    polyglossa.DetectScript("한국어").Code()        // "Hang"
    polyglossa.DetectScript("Москва 2026").Code()   // "Cyrl"
    polyglossa.DetectScript("ab 12345")             // nil
    ```

=== "Rust"

    ```rust
    use polyglossa::Script;

    Script::detect("Ελληνικά").map(|s| s.code())      // Some("Grek")
    Script::detect("東京").map(|s| s.code())          // Some("Hani")
    Script::detect("ひらがな漢字").map(|s| s.code())  // Some("Jpan")
    Script::detect("한국어").map(|s| s.code())        // Some("Hang")
    Script::detect("Москва 2026").map(|s| s.code())   // Some("Cyrl")
    Script::detect("ab 12345")                        // None
    ```

`Catalog.detectScript` (`detect_script` in Python) does the same with another catalog. Every character
counts, spaces included, for every script it belongs to:

1. The script most of the characters belong to wins.
2. When more of them belong to no script, as digits don't, the answer is none: `ab 12345` has two Latin
   letters against six characters of no script.
3. Japanese counts the characters that are Han, Hiragana or Katakana, and Korean those that are Han or
   Hangul, each only when no one of them has them all: `ひらがな漢字` is Japanese, `東京` is Han and `한국어`
   is Hangul.
4. A tie goes, in turn, to:
    - a script that holds no other whole, as Katakana does beside Hiragana-and-Katakana, and Greek beside
      Polytonic Greek;
    - a script over Unicode's codes for notation, such as mathematical notation (`Zmth`);
    - the script with the most characters, as Han is beside Latin;
    - one that is no variety of another, as Latin is beside Fraktur;
    - and last the lower code.

Wiktionary breaks some ties by the order of a Lua table, so a tie can come out differently there.

## Traditional and Simplified Han

polyglossa tells the two forms of Han apart by Unicode's [Unihan](https://www.unicode.org/reports/tr38/)
database, version 18.0.0, rather than by Wiktionary's hand-kept lists (`Module:zh/data/ts` and `st`):

- A character is **Traditional** when every simplified form Unihan gives it is another character: 語,
  whose simplified form is 语.
- It is **Simplified** when every traditional form is another character: 语.
- It marks **neither** when it is one of its own forms, as 出 is both itself and the simplified form of
  齣, or 后 both itself and the simplified form of 後.

Wiktionary's lists count some of those shared characters, such as 后, 干, 台 and 机, as Simplified, so a
text made only of them is Simplified Han there and Han here.

## How close it is to Wiktionary

Checked against Wiktionary itself on 1,008 lemmas from 32 languages, each detected in its language and in
none, polyglossa gave Wiktionary's answer for all but three:

| Text | Language | Wiktionary | polyglossa | Why |
|---|---|---|---|---|
| fax机 | Cantonese | Simplified Han | Han | 机 is shared, by Unihan |
| ⿸厂夬 | Cantonese | Simplified Han | Han | 厂 is shared, by Unihan |
| ↓ | none | Symbol (`Zsym`) | Mathematical notation (`Zmth`) | Wiktionary's precomputed table puts ↓ in a script whose characters no longer hold it |

The library's tests keep 61 of those answers, and `spec/expectations.json` holds the answers all three
implementations must give.
