/// How Wiktionary tells which script a text is in, as the Kotlin library does: `Language:findBestScript` in
/// `Module:languages` for a language, and `findBestScriptWithoutLang` in `Module:scripts/charToScript` for none.
///
/// Every table is indexed by a script's place in the catalog. The code points are cut into segments at every
/// start and end of a script's range, so the scripts that hold a code point are those of its segment:
/// `holders[offsets[i]..<offsets[i + 1]]` for segment `i`, which starts at `segments[i]`.
struct ScriptDetection: Sendable {
    private static let han = ["Hani", "Hant", "Hans"]
    private static let combined: [(code: String, parts: [String])] = [
        ("Jpan", ["Hani", "Hira", "Kana"]),
        ("Kore", ["Hani", "Hang"]),
    ]

    /// A script made of others, as Japanese is of Han, Hiragana and Katakana; a part the catalog lacks is `nil`.
    private struct Combination: Sendable {
        let script: Int?
        let parts: [Int?]
    }

    /// What a script is ranked by after its count: whether it holds another whole, whether it is over notation,
    /// how many characters it has, whether it is a variety, and its code.
    private struct Rank: Sendable {
        let isSuperset: Bool
        let isNotation: Bool
        let size: Int
        let isVariety: Bool
        let code: String

        func precedes(_ other: Rank) -> Bool {
            if isSuperset != other.isSuperset { return !isSuperset }
            if isNotation != other.isNotation { return !isNotation }
            if size != other.size { return size > other.size }
            if isVariety != other.isVariety { return !isVariety }
            return code < other.code
        }
    }

    private let scripts: [ScriptRecord]
    private let ranks: [Rank]
    private let segments: [Int]
    private let offsets: [Int]
    private let holders: [Int]
    private let combinations: [Combination]
    private let hanPlaces: (han: Int?, traditional: Int?, simplified: Int?)

    init(scripts: [ScriptRecord], codes: [String: Int]) {
        self.scripts = scripts
        combinations = Self.combined.map { Combination(script: codes[$0.code], parts: $0.parts.map { codes[$0] }) }
        hanPlaces = (codes[Self.han[0]], codes[Self.han[1]], codes[Self.han[2]])
        let combinedCodes = Set(Self.combined.map(\.code))
        let indexed = scripts.indices.filter { !scripts[$0].ranges.isEmpty && !combinedCodes.contains(scripts[$0].code) }

        var sizes = Array(repeating: 0, count: scripts.count)
        for place in indexed { sizes[place] = scripts[place].ranges.reduce(0) { $0 + $1.count } }
        for combination in combinations {
            guard let script = combination.script else { continue }
            sizes[script] = combination.parts.reduce(0) { $0 + ($1.map { sizes[$0] } ?? 0) }
        }
        var isSuperset = Array(repeating: false, count: scripts.count)
        for outer in indexed {
            isSuperset[outer] = indexed.contains { inner in
                scripts[inner].ranges != scripts[outer].ranges && Self.covers(scripts[outer].ranges, scripts[inner].ranges)
            }
        }
        ranks = scripts.indices.map { place in
            Rank(isSuperset: isSuperset[place], isNotation: Self.isNotation(scripts[place].code), size: sizes[place],
                 isVariety: scripts[place].parentCode != nil, code: scripts[place].code)
        }

        let segments = Array(Set(indexed.flatMap { scripts[$0].ranges.flatMap { [$0.lowerBound, $0.upperBound + 1] } }))
            .sorted()
        var held = Array(repeating: [Int](), count: segments.count)
        for place in indexed {
            for range in scripts[place].ranges {
                for segment in Self.segment(of: range.lowerBound, in: segments)..<Self.segment(of: range.upperBound + 1, in: segments) {
                    held[segment].append(place)
                }
            }
        }
        var offsets = [0]
        offsets.reserveCapacity(held.count + 1)
        var holders: [Int] = []
        for segment in held {
            holders.append(contentsOf: segment)
            offsets.append(holders.count)
        }
        self.segments = segments
        self.offsets = offsets
        self.holders = holders
    }

    /// The place of the segment that starts at `codePoint` or, when none does, of the one that holds it; `-1`
    /// before the first.
    private static func segment(of codePoint: Int, in segments: [Int]) -> Int {
        var low = 0
        var high = segments.count
        while low < high {
            let middle = (low + high) / 2
            if segments[middle] <= codePoint { low = middle + 1 } else { high = middle }
        }
        return low - 1
    }

    /// The places of the scripts that hold `codePoint`.
    private func holding(_ codePoint: Int) -> ArraySlice<Int> {
        let segment = Self.segment(of: codePoint, in: segments)
        return segment < 0 ? [] : holders[offsets[segment]..<offsets[segment + 1]]
    }

