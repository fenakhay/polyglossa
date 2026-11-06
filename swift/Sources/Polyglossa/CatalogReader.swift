/// Reads the tab-separated `catalog.tsv` the Kotlin library writes: a header, `polyglossa`, the format and the
/// day the data was exported, then a language (`L`), a family (`F`) or a script (`S`) to a line. Codes are
/// joined by commas, names by semicolons, a variety's names by bars, a script's characters and marks as ranges
/// of hexadecimal code points such as `41-5A,61-7A,AA`; a tab, a line break, a backslash, a semicolon or a bar
/// in a name is escaped by a backslash.
enum CatalogReader {
    static let header = "polyglossa\t1"

    /// A catalog as the text has it: each entry under its code, the later of two with the same code winning.
    struct Read {
        var date: String?
        var languages: [String: LanguageRecord] = [:]
        var families: [String: FamilyRecord] = [:]
        var scripts: [String: ScriptRecord] = [:]
    }

    private static let types: [Substring: LanguageType] =
        ["": .regular, "r": .reconstructed, "a": .appendixConstructed, "e": .etymologyOnly]
    private static let directions: [Substring: ScriptDirection] =
        ["rtl": .rightToLeft, "vertical-ltr": .verticalLeftToRight, "vertical-rtl": .verticalRightToLeft]

    /// Reads `text`, as the Kotlin library's `Catalog.toTsv` writes it.
    ///
    /// - Throws: ``PolyglossaError/notACatalog(_:)``, starting with the line, when `text` is not such a catalog.
    static func read(_ text: String) throws -> Read {
        let lines = text.split(separator: "\n", omittingEmptySubsequences: false)
        let first = lines.first ?? ""
        guard first == header || first.hasPrefix(header + "\t") else { throw notACatalog(1, "no header") }
        let date = first.dropFirst(header.count + 1)
        var read = Read(date: date.isEmpty ? nil : String(date))
        for (offset, line) in lines.enumerated().dropFirst() where !line.isEmpty {
            var cells = Cells(line)
            let kind = cells.next()
            let code = String(cells.next())
            do {
                switch kind {
                case "L": read.languages[code] = try language(code, &cells)
                case "F": read.families[code] = family(code, &cells)
                case "S": read.scripts[code] = try script(code, &cells)
                default: throw Unreadable("not a language, a family or a script: \(kind)")
                }
            } catch let unreadable as Unreadable {
                throw notACatalog(offset + 1, unreadable.reason)
            }
        }
        return read
    }

    private struct Unreadable: Error {
        let reason: String

        init(_ reason: String) { self.reason = reason }
    }

    private static func notACatalog(_ line: Int, _ reason: String) -> PolyglossaError {
        .notACatalog("line \(line): \(reason)")
    }

    /// The cells of one line, read in order; one left off the end reads as empty.
    private struct Cells {
        private var rest: Substring?

        init(_ line: Substring) { rest = line }

        mutating func next() -> Substring {
            guard let line = rest else { return "" }
            guard let tab = line.firstIndex(of: "\t") else {
                rest = nil
                return line
            }
            rest = line[line.index(after: tab)...]
            return line[..<tab]
        }

        mutating func optional() -> String? {
            let cell = next()
            return cell.isEmpty ? nil : String(cell)
        }
    }

    private static func language(_ code: String, _ cells: inout Cells) throws -> LanguageRecord {
        let name = unescape(cells.next())
        let mark = cells.next()
        guard let type = types[mark] else { throw Unreadable("no language type \(mark)") }
        return LanguageRecord(
            code: code,
            name: name,
            type: type,
            wikidata: item(cells.next()),
            familyCode: cells.optional(),
            scriptCodes: codes(cells.next()),
            ancestorCodes: codes(cells.next()),
            aliases: names(cells.next()),
            parentCode: cells.optional(),
            varieties: split(cells.next(), ";").map { written in
                let names = split(written, "|").map(unescape)
                return Variety(name: names.first ?? "", otherNames: Array(names.dropFirst()))
            }
        )
    }

