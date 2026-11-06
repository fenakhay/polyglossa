/// What kind of language Wiktionary takes one to be, which decides where its entries may live.
public enum LanguageType: String, Sendable, CaseIterable {
    /// Attested, with entries in the main namespace.
    case regular
    /// Reconstructed, such as Proto-Indo-European, with entries in the Reconstruction namespace.
    case reconstructed
    /// Constructed and kept in the Appendix namespace, such as Klingon.
    case appendixConstructed = "appendix-constructed"
    /// A variety named in etymologies only, such as Limousin, which has no entries of its own.
    case etymologyOnly = "etymology-only"
}

/// A variety of a language Wiktionary names without giving it a code, such as a dialect.
public struct Variety: Hashable, Sendable, CustomStringConvertible {
    /// Its name, such as `Bavarian German`.
    public let name: String
    /// Other names it goes by.
    public let otherNames: [String]

    public var description: String { otherNames.isEmpty ? name : "\(name) (\(otherNames.joined(separator: ", ")))" }
}

/// One language as English Wiktionary's `Module:languages` and `Module:etymology languages` describe it.
///
/// ```swift
/// let english = try Language.of("en")
/// Language["xx"]                       // nil: no such language
/// Language.named("Old English")?.code  // "ang"
/// english.family?.name                 // "Anglic"
/// ```
public final class Language: Hashable, CustomStringConvertible, @unchecked Sendable {
    /// Its code, such as `en`, `ine-pro` or the etymology-only `oc-lim`.
    public let code: String
    /// Its name as Wiktionary writes it, which is the heading of its section in an entry.
    public let name: String
    /// What kind of language it is.
    public let type: LanguageType
    /// Its Wikidata item, such as `Q1860`, or `nil`.
    public let wikidata: String?
    /// Other names it goes by.
    public let aliases: [String]
    /// The varieties of it Wiktionary names, such as dialects, which have no code of their own.
    public let varieties: [Variety]
    let familyCode: String?
    let scriptCodes: [String]
    let ancestorCodes: [String]
    let parentCode: String?
    private let link: CatalogLink

    init(row: CatalogTable.LanguageRow, link: CatalogLink) {
        code = row.code
        name = row.name
        type = row.type
        wikidata = row.wikidata
        familyCode = row.familyCode
        scriptCodes = row.scriptCodes
        ancestorCodes = row.ancestorCodes
        aliases = row.aliases
        parentCode = row.parentCode
        varieties = row.varieties
        self.link = link
    }

    /// Whether it is named in etymologies only, with no section of its own in an entry.
    public var isEtymologyOnly: Bool { type == .etymologyOnly }

    /// The family it belongs to, or `nil`; an etymology-only variety has its language's.
    public var family: LanguageFamily? { (familyCode ?? parent?.familyCode).flatMap { link.resolved.family($0) } }

    /// The scripts it is written in, most usual first, those the catalog knows.
    public var scripts: [Script] { scriptCodes.compactMap { link.resolved.script($0) } }

    /// The languages it descends from, those the catalog knows.
    public var ancestors: [Language] { ancestorCodes.compactMap { link.resolved[$0] } }

    /// The language an etymology-only variety belongs to, or `nil` for any other.
    public var parent: Language? { parentCode.flatMap { link.resolved[$0] } }

    /// The full language this stands for: itself, or for an etymology-only variety the one it belongs to.
    public var full: Language {
        var language = self
        var seen: Set<String> = [code]
        while language.isEtymologyOnly, let parent = language.parent, seen.insert(parent.code).inserted {
            language = parent
        }
        return language
    }

