/// A code one entry gives for another, resolved when the catalog is read: to the entry's place when the catalog
/// has it, or kept as written when it does not.
enum Link: Equatable, Sendable {
    case known(Int)
    case unknown(String)

    var place: Int? {
        if case .known(let place) = self { return place }
        return nil
    }
}

/// A language as the text has it, then linked: the codes it gives for other entries, kept for equality, and the
/// places they lead to.
struct LanguageRecord: Sendable {
    let code: String
    let name: String
    let type: LanguageType
    let wikidata: String?
    let familyCode: String?
    let scriptCodes: [String]
    let ancestorCodes: [String]
    let aliases: [String]
    let parentCode: String?
    let varieties: [Variety]
    var family: Link?
    var scripts: [Link] = []
    var ancestors: [Int] = []
    var parent: Int?

    init(code: String, name: String, type: LanguageType, wikidata: String?, familyCode: String?,
         scriptCodes: [String], ancestorCodes: [String], aliases: [String], parentCode: String?,
         varieties: [Variety]) {
        self.code = code
        self.name = name
        self.type = type
        self.wikidata = wikidata
        self.familyCode = familyCode
        self.scriptCodes = scriptCodes
        self.ancestorCodes = ancestorCodes
        self.aliases = aliases
        self.parentCode = parentCode
        self.varieties = varieties
    }

    /// Whether `other` says the same as this, whatever catalog each comes from.
    func says(_ other: LanguageRecord) -> Bool {
        code == other.code && name == other.name && type == other.type && wikidata == other.wikidata
            && familyCode == other.familyCode && scriptCodes == other.scriptCodes
            && ancestorCodes == other.ancestorCodes && aliases == other.aliases && parentCode == other.parentCode
            && varieties == other.varieties
    }
}

/// A family as the text has it, then linked.
struct FamilyRecord: Sendable {
    let code: String
    let name: String
    let wikidata: String?
    let parentCode: String?
    let protoLanguageCode: String?
    let aliases: [String]
    var parent: Int?
    var protoLanguage: Int?
    var languages: [Int] = []

    init(code: String, name: String, wikidata: String?, parentCode: String?, protoLanguageCode: String?,
         aliases: [String]) {
        self.code = code
        self.name = name
        self.wikidata = wikidata
        self.parentCode = parentCode
        self.protoLanguageCode = protoLanguageCode
        self.aliases = aliases
    }

    /// Whether `other` says the same as this, whatever catalog each comes from.
    func says(_ other: FamilyRecord) -> Bool {
        code == other.code && name == other.name && wikidata == other.wikidata && parentCode == other.parentCode
            && protoLanguageCode == other.protoLanguageCode && aliases == other.aliases
    }
}

/// A script as the text has it, then linked.
struct ScriptRecord: Sendable {
    let code: String
    let name: String
    let wikidata: String?
    let system: String?
    let aliases: [String]
    let parentCode: String?
    let direction: ScriptDirection
    let localNames: [String: String]
    let ranges: [ClosedRange<Int>]
    let marks: [ClosedRange<Int>]
    var parent: Int?

    init(code: String, name: String, wikidata: String?, system: String?, aliases: [String], parentCode: String?,
         direction: ScriptDirection, localNames: [String: String], ranges: [ClosedRange<Int>],
         marks: [ClosedRange<Int>]) {
        self.code = code
        self.name = name
        self.wikidata = wikidata
        self.system = system
        self.aliases = aliases
        self.parentCode = parentCode
        self.direction = direction
        self.localNames = localNames
        self.ranges = ranges
        self.marks = marks
    }

    /// Whether `other` says the same as this, whatever catalog each comes from.
    func says(_ other: ScriptRecord) -> Bool {
        code == other.code && name == other.name && wikidata == other.wikidata && system == other.system
            && aliases == other.aliases && parentCode == other.parentCode && direction == other.direction
            && localNames == other.localNames && ranges == other.ranges && marks == other.marks
    }
}

/// What a catalog holds, read and linked once and never changed after: every entry sorted by code, the indexes
/// that find them, and the tables that detection reads.
final class CatalogStorage: Sendable {
    let date: String?
    let languages: [LanguageRecord]
    let families: [FamilyRecord]
    let scripts: [ScriptRecord]
    let languageCodes: [String: Int]
    let familyCodes: [String: Int]
    let scriptCodes: [String: Int]
    let languageNames: [String: Int]
    let scriptNames: [String: Int]
    let detection: ScriptDetection

    init(_ read: CatalogReader.Read) {
        date = read.date
        var languages = read.languages.values.sorted { $0.code < $1.code }
        var families = read.families.values.sorted { $0.code < $1.code }
        var scripts = read.scripts.values.sorted { $0.code < $1.code }
        let languageCodes = Self.places(languages.map(\.code))
        let familyCodes = Self.places(families.map(\.code))
        let scriptCodes = Self.places(scripts.map(\.code))
        let link = { (codes: [String: Int], code: String) -> Link in codes[code].map(Link.known) ?? .unknown(code) }

        for place in languages.indices {
            let record = languages[place]
            languages[place].family = record.familyCode.map { link(familyCodes, $0) }
            languages[place].scripts = record.scriptCodes.map { link(scriptCodes, $0) }
            languages[place].ancestors = record.ancestorCodes.compactMap { languageCodes[$0] }
            languages[place].parent = record.parentCode.flatMap { languageCodes[$0] }
            if case .known(let family)? = languages[place].family { families[family].languages.append(place) }
        }
        for place in families.indices {
            let record = families[place]
            families[place].parent = record.parentCode.flatMap { familyCodes[$0] }
            families[place].protoLanguage = languageCodes[record.protoLanguageCode ?? "\(record.code)-pro"]
        }
        for place in scripts.indices {
            scripts[place].parent = scripts[place].parentCode.flatMap { scriptCodes[$0] }
        }

        self.languages = languages
        self.families = families
        self.scripts = scripts
        self.languageCodes = languageCodes
        self.familyCodes = familyCodes
        self.scriptCodes = scriptCodes
        languageNames = Self.names(of: languages.map { ($0.name, $0.type != .etymologyOnly) })
        scriptNames = Self.names(of: scripts.map { ($0.name, $0.parentCode == nil) })
        detection = ScriptDetection(scripts: scripts, codes: scriptCodes)
    }

    /// Each of `codes`, which are sorted and each once, with its place among them.
    private static func places(_ codes: [String]) -> [String: Int] {
        Dictionary(uniqueKeysWithValues: codes.enumerated().map { ($1, $0) })
    }

    /// Each name with the place of the entry that has it, one that wins beating one that does not, and the later
    /// beating the earlier otherwise.
    private static func names(of entries: [(name: String, wins: Bool)]) -> [String: Int] {
        var names: [String: Int] = [:]
        for winners in [false, true] {
            for (place, entry) in entries.enumerated() where entry.wins == winners { names[entry.name] = place }
        }
        return names
    }
}

extension Array where Element == ClosedRange<Int> {
    /// Whether one of these ranges, sorted and apart, holds `codePoint`.
    func holds(_ codePoint: Int) -> Bool {
        var low = 0
        var high = count
        while low < high {
            let middle = (low + high) / 2
            if self[middle].upperBound < codePoint { low = middle + 1 } else { high = middle }
        }
        return low < count && self[low].lowerBound <= codePoint
    }
}
