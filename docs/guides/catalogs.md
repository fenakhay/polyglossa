# Catalogs

A catalog is a set of languages, families and scripts that know one another: a language's family,
ancestors and scripts are looked up in the catalog it came from. The bundled catalog, English Wiktionary's
own as it was when the version was made, is the one `Language`, `LanguageFamily` and `Script` look up.

=== "Kotlin"

    ```kotlin
    import com.fenakhay.polyglossa.Catalog

    val catalog = Catalog.BUNDLED
    catalog.date                        // "{{ date }}"
    catalog["en"]                       // English (en)
    catalog.family("ine")               // Indo-European (ine)
    catalog.script("Latn")              // Latin (Latn)
    catalog.named("Old English")        // Old English (ang)
    catalog.scriptNamed("Arabic")       // Arabic (Arab)
    catalog.languages.size              // 8970
    ```

=== "Java"

    ```java
    import com.fenakhay.polyglossa.Catalog;

    Catalog catalog = Catalog.getBUNDLED();
    catalog.getDate();                      // "{{ date }}"
    catalog.get("en");                      // English (en)
    catalog.family("ine");                  // Indo-European (ine)
    catalog.script("Latn");                 // Latin (Latn)
    catalog.named("Old English");           // Old English (ang)
    catalog.scriptNamed("Arabic");          // Arabic (Arab)
    catalog.getLanguages().size();          // 8970
    ```

=== "Swift"

    ```swift
    import Polyglossa

    let catalog = Catalog.bundled
    catalog.date                        // "{{ date }}"
    catalog["en"]                       // English (en)
    catalog.family("ine")               // Indo-European (ine)
    catalog.script("Latn")              // Latin (Latn)
    catalog.named("Old English")        // Old English (ang)
    catalog.script(named: "Arabic")     // Arabic (Arab)
    catalog.languages.count             // 8970
    ```

=== "Python"

    ```python
    from polyglossa import Catalog

    catalog = Catalog.bundled()
    catalog.date                        # '{{ date }}'
    catalog["en"].code                  # 'en'; catalog.get("xx") is None
    catalog.family("ine").code          # 'ine'
    catalog.script("Latn").code         # 'Latn'
    catalog.named("Old English").code   # 'ang'
    catalog.script_named("Arabic").code # 'Arab'
    len(catalog.languages)              # 8970
    ```

=== "TypeScript"

    ```ts
    import { Catalog } from "polyglossa";

    const catalog = Catalog.bundled;
    catalog.date                          // "{{ date }}"
    catalog.of("en").code                 // "en"; catalog.get("xx") is null
    catalog.family("ine")?.code           // "ine"
    catalog.script("Latn")?.code          // "Latn"
    catalog.named("Old English")?.code    // "ang"
    catalog.scriptNamed("Arabic")?.code   // "Arab"
    catalog.languages.length              // 8970
    ```

=== "Go"

    ```go
    import "fenakhay.com/polyglossa/go"

    catalog := polyglossa.Bundled()
    catalog.Date()                         // "{{ date }}"
    catalog.Language("en").Code()          // "en"; catalog.Language("xx") is nil
    catalog.Family("ine").Code()           // "ine"
    catalog.Script("Latn").Code()          // "Latn"
    catalog.Named("Old English").Code()    // "ang"
    catalog.ScriptNamed("Arabic").Code()   // "Arab"
    len(catalog.Languages())               // 8970
    ```

=== "Rust"

    ```rust
    use polyglossa::Catalog;

    let catalog = Catalog::bundled();
    catalog.date()                                     // Some("{{ date }}")
    catalog.of("en")?.code()                           // "en"; catalog.get("xx") is None
    catalog.family("ine").map(|f| f.code())            // Some("ine")
    catalog.script("Latn").map(|s| s.code())           // Some("Latn")
    catalog.named("Old English").map(|l| l.code())     // Some("ang")
    catalog.script_named("Arabic").map(|s| s.code())   // Some("Arab")
    catalog.languages().len()                          // 8970
    ```

In Kotlin the collections are in no particular order; in the other languages they are sorted by
code.

## The catalog as text

A catalog is written as `catalog.tsv`, tab-separated lines with a language, a family or a script to each,
sorted by code, so two copies differ line by line where the data does. Every implementation reads it:

=== "Kotlin"

    ```kotlin
    val text = Catalog.BUNDLED.toTsv()
    val copy = Catalog.fromTsv(text)
    copy["en"]?.family                 // Anglic (gmw-ang), from copy, not from BUNDLED
    ```

=== "Java"

    ```java
    String text = Catalog.getBUNDLED().toTsv();
    Catalog copy = Catalog.fromTsv(text);
    copy.get("en").getFamily();             // Anglic (gmw-ang), from copy, not from BUNDLED
    ```

=== "Swift"

    ```swift
    let text = try String(contentsOfFile: "catalog.tsv", encoding: .utf8)
    let copy = try Catalog(tsv: text)
    copy["en"]?.family                 // Anglic (gmw-ang), from copy
    ```

=== "Python"

    ```python
    text = open("catalog.tsv", encoding="utf-8").read()
    copy = Catalog(text)
    copy["en"].family.code             # 'gmw-ang', from copy
    ```

=== "TypeScript"

    ```ts
    import { readFileSync } from "node:fs";

    const copy = Catalog.fromTsv(readFileSync("catalog.tsv", "utf8"));
    copy.of("en").family?.code            // "gmw-ang", from copy
    ```

=== "Go"

    ```go
    text, err := os.ReadFile("catalog.tsv")
    other, err := polyglossa.ParseCatalog(string(text))
    other.Language("en").Family().Code()   // "gmw-ang", from other
    ```

=== "Rust"

    ```rust
    let text = std::fs::read_to_string("catalog.tsv")?;
    let copy = Catalog::from_tsv(&text)?;
    copy.of("en")?.family().map(|f| f.code())   // Some("gmw-ang"), from copy
    ```

A catalog read this way is a world of its own: its languages' links lead into it, not into the bundled one.
That's how a program can use a newer `catalog.tsv`, from a later release's assets, without waiting for a
new version of the library.

## From Wiktionary's exports

The Kotlin library also reads what Wiktionary's `Module:JSON data` exports, which is how the bundled
catalog is made. `Catalog.EXPORTS` lists the six invocations, and `fromExports` takes what the wiki expanded
each to, in that order:

```kotlin
val texts = Catalog.EXPORTS.map { invocation -> expandOnTheWiki(invocation) }
val fresh = Catalog.fromExports(texts, date = "2026-11-12")
```

`expandOnTheWiki` stands for a call to the wiki's `action=expandtemplates`. The marks that tell
Traditional from Simplified Han are not in Wiktionary's data: `withScriptMarks` adds them, by script code,
as polyglossa's own tool does from Unihan:

```kotlin
val marked = fresh.withScriptMarks(mapOf("Hant" to traditional, "Hans" to simplified))
```

Each call makes a new catalog and leaves the one it came from as it was.