    /// The script `text` is written in, among this language's ``scripts``, as Wiktionary's `findBestScript`
    /// finds it, or `nil` when none of them has any of its characters.
    ///
    /// Spaces and ASCII punctuation are left out, and the scripts are tried in order: the first that has every
    /// character wins, or else the one with the most of them, the earlier on a tie. Chinese and the other
    /// languages written in Han give Traditional or Simplified Han when more of the characters mark one than the
    /// other, and Han when as many mark each. A language written in every script finds it as
    /// ``Catalog/detectScript(_:)`` does, and an etymology-only variety with no scripts of its own uses its
    /// language's. Pass plain text: wiki markup counts as characters.
    ///
    /// ```swift
    /// try Language.of("sh").detectScript("Москва")?.code   // "Cyrl"
    /// try Language.of("zh").detectScript("語言")?.code      // "Hant"
    /// try Language.of("en").detectScript("123")            // nil
    /// ```
    public func detectScript(_ text: String) -> Script? { link.resolved.detectScript(self, text) }

    public static func == (left: Language, right: Language) -> Bool {
        left.code == right.code && left.name == right.name && left.type == right.type
            && left.wikidata == right.wikidata && left.familyCode == right.familyCode
            && left.scriptCodes == right.scriptCodes && left.ancestorCodes == right.ancestorCodes
            && left.aliases == right.aliases && left.parentCode == right.parentCode && left.varieties == right.varieties
    }

    public func hash(into hasher: inout Hasher) {
        hasher.combine(code)
        hasher.combine(name)
    }

    /// Its name and code, as `English (en)`.
    public var description: String { "\(name) (\(code))" }

    /// The language with `code`, or `nil` when Wiktionary has none.
    public static subscript(code: String) -> Language? { Catalog.bundled[code] }

    /// The language with `code`.
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

/// One language family as English Wiktionary's `Module:families` describes it.
public final class LanguageFamily: Hashable, CustomStringConvertible, @unchecked Sendable {
    /// Its code, such as `ine` or `gmw-fri`.
    public let code: String
    /// Its name, such as `Indo-European`.
    public let name: String
    /// Its Wikidata item, or `nil`.
    public let wikidata: String?
    /// Other names it goes by.
    public let aliases: [String]
    let parentCode: String?
    let protoLanguageCode: String?
    private let link: CatalogLink

    init(row: CatalogTable.FamilyRow, link: CatalogLink) {
        code = row.code
        name = row.name
        wikidata = row.wikidata
        parentCode = row.parentCode
        protoLanguageCode = row.protoLanguageCode
        aliases = row.aliases
        self.link = link
    }

    /// The family it belongs to, or `nil`.
    public var parent: LanguageFamily? { parentCode.flatMap { link.resolved.family($0) } }

    /// The language it descends from: the one Wiktionary names, or else `<code>-pro` when there is one.
    public var protoLanguage: Language? { link.resolved[protoLanguageCode ?? "\(code)-pro"] }

    /// The languages in it directly, not those of the families under it, sorted by code.
    public var languages: [Language] { link.resolved.inFamily(code) }

    public static func == (left: LanguageFamily, right: LanguageFamily) -> Bool {
        left.code == right.code && left.name == right.name && left.wikidata == right.wikidata
            && left.parentCode == right.parentCode && left.protoLanguageCode == right.protoLanguageCode
            && left.aliases == right.aliases
    }

    public func hash(into hasher: inout Hasher) {
        hasher.combine(code)
        hasher.combine(name)
    }

    /// Its name and code, as `Indo-European (ine)`.
    public var description: String { "\(name) (\(code))" }

    /// The family with `code`, or `nil` when Wiktionary has none.
    public static subscript(code: String) -> LanguageFamily? { Catalog.bundled.family(code) }

    /// The family with `code`.
    public static func of(_ code: String) throws -> LanguageFamily {
        guard let family = Catalog.bundled.family(code) else { throw PolyglossaError.unknownFamily(code) }
        return family
    }

    /// Whether Wiktionary has a family with `code`.
    public static func contains(_ code: String) -> Bool { Catalog.bundled.family(code) != nil }

    /// Every family, sorted by code.
    public static var all: [LanguageFamily] { Catalog.bundled.families }
}
