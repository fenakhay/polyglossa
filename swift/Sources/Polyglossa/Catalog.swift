import Foundation

/// A set of languages, families and scripts that know one another, read from the tab-separated `catalog.tsv`
/// the Kotlin library writes. ``bundled``, English Wiktionary's own, is the one ``Language``, ``LanguageFamily``
/// and ``Script`` look up.
///
/// A catalog never changes once it is read, so it and everything it gives can be shared between tasks.
public final class Catalog: Sendable {
    /// English Wiktionary's languages, families and scripts as the package was built with them.
    public static let bundled: Catalog = {
        guard let url = Bundle.module.url(forResource: "catalog", withExtension: "tsv"),
              let text = try? String(contentsOf: url, encoding: .utf8),
              let catalog = try? Catalog(tsv: text)
        else { fatalError("the catalog bundled with polyglossa cannot be read") }
        return catalog
    }()

    let storage: CatalogStorage

    /// The day Wiktionary exported the data, as `2026-11-05`, or `nil` when nobody said.
    public var date: String? { storage.date }

    /// Every language and etymology-only variety, sorted by code.
    public var languages: [Language] { storage.languages.indices.map { Language(storage: storage, place: $0) } }
    /// Every family, sorted by code.
    public var families: [LanguageFamily] {
        storage.families.indices.map { LanguageFamily(storage: storage, place: $0) }
    }
    /// Every script, sorted by code.
    public var scripts: [Script] { storage.scripts.indices.map { Script(storage: storage, place: $0) } }

    /// Creates the catalog `tsv` holds, as the Kotlin library's `Catalog.toTsv` writes it.
    ///
    /// - Throws: ``PolyglossaError/notACatalog(_:)``, starting with the line, when `tsv` is not such a catalog.
    public init(tsv: String) throws {
        storage = CatalogStorage(try CatalogReader.read(tsv))
    }

    /// The language with `code`, or `nil`.
    public subscript(code: String) -> Language? {
        storage.languageCodes[code].map { Language(storage: storage, place: $0) }
    }

    /// Whether there is a language with `code`.
    public func contains(_ code: String) -> Bool { storage.languageCodes[code] != nil }

    /// The language called `name`, as its section in an entry is headed; a full language wins over an
    /// etymology-only variety of the same name.
    public func named(_ name: String) -> Language? {
        storage.languageNames[name.trimmingCharacters(in: .whitespaces)].map { Language(storage: storage, place: $0) }
    }

    /// The family with `code`, or `nil`.
    public func family(_ code: String) -> LanguageFamily? {
        storage.familyCodes[code].map { LanguageFamily(storage: storage, place: $0) }
    }

    /// The script with `code`, or `nil`.
    public func script(_ code: String) -> Script? {
        storage.scriptCodes[code].map { Script(storage: storage, place: $0) }
    }

    /// The script called `name`; one that is no variety of another wins over one that is.
    public func script(named name: String) -> Script? {
        storage.scriptNames[name.trimmingCharacters(in: .whitespaces)].map { Script(storage: storage, place: $0) }
    }

    /// Finds the script `text` is written in, whatever its language, as Wiktionary's `findBestScriptWithoutLang`
    /// finds it.
    ///
    /// The script most of its characters belong to wins. A tie goes to a script that holds no other whole, as
    /// Greek does beside Polytonic Greek, then to a script over notation such as `Zmth`, then to the one with the
    /// most characters, as Han is beside Latin, then to one that is no variety of another.
    /// ``Language/detectScript(_:)`` knows better when the language is known.
    ///
    /// - Returns: The script, or `nil` when more of the characters belong to no script.
    public func detectScript(_ text: String) -> Script? {
        storage.detection.detect(text).map { Script(storage: storage, place: $0) }
    }
}
