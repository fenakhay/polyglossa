/// Which way a script runs, as Wiktionary's `Module:scripts/data` has it.
public enum ScriptDirection: String, Sendable, CaseIterable {
    /// Left to right, as most scripts run.
    case leftToRight = "ltr"
    /// Right to left, as Arabic and Hebrew run.
    case rightToLeft = "rtl"
    /// Top to bottom, columns left to right, as Mongolian runs.
    case verticalLeftToRight = "vertical-ltr"
    /// Top to bottom, columns right to left.
    case verticalRightToLeft = "vertical-rtl"
}

/// One script as English Wiktionary's `Module:scripts/data` describes it.
///
/// ```swift
/// let latin = try Script.of("Latn")
/// Script.named("Arabic")?.direction            // .rightToLeft
/// try Script.of("Deva").name(for: try Language.of("mr"))   // "Balbodh"
/// Script.detect("Ελληνικά")?.code                // "Grek"
/// try Script.of("Cyrl").contains(0x416)          // true
/// ```
public final class Script: Hashable, CustomStringConvertible, @unchecked Sendable {
    /// Its code, such as `Latn`, `Arab` or `fa-Arab`.
    public let code: String
    /// Its name, such as `Latin`.
    public let name: String
    /// Its Wikidata item, such as `Q8229`, or `nil`.
    public let wikidata: String?
    /// The kind of writing system it is, as Wiktionary words it, such as `alphabet`.
    public let system: String?
    /// Other names it goes by.
    public let aliases: [String]
    /// Which way it runs.
    public let direction: ScriptDirection
    /// The code points it is made of, as `Module:scripts/data` lists them, sorted; empty for a script Unicode
    /// does not encode.
    public let ranges: [ClosedRange<Int>]
    /// The code points that tell it from the other scripts with the same ``ranges``: for Traditional Han, the
    /// characters Unihan simplifies, and for Simplified Han, those it gives a traditional form; empty for most.
    public let marks: [ClosedRange<Int>]
    let parentCode: String?
    let localNameCodes: [String: String]
    private let link: CatalogLink

    init(row: CatalogTable.ScriptRow, link: CatalogLink) {
        code = row.code
        name = row.name
        wikidata = row.wikidata
        system = row.system
        aliases = row.aliases
        parentCode = row.parentCode
        direction = row.direction
        localNameCodes = row.localNames
        ranges = row.ranges
        marks = row.marks
        self.link = link
    }

    /// Whether `codePoint` is one of its characters.
    public func contains(_ codePoint: Int) -> Bool { ranges.holds(codePoint) }

    /// How many of the characters of `text` are its own, counting each Unicode scalar once.
    public func count(_ text: String) -> Int { text.unicodeScalars.filter { contains(Int($0.value)) }.count }

    func isMark(_ codePoint: Int) -> Bool { marks.holds(codePoint) }

    /// The script this is a variety of, such as Han for Simplified Han, or `nil`.
    public var parent: Script? { parentCode.flatMap { link.resolved.script($0) } }

    /// The name `language` gives this script, which is ``name`` unless it has its own.
    public func name(for language: Language) -> String { localNameCodes[language.code] ?? name }

    /// The names some languages give it instead of ``name``, such as Balbodh for Marathi's Devanagari.
    public var localNames: [Language: String] {
        var names: [Language: String] = [:]
        for (code, local) in localNameCodes {
            if let language = link.resolved[code] { names[language] = local }
        }
        return names
    }

    public static func == (left: Script, right: Script) -> Bool {
        left.code == right.code && left.name == right.name && left.wikidata == right.wikidata
            && left.system == right.system && left.aliases == right.aliases && left.parentCode == right.parentCode
            && left.direction == right.direction && left.localNameCodes == right.localNameCodes
            && left.ranges == right.ranges && left.marks == right.marks
    }

    public func hash(into hasher: inout Hasher) {
        hasher.combine(code)
        hasher.combine(name)
    }

    /// Its name and code, as `Latin (Latn)`.
    public var description: String { "\(name) (\(code))" }

    /// The script with `code`, or `nil` when Wiktionary has none.
    public static subscript(code: String) -> Script? { Catalog.bundled.script(code) }

    /// The script with `code`.
    public static func of(_ code: String) throws -> Script {
        guard let script = Catalog.bundled.script(code) else { throw PolyglossaError.unknownScript(code) }
        return script
    }

    /// The script called `name`, such as `Latin`, or `nil`; the one without a parent when several are.
    public static func named(_ name: String) -> Script? { Catalog.bundled.scriptNamed(name) }

    /// Whether Wiktionary has a script with `code`.
    public static func contains(_ code: String) -> Bool { Catalog.bundled.script(code) != nil }

    /// Every script, sorted by code.
    public static var all: [Script] { Catalog.bundled.scripts }

    /// The script `text` is written in, whatever its language, or `nil`; see ``Catalog/detectScript(_:)``.
    public static func detect(_ text: String) -> Script? { Catalog.bundled.detectScript(text) }
}
