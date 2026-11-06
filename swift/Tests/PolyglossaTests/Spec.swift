import Foundation
import Testing

/// `spec/expectations.json`: what every package must answer from the same `catalog.tsv`.
struct Spec: Decodable, Sendable {
    struct Counts: Decodable, Sendable {
        let languages: Int
        let families: Int
        let scripts: Int
    }

    struct LanguageCase: Decodable, Sendable, CustomTestStringConvertible {
        let code: String
        let name: String
        let type: String
        let wikidata: String?
        let family: String?
        let scripts: [String]
        let ancestors: [String]
        let aliases: [String]
        let parent: String?
        let full: String
        let varieties: [[String]]

        var testDescription: String { code }
    }

    struct FamilyCase: Decodable, Sendable, CustomTestStringConvertible {
        let code: String
        let name: String
        let wikidata: String?
        let parent: String?
        let protoLanguage: String?
        let aliases: [String]
        let languages: [String]

        var testDescription: String { code }
    }

    struct ScriptCase: Decodable, Sendable, CustomTestStringConvertible {
        let code: String
        let name: String
        let wikidata: String?
        let system: String?
        let aliases: [String]
        let parent: String?
        let direction: String
        let localNames: [String: String]
        let characters: Int
        let ranges: Int
        let marks: Int

        var testDescription: String { code }
    }

    struct DetectionCase: Decodable, Sendable, CustomTestStringConvertible {
        let language: String?
        let text: String
        let script: String?

        var testDescription: String { "\(language ?? "no language"): \(text)" }
    }

    let date: String
    let counts: Counts
    let languages: [LanguageCase]
    let families: [FamilyCase]
    let scripts: [ScriptCase]
    let named: [String: String?]
    let missing: [String]
    let detections: [DetectionCase]

    /// The spec, read once from the repository; `nil` when it cannot be read, which ``SpecTests`` reports.
    static let loaded: Spec? = {
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent()
        guard let data = try? Data(contentsOf: root.appendingPathComponent("spec/expectations.json")) else { return nil }
        return try? JSONDecoder().decode(Spec.self, from: data)
    }()
}
