import Foundation

/// Why a lookup that must succeed failed, or why a catalog could not be read.
public enum PolyglossaError: Error, Equatable, CustomStringConvertible {
    /// Wiktionary has no language with this code.
    case unknownLanguage(String)
    /// Wiktionary has no family with this code.
    case unknownFamily(String)
    /// Wiktionary has no script with this code.
    case unknownScript(String)
    /// The text is not a catalog Polyglossa wrote.
    case notACatalog(String)

    public var description: String {
        switch self {
        case .unknownLanguage(let code): return "Wiktionary has no language \(code)"
        case .unknownFamily(let code): return "Wiktionary has no family \(code)"
        case .unknownScript(let code): return "Wiktionary has no script \(code)"
        case .notACatalog(let why): return "not a catalog Polyglossa wrote: \(why)"
        }
    }
}

/// A set of languages, families and scripts that know one another, read from the tab-separated `catalog.tsv`
/// the Kotlin library writes. ``bundled``, English Wiktionary's own, is the one ``Language``, ``LanguageFamily``
/// and ``Script`` look up.
public final class Catalog: @unchecked Sendable {
    /// English Wiktionary's languages, families and scripts as the package was built with them.
    public static let bundled: Catalog = {
        guard let url = Bundle.module.url(forResource: "catalog", withExtension: "tsv"),
              let text = try? String(contentsOf: url, encoding: .utf8),
              let catalog = try? Catalog(tsv: text)
        else { fatalError("the catalog bundled with Polyglossa cannot be read") }
        return catalog
    }()

    /// The day Wiktionary exported the data, as `2026-11-05`, or `nil` when nobody said.
    public let date: String?
    private let byCode: [String: Language]
    private let familiesByCode: [String: LanguageFamily]
    private let scriptsByCode: [String: Script]
    private let byName: [String: Language]
    private let scriptsByName: [String: Script]
    private let byFamily: [String: [Language]]

    /// Every language and etymology-only variety, sorted by code.
    public var languages: [Language] { byCode.values.sorted { $0.code < $1.code } }
    /// Every family, sorted by code.
    public var families: [LanguageFamily] { familiesByCode.values.sorted { $0.code < $1.code } }
    /// Every script, sorted by code.
    public var scripts: [Script] { scriptsByCode.values.sorted { $0.code < $1.code } }

    /// The catalog `tsv` holds, as the Kotlin library's `Catalog.toTsv` writes it.
    public init(tsv: String) throws {
        let table = try CatalogTable.read(tsv)
        date = table.date
        let link = CatalogLink()
        byCode = Dictionary(table.languages.map { ($0.code, Language(row: $0, link: link)) }, uniquingKeysWith: { _, last in last })
        familiesByCode = Dictionary(table.families.map { ($0.code, LanguageFamily(row: $0, link: link)) }, uniquingKeysWith: { _, last in last })
        scriptsByCode = Dictionary(table.scripts.map { ($0.code, Script(row: $0, link: link)) }, uniquingKeysWith: { _, last in last })
        var names: [String: Language] = [:]
        for language in byCode.values where language.isEtymologyOnly { names[language.name] = language }
        for language in byCode.values where !language.isEtymologyOnly { names[language.name] = language }
        byName = names
        var scriptNames: [String: Script] = [:]
        for script in scriptsByCode.values where script.parentCode != nil { scriptNames[script.name] = script }
        for script in scriptsByCode.values where script.parentCode == nil { scriptNames[script.name] = script }
        scriptsByName = scriptNames
        byFamily = Dictionary(grouping: byCode.values.filter { $0.familyCode != nil }, by: { $0.familyCode! })
            .mapValues { $0.sorted { $0.code < $1.code } }
        link.catalog = self
    }

    /// The language with `code`, or `nil`.
    public subscript(code: String) -> Language? { byCode[code] }

    /// Whether there is a language with `code`.
    public func contains(_ code: String) -> Bool { byCode[code] != nil }

    /// The language called `name`, as its section in an entry is headed; a full language wins over an
    /// etymology-only variety of the same name.
    public func named(_ name: String) -> Language? { byName[name.trimmingCharacters(in: .whitespaces)] }

    /// The family with `code`, or `nil`.
    public func family(_ code: String) -> LanguageFamily? { familiesByCode[code] }

    /// The script with `code`, or `nil`.
    public func script(_ code: String) -> Script? { scriptsByCode[code] }

    /// The script called `name`; one that is no variety of another wins over one that is.
    public func scriptNamed(_ name: String) -> Script? { scriptsByName[name.trimmingCharacters(in: .whitespaces)] }

    func inFamily(_ code: String) -> [Language] { byFamily[code] ?? [] }
}

/// Where a language, a family or a script finds the catalog its links lead into, set once the catalog is made.
final class CatalogLink: @unchecked Sendable {
    weak var catalog: Catalog?

    var resolved: Catalog { catalog ?? Catalog.bundled }
}
