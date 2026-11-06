import Testing
@testable import Polyglossa

/// The bundled catalog answers as `spec/expectations.json` says, as the Kotlin library and every other package do.
struct SpecTests {
    @Test func specIsRead() throws {
        let spec = try #require(Spec.loaded, "spec/expectations.json should be read and decoded")
        #expect(spec.languages.isEmpty == false)
        #expect(spec.detections.isEmpty == false)
    }

    @Test func countsAndDate() throws {
        let spec = try #require(Spec.loaded)
        let catalog = Catalog.bundled
        #expect(catalog.date == spec.date)
        #expect(catalog.languages.count == spec.counts.languages)
        #expect(catalog.families.count == spec.counts.families)
        #expect(catalog.scripts.count == spec.counts.scripts)
    }

    @Test(arguments: Spec.loaded?.missing ?? [])
    func missingLanguage(code: String) {
        #expect(Language[code] == nil)
    }

    @Test(arguments: Spec.loaded?.named.map { ($0.key, $0.value) } ?? [])
    func namedLanguage(name: String, code: String?) {
        #expect(Language.named(name)?.code == code)
    }

    @Test(arguments: Spec.loaded?.languages ?? [])
    func language(expected: Spec.LanguageCase) throws {
        let language = try Language.of(expected.code)
        #expect(language.name == expected.name)
        #expect(language.type.rawValue == expected.type)
        #expect(language.wikidata == expected.wikidata)
        #expect(language.family?.code == expected.family)
        #expect(language.scripts.map(\.code) == expected.scripts)
        #expect(language.ancestors.map(\.code) == expected.ancestors)
        #expect(language.aliases == expected.aliases)
        #expect(language.parent?.code == expected.parent)
        #expect(language.full.code == expected.full)
        #expect(language.varieties.map { [$0.name] + $0.otherNames } == expected.varieties)
    }

    @Test(arguments: Spec.loaded?.families ?? [])
    func family(expected: Spec.FamilyCase) throws {
        let family = try LanguageFamily.of(expected.code)
        #expect(family.name == expected.name)
        #expect(family.wikidata == expected.wikidata)
        #expect(family.parent?.code == expected.parent)
        #expect(family.protoLanguage?.code == expected.protoLanguage)
        #expect(family.aliases == expected.aliases)
        #expect(family.languages.map(\.code) == expected.languages)
    }

    @Test(arguments: Spec.loaded?.scripts ?? [])
    func script(expected: Spec.ScriptCase) throws {
        let script = try Script.of(expected.code)
        #expect(script.name == expected.name)
        #expect(script.wikidata == expected.wikidata)
        #expect(script.system == expected.system)
        #expect(script.aliases == expected.aliases)
        #expect(script.parent?.code == expected.parent)
        #expect(script.direction.rawValue == expected.direction)
        #expect(Dictionary(uniqueKeysWithValues: script.localNames.map { ($0.key.code, $0.value) }) == expected.localNames)
        #expect(script.ranges.reduce(0) { $0 + $1.count } == expected.characters)
        #expect(script.ranges.count == expected.ranges)
        #expect(script.marks.reduce(0) { $0 + $1.count } == expected.marks)
    }

    @Test(arguments: Spec.loaded?.detections ?? [])
    func detection(expected: Spec.DetectionCase) throws {
        let script = try expected.language.map { try Language.of($0).detectScript(expected.text) }
            ?? Script.detect(expected.text)
        #expect(script?.code == expected.script)
    }
}
