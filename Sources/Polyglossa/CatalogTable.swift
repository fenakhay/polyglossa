/// Reads the tab-separated `catalog.tsv` the Kotlin library writes: a header, `polyglossa`, the format and the
/// day the data was exported, then a language (`L`), a family (`F`) or a script (`S`) to a line. Codes are
/// joined by commas, names by semicolons, a variety's names by bars; a tab, a line break, a backslash, a
/// semicolon or a bar in a name is escaped by a backslash.
enum CatalogTable {
    static let header = "polyglossa\t1"

    struct LanguageRow {
        let code: String, name: String, type: LanguageType, wikidata: String?, familyCode: String?
        let scriptCodes: [String], ancestorCodes: [String], aliases: [String], parentCode: String?
        let varieties: [Variety]
    }

    struct FamilyRow {
        let code: String, name: String, wikidata: String?, parentCode: String?, protoLanguageCode: String?
        let aliases: [String]
    }

    struct ScriptRow {
        let code: String, name: String, wikidata: String?, system: String?, aliases: [String]
        let parentCode: String?, direction: ScriptDirection, localNames: [String: String]
    }

    struct Table {
        let date: String?
        let languages: [LanguageRow]
        let families: [FamilyRow]
        let scripts: [ScriptRow]
    }

    private static let types: [String: LanguageType] =
        ["": .regular, "r": .reconstructed, "a": .appendixConstructed, "e": .etymologyOnly]

    static func read(_ text: String) throws -> Table {
        let lines = text.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
        let first = lines.first ?? ""
        guard first == header || first.hasPrefix(header + "\t") else { throw PolyglossaError.notACatalog("no header") }
        let date = first.count > header.count ? String(first.dropFirst(header.count + 1)) : nil
        var languages: [LanguageRow] = []
        var families: [FamilyRow] = []
        var scripts: [ScriptRow] = []
        for line in lines.dropFirst() where !line.isEmpty {
            var cells = Cells(line.split(separator: "\t", omittingEmptySubsequences: false).map(String.init))
            switch cells.next() {
            case "L": languages.append(try language(&cells))
            case "F": families.append(family(&cells))
            case "S": scripts.append(script(&cells))
            default: throw PolyglossaError.notACatalog("not a language, a family or a script: \(line)")
            }
        }
        return Table(date: date.flatMap { $0.isEmpty ? nil : $0 }, languages: languages, families: families, scripts: scripts)
    }

    /// The cells of one line, read in order; one left off the end reads as empty.
    private struct Cells {
        let cells: [String]
        var at = 0

        init(_ cells: [String]) { self.cells = cells }

        mutating func next() -> String {
            defer { at += 1 }
            return at < cells.count ? cells[at] : ""
        }
    }

    private static func language(_ cells: inout Cells) throws -> LanguageRow {
        let code = cells.next()
        let name = unescape(cells.next())
        let mark = cells.next()
        guard let type = types[mark] else { throw PolyglossaError.notACatalog("no language type \(mark)") }
        let wikidata = item(cells.next())
        let family = optional(cells.next())
        let scripts = codes(cells.next())
        let ancestors = codes(cells.next())
        let aliases = names(cells.next())
        let parent = optional(cells.next())
        let varieties = split(cells.next(), ";").map { variety -> Variety in
            let names = split(variety, "|").map(unescape)
            return Variety(name: names.first ?? "", otherNames: Array(names.dropFirst()))
        }
        return LanguageRow(code: code, name: name, type: type, wikidata: wikidata, familyCode: family,
                           scriptCodes: scripts, ancestorCodes: ancestors, aliases: aliases, parentCode: parent,
                           varieties: varieties)
    }

    private static func family(_ cells: inout Cells) -> FamilyRow {
        let code = cells.next()
        let name = unescape(cells.next())
        let wikidata = item(cells.next())
        let parent = optional(cells.next())
        let proto = optional(cells.next())
        let aliases = names(cells.next())
        return FamilyRow(code: code, name: name, wikidata: wikidata, parentCode: parent, protoLanguageCode: proto,
                         aliases: aliases)
    }

    private static func script(_ cells: inout Cells) -> ScriptRow {
        let code = cells.next()
        let name = unescape(cells.next())
        let wikidata = item(cells.next())
        let system = optional(unescape(cells.next()))
        let aliases = names(cells.next())
        let parent = optional(cells.next())
        let direction = ScriptDirection(rawValue: cells.next()) ?? .leftToRight
        var localNames: [String: String] = [:]
        for entry in names(cells.next()) {
            if let equals = entry.firstIndex(of: "=") {
                localNames[String(entry[..<equals])] = String(entry[entry.index(after: equals)...])
            }
        }
        return ScriptRow(code: code, name: name, wikidata: wikidata, system: system, aliases: aliases,
                         parentCode: parent, direction: direction, localNames: localNames)
    }

    private static func optional(_ cell: String) -> String? { cell.isEmpty ? nil : cell }

    private static func item(_ cell: String) -> String? { cell.isEmpty ? nil : "Q" + cell }

    private static func codes(_ cell: String) -> [String] {
        cell.isEmpty ? [] : cell.split(separator: ",", omittingEmptySubsequences: false).map(String.init)
    }

    private static func names(_ cell: String) -> [String] { split(cell, ";").map(unescape) }

    /// `cell` split at each `separator` that is not escaped, the pieces left escaped; none for an empty cell.
    private static func split(_ cell: String, _ separator: Character) -> [String] {
        if cell.isEmpty { return [] }
        var pieces: [String] = []
        var current = ""
        var escaped = false
        for character in cell {
            if escaped {
                current.append(character)
                escaped = false
            } else if character == "\\" {
                current.append(character)
                escaped = true
            } else if character == separator {
                pieces.append(current)
                current = ""
            } else {
                current.append(character)
            }
        }
        pieces.append(current)
        return pieces
    }

    /// `text` with the escapes the Kotlin library writes undone: `\t` and `\n` for a tab and a line break, and
    /// any other escaped character as itself.
    private static func unescape(_ text: String) -> String {
        guard text.contains("\\") else { return text }
        var result = ""
        var escaped = false
        for character in text {
            if escaped {
                switch character {
                case "t": result.append("\t")
                case "n": result.append("\n")
                default: result.append(character)
                }
                escaped = false
            } else if character == "\\" {
                escaped = true
            } else {
                result.append(character)
            }
        }
        if escaped { result.append("\\") }
        return result
    }
}
