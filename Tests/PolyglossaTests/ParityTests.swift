import Foundation
import XCTest
@testable import Polyglossa

/// The bundled catalog answers as `spec/expectations.json` says, as the Kotlin library and the Python package do.
final class ParityTests: XCTestCase {
    private func spec() throws -> [String: Any] {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent()
        let data = try Data(contentsOf: root.appendingPathComponent("spec/expectations.json"))
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    private func text(_ value: Any?) -> String? { value as? String }

    private func texts(_ value: Any?) -> [String] { (value as? [Any])?.compactMap { $0 as? String } ?? [] }

    func testCountsDateMissesAndNames() throws {
        let spec = try spec()
        let catalog = Catalog.bundled
        let counts = try XCTUnwrap(spec["counts"] as? [String: Int])
        XCTAssertEqual(catalog.date, text(spec["date"]))
        XCTAssertEqual(catalog.languages.count, counts["languages"])
        XCTAssertEqual(catalog.families.count, counts["families"])
        XCTAssertEqual(catalog.scripts.count, counts["scripts"])
        for code in texts(spec["missing"]) { XCTAssertNil(catalog[code], code) }
        for (name, code) in try XCTUnwrap(spec["named"] as? [String: Any]) {
            XCTAssertEqual(catalog.named(name)?.code, text(code), name)
        }
    }

    func testSampledLanguages() throws {
        for expected in try XCTUnwrap(spec()["languages"] as? [[String: Any]]) {
            let language = try Language.of(try XCTUnwrap(text(expected["code"])))
            XCTAssertEqual(language.name, text(expected["name"]))
            XCTAssertEqual(language.type.rawValue, text(expected["type"]))
            XCTAssertEqual(language.wikidata, text(expected["wikidata"]))
            XCTAssertEqual(language.family?.code, text(expected["family"]))
            XCTAssertEqual(language.scripts.map(\.code), texts(expected["scripts"]))
            XCTAssertEqual(language.ancestors.map(\.code), texts(expected["ancestors"]))
            XCTAssertEqual(language.aliases, texts(expected["aliases"]))
            XCTAssertEqual(language.parent?.code, text(expected["parent"]))
            XCTAssertEqual(language.full.code, text(expected["full"]))
            XCTAssertEqual(language.varieties.map { [$0.name] + $0.otherNames },
                           (expected["varieties"] as? [Any] ?? []).map(texts), language.code)
        }
    }

    func testSampledFamiliesAndScripts() throws {
        let spec = try spec()
        for expected in try XCTUnwrap(spec["families"] as? [[String: Any]]) {
            let family = try LanguageFamily.of(try XCTUnwrap(text(expected["code"])))
            XCTAssertEqual(family.name, text(expected["name"]))
            XCTAssertEqual(family.wikidata, text(expected["wikidata"]))
            XCTAssertEqual(family.parent?.code, text(expected["parent"]))
            XCTAssertEqual(family.protoLanguage?.code, text(expected["protoLanguage"]))
            XCTAssertEqual(family.aliases, texts(expected["aliases"]))
            XCTAssertEqual(family.languages.map(\.code), texts(expected["languages"]))
        }
        for expected in try XCTUnwrap(spec["scripts"] as? [[String: Any]]) {
            let script = try Script.of(try XCTUnwrap(text(expected["code"])))
            XCTAssertEqual(script.name, text(expected["name"]))
            XCTAssertEqual(script.wikidata, text(expected["wikidata"]))
            XCTAssertEqual(script.system, text(expected["system"]))
            XCTAssertEqual(script.aliases, texts(expected["aliases"]))
            XCTAssertEqual(script.parent?.code, text(expected["parent"]))
            XCTAssertEqual(script.direction.rawValue, text(expected["direction"]))
            let local = Dictionary(uniqueKeysWithValues: script.localNames.map { ($0.key.code, $0.value) })
            XCTAssertEqual(local, (expected["localNames"] as? [String: String]) ?? [:], script.code)
            XCTAssertEqual(script.ranges.reduce(0) { $0 + $1.count }, expected["characters"] as? Int, script.code)
            XCTAssertEqual(script.ranges.count, expected["ranges"] as? Int, script.code)
            XCTAssertEqual(script.marks.reduce(0) { $0 + $1.count }, expected["marks"] as? Int, script.code)
        }
    }

    func testDetections() throws {
        for expected in try XCTUnwrap(try spec()["detections"] as? [[String: Any]]) {
            let text = try XCTUnwrap(self.text(expected["text"]))
            let script: Script?
            if let code = self.text(expected["language"]) {
                script = try Language.of(code).detectScript(text)
            } else {
                script = Script.detect(text)
            }
            XCTAssertEqual(script?.code, self.text(expected["script"]), text)
        }
        XCTAssertTrue(try Script.of("Cyrl").contains(0x416))
        XCTAssertEqual(try Script.of("Arab").count("كتاب abc"), 4)
    }

    func testLookupsReadAsSwift() throws {
        XCTAssertThrowsError(try Language.of("xx-none")) { error in
            XCTAssertEqual(error as? PolyglossaError, .unknownLanguage("xx-none"))
        }
        XCTAssertTrue(Language.contains("ang"))
        XCTAssertEqual(try Language.of("en-ear").full, try Language.of("en"))
        XCTAssertEqual(try Script.of("Deva").name(for: try Language.of("mr")), "Balbodh")
        XCTAssertEqual(Script.named("Arabic")?.direction, .rightToLeft)
        XCTAssertEqual(try Language.of("en").description, "English (en)")
    }
}
