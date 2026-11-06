/// One language as English Wiktionary's `Module:languages` and `Module:etymology languages` describe it.
///
/// A language is a value that points into its catalog. Two are equal when they say the same, whatever catalog
/// each comes from.
///
/// ```swift
/// let english = try Language.of("en")
/// Language["xx"]                       // nil: no such language
/// Language.named("Old English")?.code  // "ang"
/// english.family?.name                 // "Anglic"
/// ```
public struct Language: Hashable, Sendable, CustomStringConvertible {
    let storage: CatalogStorage
    let place: Int

    var record: LanguageRecord { storage.languages[place] }

    /// Its code, such as `en`, `ine-pro` or the etymology-only `oc-lim`.
    public var code: String { record.code }
    /// Its name as Wiktionary writes it, which is the heading of its section in an entry.
    public var name: String { record.name }
    /// What kind of language it is.
    public var type: LanguageType { record.type }
    /// Its Wikidata item, such as `Q1860`, or `nil`.
    public var wikidata: String? { record.wikidata }
    /// Other names it goes by.
    public var aliases: [String] { record.aliases }
    /// The varieties of it Wiktionary names, such as dialects, which have no code of their own.
    public var varieties: [Variety] { record.varieties }

    /// Whether it is named in etymologies only, with no section of its own in an entry.
    public var isEtymologyOnly: Bool { record.type == .etymologyOnly }

    /// The family it belongs to, or `nil`; an etymology-only variety with none of its own has that of the nearest
    /// of its parents that has one, as American English has English's through North American English.
    public var family: LanguageFamily? {
        guard let link = lineage.lazy.compactMap({ $0.record.family }).first, let place = link.place else { return nil }
        return LanguageFamily(storage: storage, place: place)
    }

    /// The scripts it is written in, most usual first, those the catalog knows.
    public var scripts: [Script] { record.scripts.compactMap { $0.place.map { Script(storage: storage, place: $0) } } }

    /// The languages it descends from, those the catalog knows.
    public var ancestors: [Language] { record.ancestors.map { Language(storage: storage, place: $0) } }

    /// The language an etymology-only variety belongs to, or `nil` for any other.
    public var parent: Language? { record.parent.map { Language(storage: storage, place: $0) } }

    /// The full language this stands for: itself, or for an etymology-only variety the one it belongs to.
    public var full: Language {
        let lineage = lineage
        return lineage.first { !$0.isEtymologyOnly } ?? lineage.last ?? self
    }

    /// Finds the script `text` is written in, among this language's ``scripts``, as Wiktionary's
    /// `findBestScript` finds it.
    ///
    /// Spaces and ASCII punctuation are left out, and the scripts are tried in order: the first that has every
    /// character wins, or else the one with the most of them, the earlier on a tie. Chinese and the other
    /// languages written in Han give Traditional or Simplified Han when more of the characters mark one than the
    /// other, and Han when as many mark each. A language written in every script finds it as
    /// ``Catalog/detectScript(_:)`` does, and an etymology-only variety with no scripts of its own uses its
    /// language's.
    ///
    /// ```swift
    /// try Language.of("sh").detectScript("Москва")?.code   // "Cyrl"
    /// try Language.of("zh").detectScript("語言")?.code      // "Hant"
    /// try Language.of("en").detectScript("123")            // nil
    /// ```
    ///
    /// - Parameter text: Plain text; wiki markup counts as characters.
    /// - Returns: The script, or `nil` when none of the language's scripts has any of its characters.
    public func detectScript(_ text: String) -> Script? {
        storage.detection.detect(record, lineage: lineage.map(\.record), text).map { Script(storage: storage, place: $0) }
    }

    /// Itself, then its parent, its parent's parent and so on, each once.
    private var lineage: [Language] {
        var lineage: [Language] = []
        var current: Language? = self
        while let language = current, !lineage.contains(where: { $0.place == language.place }) {
            lineage.append(language)
            current = language.parent
        }
        return lineage
    }

    /// Whether `left` and `right` say the same, whatever catalog each comes from.
    public static func == (left: Language, right: Language) -> Bool {
        (left.storage === right.storage && left.place == right.place) || left.record.says(right.record)
    }

    /// Hashes its code and name.
    public func hash(into hasher: inout Hasher) {
        hasher.combine(code)
        hasher.combine(name)
    }

    /// Its name and code, as `English (en)`.
    public var description: String { "\(name) (\(code))" }

    /// The language with `code`, or `nil` when Wiktionary has none.
    public static subscript(code: String) -> Language? { Catalog.bundled[code] }

    /// The language with `code`.
    ///
    /// - Throws: ``PolyglossaError/unknownLanguage(_:)`` when Wiktionary has none.
    public static func of(_ code: String) throws -> Language {
        guard let language = Catalog.bundled[code] else { throw PolyglossaError.unknownLanguage(code) }
        return language
    }

    /// The language called `name`, as its section in an entry is headed, or `nil`.
    public static func named(_ name: String) -> Language? { Catalog.bundled.named(name) }

    /// Whether Wiktionary has a language with `code`.
    public static func contains(_ code: String) -> Bool { Catalog.bundled.contains(code) }

    /// Every language and etymology-only variety, sorted by code.
    public static var all: [Language] { Catalog.bundled.languages }
}
