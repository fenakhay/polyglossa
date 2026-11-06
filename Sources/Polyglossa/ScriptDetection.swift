/// How Wiktionary tells which script a text is in, as the Kotlin library does: `Language:findBestScript` in
/// `Module:languages` for a language, and `findBestScriptWithoutLang` in `Module:scripts/charToScript` for none.
final class ScriptDetection: @unchecked Sendable {
    private static let han = ["Hani", "Hant", "Hans"]
    private static let hants = "Hants"
    private static let combined: [(String, [String])] = [("Jpan", ["Hani", "Hira", "Kana"]), ("Kore", ["Hani", "Hang"])]
    private static let punctuation = Set("!\"#%&'()*,-./:;?@[\\]_{}".unicodeScalars.map { Int($0.value) })
    private static let spaces: [ClosedRange<Int>] = [
        0x9...0xD, 0x20...0x20, 0xA0...0xA0, 0x1680...0x1680, 0x2000...0x200A, 0x2028...0x2029, 0x202F...0x202F,
        0x205F...0x205F, 0x3000...0x3000,
    ]

    private let byCode: [String: Script]
    private let starts: [Int]
    private let holders: [[Script]]
    private let sizes: [String: Int]
    private let supersets: Set<String>

    init(scripts: [String: Script]) {
        byCode = scripts
        let indexed = scripts.values.filter { script in
            !script.ranges.isEmpty && !ScriptDetection.combined.contains { $0.0 == script.code }
        }
        sizes = Dictionary(uniqueKeysWithValues: indexed.map { ($0.code, $0.ranges.reduce(0) { $0 + $1.count }) })
        supersets = Set(indexed.filter { outer in
            indexed.contains { inner in inner.ranges != outer.ranges && ScriptDetection.covers(outer, inner) }
        }.map(\.code))
        starts = Array(Set(indexed.flatMap { $0.ranges.flatMap { [$0.lowerBound, $0.upperBound + 1] } })).sorted()
        let index = Dictionary(uniqueKeysWithValues: starts.enumerated().map { ($1, $0) })
        var found = Array(repeating: [Script](), count: starts.count)
        for script in indexed {
            for range in script.ranges {
                for at in index[range.lowerBound]! ..< index[range.upperBound + 1]! { found[at].append(script) }
            }
        }
        holders = found
    }

    private func holding(_ codePoint: Int) -> [Script] {
        var low = 0
        var high = starts.count - 1
        var at = -1
        while low <= high {
            let mid = (low + high) / 2
            if starts[mid] <= codePoint {
                at = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return at < 0 ? [] : holders[at]
    }

    func detect(_ text: String) -> Script? {
        var counts: [String: Int] = [:]
        var combined = Array(repeating: 0, count: ScriptDetection.combined.count)
        var unmatched = 0
        for scalar in text.unicodeScalars {
            let found = holding(Int(scalar.value))
            if found.isEmpty { unmatched += 1 }
            for script in found { counts[script.code, default: 0] += 1 }
            for (at, (_, parts)) in ScriptDetection.combined.enumerated() where found.contains(where: { parts.contains($0.code) }) {
                combined[at] += 1
            }
        }
        for (at, (code, parts)) in ScriptDetection.combined.enumerated() {
            let shares = parts.map { counts[$0] ?? 0 }
            if byCode[code] != nil && !shares.contains(combined[at]) { counts[code] = combined[at] }
        }
        let ranked = counts.compactMap { code, count in byCode[code].map { ($0, count) } }.sorted { left, right in
            if left.1 != right.1 { return left.1 > right.1 }
            let leftSuperset = supersets.contains(left.0.code), rightSuperset = supersets.contains(right.0.code)
            if leftSuperset != rightSuperset { return !leftSuperset }
            let leftNotation = ScriptDetection.isNotation(left.0), rightNotation = ScriptDetection.isNotation(right.0)
            if leftNotation != rightNotation { return !leftNotation }
            let leftSize = size(left.0), rightSize = size(right.0)
            if leftSize != rightSize { return leftSize > rightSize }
            if (left.0.parentCode == nil) != (right.0.parentCode == nil) { return left.0.parentCode == nil }
            return left.0.code < right.0.code
        }
        guard let best = ranked.first, best.1 >= unmatched else { return nil }
        return best.0
    }

    private static func isNotation(_ script: Script) -> Bool { script.code.count == 4 && script.code.hasPrefix("Z") }

    private static func covers(_ outer: Script, _ inner: Script) -> Bool {
        inner.ranges.allSatisfy { range in
            outer.ranges.contains { $0.lowerBound <= range.lowerBound && range.upperBound <= $0.upperBound }
        }
    }

    private func size(_ script: Script) -> Int {
        if let size = sizes[script.code] { return size }
        let parts = ScriptDetection.combined.first { $0.0 == script.code }?.1 ?? []
        return parts.reduce(0) { $0 + (sizes[$1] ?? 0) }
    }

    func detect(_ language: Language, _ text: String) -> Script? {
        let codes = scriptCodes(language)
        if codes.isEmpty { return nil }
        if codes.count >= byCode.count && Set(codes).isSuperset(of: byCode.keys) { return detect(text) }
        let points = text.unicodeScalars.map { Int($0.value) }.filter { !ScriptDetection.ignored($0) }
        if points.isEmpty { return nil }
        var best: Script?
        var bestCount = 0
        for code in groups(codes) {
            if code == ScriptDetection.hants {
                if let script = han(points) { return script }
                continue
            }
            guard let script = byCode[code] else { continue }
            let count = points.filter { script.contains($0) }.count
            if count >= points.count { return script }
            if count > bestCount {
                best = script
                bestCount = count
            }
        }
        return best
    }

    private func scriptCodes(_ language: Language) -> [String] {
        var current: Language? = language
        var seen: Set<String> = []
        while let next = current, seen.insert(next.code).inserted {
            if !next.scriptCodes.isEmpty { return next.scriptCodes }
            current = next.parent
        }
        return []
    }

    private func groups(_ codes: [String]) -> [String] {
        var groups: [String] = []
        var at = 0
        while at < codes.count {
            let end = min(at + ScriptDetection.han.count, codes.count)
            if Array(codes[at..<end]) == ScriptDetection.han {
                groups.append(ScriptDetection.hants)
                at = end
            } else {
                groups.append(codes[at])
                at += 1
            }
        }
        return groups
    }

    private func han(_ points: [Int]) -> Script? {
        guard let han = byCode["Hani"] else { return nil }
        let traditional = byCode["Hant"], simplified = byCode["Hans"]
        var marked = 0
        var found = false
        for point in points where han.contains(point) {
            found = true
            let isTraditional = traditional?.isMark(point) ?? false
            let isSimplified = simplified?.isMark(point) ?? false
            if isTraditional != isSimplified { marked += isTraditional ? 1 : -1 }
        }
        if !found { return nil }
        if marked > 0 { return traditional ?? han }
        if marked < 0 { return simplified ?? han }
        return han
    }

    private static func ignored(_ codePoint: Int) -> Bool {
        spaces.holds(codePoint) || punctuation.contains(codePoint)
    }
}

extension Array where Element == ClosedRange<Int> {
    /// Whether one of these ranges, sorted and apart, holds `codePoint`.
    func holds(_ codePoint: Int) -> Bool {
        var low = 0
        var high = count - 1
        while low <= high {
            let mid = (low + high) / 2
            if codePoint < self[mid].lowerBound {
                high = mid - 1
            } else if codePoint > self[mid].upperBound {
                low = mid + 1
            } else {
                return true
            }
        }
        return false
    }
}