    private static func family(_ code: String, _ cells: inout Cells) -> FamilyRecord {
        FamilyRecord(
            code: code,
            name: unescape(cells.next()),
            wikidata: item(cells.next()),
            parentCode: cells.optional(),
            protoLanguageCode: cells.optional(),
            aliases: names(cells.next())
        )
    }

    private static func script(_ code: String, _ cells: inout Cells) throws -> ScriptRecord {
        let name = unescape(cells.next())
        let wikidata = item(cells.next())
        let system = unescape(cells.next())
        let aliases = names(cells.next())
        let parent = cells.optional()
        let direction = directions[cells.next()] ?? .leftToRight
        var localNames: [String: String] = [:]
        for entry in names(cells.next()) {
            if let equals = entry.firstIndex(of: "=") {
                localNames[String(entry[..<equals])] = String(entry[entry.index(after: equals)...])
            }
        }
        return ScriptRecord(
            code: code,
            name: name,
            wikidata: wikidata,
            system: system.isEmpty ? nil : system,
            aliases: aliases,
            parentCode: parent,
            direction: direction,
            localNames: localNames,
            ranges: try codePoints(cells.next()),
            marks: try codePoints(cells.next())
        )
    }

    /// The ranges of hexadecimal code points in `cell`, as `41-5A,AA`.
    ///
    /// - Throws: When a piece of `cell` is not a code point or a range of them.
    private static func codePoints(_ cell: Substring) throws -> [ClosedRange<Int>] {
        guard !cell.isEmpty else { return [] }
        return try cell.split(separator: ",", omittingEmptySubsequences: false).map { written in
            let ends = written.split(separator: "-", maxSplits: 1, omittingEmptySubsequences: false)
            guard let first = ends.first.flatMap({ Int($0, radix: 16) }),
                  let last = ends.count > 1 ? Int(ends[1], radix: 16) : first,
                  first <= last
            else { throw Unreadable("no code points \(written)") }
            return first...last
        }
    }

    private static func item(_ cell: Substring) -> String? { cell.isEmpty ? nil : "Q" + cell }

    private static func codes(_ cell: Substring) -> [String] {
        cell.isEmpty ? [] : cell.split(separator: ",", omittingEmptySubsequences: false).map(String.init)
    }

    private static func names(_ cell: Substring) -> [String] { split(cell, ";").map(unescape) }

    /// `cell` split at each `separator` that is not escaped, the pieces left escaped; none for an empty cell.
    private static func split(_ cell: Substring, _ separator: Character) -> [Substring] {
        guard !cell.isEmpty else { return [] }
        guard cell.contains("\\") else { return cell.split(separator: separator, omittingEmptySubsequences: false) }
        var pieces: [Substring] = []
        var start = cell.startIndex
        var at = cell.startIndex
        while at < cell.endIndex {
            if cell[at] == "\\" {
                at = cell.index(after: at)
                if at == cell.endIndex { break }
            } else if cell[at] == separator {
                pieces.append(cell[start..<at])
                start = cell.index(after: at)
            }
            at = cell.index(after: at)
        }
        pieces.append(cell[start...])
        return pieces
    }

    /// `text` with the escapes the Kotlin library writes undone: `\t` and `\n` for a tab and a line break, and
    /// any other escaped character as itself.
    private static func unescape(_ text: Substring) -> String {
        guard text.contains("\\") else { return String(text) }
        var result = ""
        var isEscaped = false
        for character in text {
            if isEscaped {
                switch character {
                case "t": result.append("\t")
                case "n": result.append("\n")
                default: result.append(character)
                }
                isEscaped = false
            } else if character == "\\" {
                isEscaped = true
            } else {
                result.append(character)
            }
        }
        if isEscaped { result.append("\\") }
        return result
    }
}
