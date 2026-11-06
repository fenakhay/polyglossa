# Scripts

A script is one entry of Wiktionary's `Module:scripts/data`: Latin (`Latn`), Arabic (`Arab`), Arabic in
its Nastaliq form (`Aran`), Simplified Han (`Hans`). There are 233 of them, each with its name, the kind
of writing system it is, the way it runs, and the characters it is made of.

=== "Kotlin"

    ```kotlin
    import com.fenakhay.polyglossa.Language
    import com.fenakhay.polyglossa.Script

    val arabic = Script.of("Arab")
    arabic.system                      // "abjad"
    arabic.direction                   // RIGHT_TO_LEFT
    Script.named("Arabic")             // Arabic (Arab)
    Script.of("Hans").parent           // Han (Hani)
    Script.of("Latn").aliases          // [Roman]
    Script["Xxxx"]                     // null
    ```

=== "Swift"

    ```swift
    import Polyglossa

    let arabic = try Script.of("Arab")
    arabic.system                      // "abjad"
    arabic.direction                   // .rightToLeft
    Script.named("Arabic")             // Arabic (Arab)
    try Script.of("Hans").parent       // Han (Hani)
    try Script.of("Latn").aliases      // ["Roman"]
    Script["Xxxx"]                     // nil
    ```

=== "Python"

    ```python
    from polyglossa import Language, Script

    arabic = Script["Arab"]
    arabic.system                      # 'abjad'
    arabic.direction                   # <ScriptDirection.RIGHT_TO_LEFT: 'rtl'>
    Script.named("Arabic").code        # 'Arab'
    Script["Hans"].parent.code         # 'Hani'
    Script["Latn"].aliases             # ('Roman',)
    Script.get("Xxxx")                 # None
    ```

| Kotlin | Swift | Python | What it is |
|---|---|---|---|
| `code`, `name`, `wikidata`, `aliases` | the same | the same | As for a language |
| `system` | `system` | `system` | The kind of writing system: `alphabet`, `abjad`, `abugida`, `logography` |
| `direction` | `direction` | `direction` | Left to right, right to left, or vertical (Mongolian) |
| `parent` | `parent` | `parent` | The script it is a variety of, such as Han for Simplified Han |
| `nameFor(language)` | `name(for:)` | `name_for(language)` | The name a language gives it |
| `localNames` | `localNames` | `local_names` | Those names, by language |
| `ranges` | `ranges` | `ranges` | Its characters, as ranges of code points |
| `marks` | `marks` | `marks` | The characters that tell it from a script with the same `ranges` |
| `contains(codePoint)` | `contains(_:)` | `code_point in script` | Whether a code point is one of its characters |
| `count(text)` | `count(_:)` | `count(text)` | How many of a text's characters are its own |

## Names a language gives a script

Some languages call a script by a name of their own: Punjabi writes Arabic in its Nastaliq form (`Aran`)
and calls it Shahmukhi. `name` is the general name, and `nameFor` the one a language uses:

=== "Kotlin"

    ```kotlin
    Script.of("Aran").name                          // "Arabic"
    Script.of("Aran").nameFor(Language.of("pa"))    // "Shahmukhi"
    Script.of("Aran").nameFor(Language.of("ur"))    // "Arabic"
    ```

=== "Swift"

    ```swift
    try Script.of("Aran").name                          // "Arabic"
    try Script.of("Aran").name(for: Language.of("pa"))  // "Shahmukhi"
    try Script.of("Aran").name(for: Language.of("ur"))  // "Arabic"
    ```

=== "Python"

    ```python
    Script["Aran"].name                            # 'Arabic'
    Script["Aran"].name_for(Language["pa"])        # 'Shahmukhi'
    Script["Aran"].name_for(Language["ur"])        # 'Arabic'
    ```

## Characters

`ranges` are the code points a script is made of, as `Module:scripts/data` lists them, sorted and joined.
A script Unicode doesn't encode, such as Afaka, has none:

=== "Kotlin"

    ```kotlin
    Script.of("Latn").ranges.take(3)        // [65..90, 97..122, 170..170]
    0x416 in Script.of("Cyrl")              // true: Ж
    Script.of("Arab").count("كتاب abc")      // 4
    Script.of("Afak").ranges                // []
    ```

=== "Swift"

    ```swift
    try Script.of("Latn").ranges.prefix(3)  // [65...90, 97...122, 170...170]
    try Script.of("Cyrl").contains(0x416)   // true: Ж
    try Script.of("Arab").count("كتاب abc")  // 4
    try Script.of("Afak").ranges            // []
    ```

=== "Python"

    ```python
    Script["Latn"].ranges[:3]               # (range(65, 91), range(97, 123), range(170, 171))
    0x416 in Script["Cyrl"]                 # True: Ж
    Script["Arab"].count("كتاب abc")         # 4
    Script["Afak"].ranges                   # ()
    ```

A character can belong to several scripts: every Latin letter is also Fraktur's, Gaelic's and Japanese's,
since Japanese text mixes in Latin. [Detecting scripts](detecting-scripts.md) says how one is chosen.

`marks` matter for two scripts only. Traditional Han and Simplified Han have Han's characters, and their
marks are the characters that tell them apart, from Unicode's Unihan: 6,861 characters Unihan simplifies,
and 6,410 it gives a traditional form.
