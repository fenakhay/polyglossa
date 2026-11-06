/// One script as English Wiktionary's `Module:scripts/data` describes it.
///
/// A script is a value that points into its catalog. Two are equal when they say the same, whatever catalog each
/// comes from.
///
/// ```swift
/// let latin = try Script.of("Latn")
/// Script.named("Arabic")?.direction                        // .rightToLeft
/// try Script.of("Deva").name(for: try Language.of("mr"))   // "Balbodh"
/// Script.detect("Ελληνικά")?.code                          // "Grek"
/// try Script.of("Cyrl").contains(0x416)                    // true
/// ```
public struct Script: Hashable, Sendable, CustomStringConvertible {
    let storage: CatalogStorage
    let place: Int

    private var record: ScriptRecord { storage.scripts[place] }

    /// Its code, such as `Latn`, `Arab` or `fa-Arab`.
    public var code: String { record.code }
    /// Its name, such as `Latin`.
    public var name: String { record.name }
    /// Its Wikidata item, such as `Q8229`, or `nil`.
    public var wikidata: String? { record.wikidata }
    /// The kind of writing system it is, as Wiktionary words it, such as `alphabet`.
    public var system: String? { record.system }
    /// Other names it goes by.
    public var aliases: [String] { record.aliases }
    /// Which way it runs.
    public var direction: ScriptDirection { record.direction }
    /// The code points it is made of, as `Module:scripts/data` lists them, sorted; empty for a script Unicode
    /// does not encode.
    public var ranges: [ClosedRange<Int>] { record.ranges }
    /// The code points that tell it from the other scripts with the same ``ranges``: for Traditional Han, the
    /// characters Unihan simplifies, and for Simplified Han, those it gives a traditional form; empty for most.
    public var marks: [ClosedRange<Int>] { record.marks }

    /// Whether `codePoint` is one of its characters.
    public func contains(_ codePoint: Int) -> Bool { record.ranges.holds(codePoint) }

    /// Whether `scalar` is one of its characters.
    public func contains(_ scalar: Unicode.Scalar) -> Bool { record.ranges.holds(Int(scalar.value)) }

    /// Counts the characters of `text` that are its own, each Unicode scalar once.
    ///
    /// - Complexity: O(*n* log *r*), where *n* is the length of `text` and *r* the number of its ranges.
    public func count(in text: String) -> Int {
        let ranges = record.ranges
        return text.unicodeScalars.reduce(0) { $0 + (ranges.holds(Int($1.value)) ? 1 : 0) }
    }

    /// The script this is a variety of, such as Han for Simplified Han, or `nil`.
    public var parent: Script? { record.parent.map { Script(storage: storage, place: $0) } }

    /// The name `language` gives this script, which is ``name`` unless it has its own.
    public func name(for language: Language) -> String { record.localNames[language.code] ?? record.name }

    /// The names some languages give it instead of ``name``, such as Balbodh for Marathi's Devanagari.
    public var localNames: [Language: String] {
        var names: [Language: String] = [:]
        for (code, local) in record.localNames {
            if let place = storage.languageCodes[code] { names[Language(storage: storage, place: place)] = local }
        }
        return names
    }

    /// Whether `left` and `right` say the same, whatever catalog each comes from.
    public static func == (left: Script, right: Script) -> Bool {
        (left.storage === right.storage && left.place == right.place) || left.record.says(right.record)
    }

    /// Hashes its code and name.
    public func hash(into hasher: inout Hasher) {
        hasher.combine(code)
        hasher.combine(name)
    }

    /// Its name and code, as `Latin (Latn)`.
    public var description: String { "\(name) (\(code))" }

    /// The script with `code`, or `nil` when Wiktionary has none.
    public static subscript(code: String) -> Script? { Catalog.bundled.script(code) }

    /// The script with `code`.
    ///
    /// - Throws: ``PolyglossaError/unknownScript(_:)`` when Wiktionary has none.
    public static func of(_ code: String) throws -> Script {
        guard let script = Catalog.bundled.script(code) else { throw PolyglossaError.unknownScript(code) }
        return script
    }

    /// The script called `name`, such as `Latin`, or `nil`; the one without a parent when several are.
    public static func named(_ name: String) -> Script? { Catalog.bundled.script(named: name) }

    /// Whether Wiktionary has a script with `code`.
    public static func contains(_ code: String) -> Bool { Catalog.bundled.script(code) != nil }

    /// Every script, sorted by code.
    public static var all: [Script] { Catalog.bundled.scripts }

    /// Finds the script `text` is written in, whatever its language; see ``Catalog/detectScript(_:)``.
    public static func detect(_ text: String) -> Script? { Catalog.bundled.detectScript(text) }
}
