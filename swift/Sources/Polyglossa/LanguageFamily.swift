/// One language family as English Wiktionary's `Module:families` describes it.
///
/// A family is a value that points into its catalog. Two are equal when they say the same, whatever catalog each
/// comes from.
public struct LanguageFamily: Hashable, Sendable, CustomStringConvertible {
    let storage: CatalogStorage
    let place: Int

    private var record: FamilyRecord { storage.families[place] }

    /// Its code, such as `ine` or `gmw-fri`.
    public var code: String { record.code }
    /// Its name, such as `Indo-European`.
    public var name: String { record.name }
    /// Its Wikidata item, or `nil`.
    public var wikidata: String? { record.wikidata }
    /// Other names it goes by.
    public var aliases: [String] { record.aliases }

    /// The family it belongs to, or `nil`.
    public var parent: LanguageFamily? { record.parent.map { LanguageFamily(storage: storage, place: $0) } }

    /// The language it descends from: the one Wiktionary names, or else `<code>-pro` when there is one.
    public var protoLanguage: Language? { record.protoLanguage.map { Language(storage: storage, place: $0) } }

    /// The languages in it directly, not those of the families under it, sorted by code.
    public var languages: [Language] { record.languages.map { Language(storage: storage, place: $0) } }

    /// Whether `left` and `right` say the same, whatever catalog each comes from.
    public static func == (left: LanguageFamily, right: LanguageFamily) -> Bool {
        (left.storage === right.storage && left.place == right.place) || left.record.says(right.record)
    }

    /// Hashes its code and name.
    public func hash(into hasher: inout Hasher) {
        hasher.combine(code)
        hasher.combine(name)
    }

    /// Its name and code, as `Indo-European (ine)`.
    public var description: String { "\(name) (\(code))" }

    /// The family with `code`, or `nil` when Wiktionary has none.
    public static subscript(code: String) -> LanguageFamily? { Catalog.bundled.family(code) }

    /// The family with `code`.
    ///
    /// - Throws: ``PolyglossaError/unknownFamily(_:)`` when Wiktionary has none.
    public static func of(_ code: String) throws -> LanguageFamily {
        guard let family = Catalog.bundled.family(code) else { throw PolyglossaError.unknownFamily(code) }
        return family
    }

    /// Whether Wiktionary has a family with `code`.
    public static func contains(_ code: String) -> Bool { Catalog.bundled.family(code) != nil }

    /// Every family, sorted by code.
    public static var all: [LanguageFamily] { Catalog.bundled.families }
}