    func detect(_ text: String) -> Int? {
        var tallies: [(script: Int, count: Int)] = []
        var together = Array(repeating: 0, count: combinations.count)
        var unmatched = 0
        for scalar in text.unicodeScalars {
            let found = holding(Int(scalar.value))
            if found.isEmpty {
                unmatched += 1
                continue
            }
            for script in found {
                if let at = tallies.firstIndex(where: { $0.script == script }) {
                    tallies[at].count += 1
                } else {
                    tallies.append((script, 1))
                }
            }
            for (at, combination) in combinations.enumerated() where found.contains(where: { combination.parts.contains($0) }) {
                together[at] += 1
            }
        }
        for (at, combination) in combinations.enumerated() {
            guard let script = combination.script else { continue }
            let onePartHasAll = combination.parts.contains { part in
                (part.flatMap { part in tallies.first { $0.script == part }?.count } ?? 0) == together[at]
            }
            if !onePartHasAll { tallies.append((script, together[at])) }
        }
        let best = tallies.min { left, right in
            left.count != right.count ? left.count > right.count : ranks[left.script].precedes(ranks[right.script])
        }
        guard let best, best.count >= unmatched else { return nil }
        return best.script
    }

    func detect(_ language: LanguageRecord, lineage: [LanguageRecord], _ text: String) -> Int? {
        guard let listed = lineage.first(where: { !$0.scripts.isEmpty })?.scripts else { return nil }
        if isEveryScript(listed) { return detect(text) }
        let points = text.unicodeScalars.lazy.map { Int($0.value) }.filter { !Self.isIgnored($0) }
        let codePoints = Array(points)
        if codePoints.isEmpty { return nil }
        var best: Int?
        var bestCount = 0
        var at = 0
        while at < listed.count {
            if isHan(listed, at) {
                at += Self.han.count
                if let han = decideHan(codePoints) { return han }
                continue
            }
            defer { at += 1 }
            guard let script = listed[at].place else { continue }
            let ranges = scripts[script].ranges
            let count = codePoints.reduce(0) { $0 + (ranges.holds($1) ? 1 : 0) }
            if count >= codePoints.count { return script }
            if count > bestCount {
                best = script
                bestCount = count
            }
        }
        return best
    }

    /// Whether `listed` lists every script of the catalog, as Translingual's does.
    private func isEveryScript(_ listed: [Link]) -> Bool {
        guard listed.count >= scripts.count else { return false }
        var isListed = Array(repeating: false, count: scripts.count)
        for link in listed { if let place = link.place { isListed[place] = true } }
        return !isListed.contains(false)
    }

    /// Whether `listed` has Han, Traditional Han and Simplified Han side by side at `at`, as Wiktionary's `Hants`.
    private func isHan(_ listed: [Link], _ at: Int) -> Bool {
        guard at + Self.han.count <= listed.count else { return false }
        return Self.han.indices.allSatisfy { offset in code(of: listed[at + offset]) == Self.han[offset] }
    }

    private func code(of link: Link) -> String {
        switch link {
        case .known(let place): scripts[place].code
        case .unknown(let code): code
        }
    }

    /// Traditional or Simplified Han when more of the Han characters mark one than the other, Han when as many
    /// mark each, and `nil` when no character is Han.
    private func decideHan(_ codePoints: [Int]) -> Int? {
        guard let han = hanPlaces.han else { return nil }
        let traditional = hanPlaces.traditional, simplified = hanPlaces.simplified
        var isFound = false
        var traditionalMarks = 0
        var simplifiedMarks = 0
        for point in codePoints where scripts[han].ranges.holds(point) {
            isFound = true
            let isTraditional = traditional.map { scripts[$0].marks.holds(point) } ?? false
            let isSimplified = simplified.map { scripts[$0].marks.holds(point) } ?? false
            if isTraditional && !isSimplified { traditionalMarks += 1 }
            if isSimplified && !isTraditional { simplifiedMarks += 1 }
        }
        guard isFound else { return nil }
        if traditionalMarks > simplifiedMarks { return traditional ?? han }
        if simplifiedMarks > traditionalMarks { return simplified ?? han }
        return han
    }

    private static func isNotation(_ code: String) -> Bool { code.utf8.count == 4 && code.hasPrefix("Z") }

    private static func covers(_ outer: [ClosedRange<Int>], _ inner: [ClosedRange<Int>]) -> Bool {
        inner.allSatisfy { range in outer.contains { $0.lowerBound <= range.lowerBound && range.upperBound <= $0.upperBound } }
    }

    /// Whether `codePoint` is left out of a text whose language is known: spaces, and ASCII punctuation.
    private static func isIgnored(_ codePoint: Int) -> Bool {
        switch codePoint {
        case 0x9...0xD, 0x20, 0xA0, 0x1680, 0x2000...0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000: true
        case 0x21, 0x22, 0x23, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2A, 0x2C, 0x2D, 0x2E, 0x2F, 0x3A, 0x3B, 0x3F, 0x40,
             0x5B, 0x5C, 0x5D, 0x5F, 0x7B, 0x7D: true
        default: false
        }
    }
}
