import Testing
@testable import Polyglossa

/// Lookups, errors and equality read as Swift.
struct CatalogTests {
    private static let small = "polyglossa\t1\nL\txx\tExample\t\t\t\tLatn\nS\tLatn\tLatin\t\t\t\t\t\t\t41-5A\n"

    @Test func lookupsThatMustSucceedThrow() {
        #expect(throws: PolyglossaError.unknownLanguage("xx-none")) { try Language.of("xx-none") }
        #expect(throws: PolyglossaError.unknownFamily("xx-none")) { try LanguageFamily.of("xx-none") }
        #expect(throws: PolyglossaError.unknownScript("Xxxx-none")) { try Script.of("Xxxx-none") }
        #expect(PolyglossaError.unknownLanguage("xx").description == "polyglossa: unknown language \"xx\"")
    }

    @Test func missesAndMembership() {
        #expect(Language["xx-none"] == nil)
        #expect(Language.contains("ang"))
        #expect(Language.contains("xx-none") == false)
        #expect(LanguageFamily.contains("ine"))
        #expect(Script.contains("Latn"))
    }

    @Test func links() throws {
        #expect(try Language.of("en-ear").full == Language.of("en"))
        #expect(try Script.of("Deva").name(for: Language.of("mr")) == "Balbodh")
        #expect(Script.named("Arabic")?.direction == .rightToLeft)
        #expect(try Language.of("ine-pro").type == .reconstructed)
        #expect(try Language.of("en").description == "English (en)")
    }

    @Test func codePoints() throws {
        let cyrillic = try Script.of("Cyrl")
        #expect(cyrillic.contains(0x416))
        #expect(cyrillic.contains("Ж" as Unicode.Scalar))
        #expect(cyrillic.contains(0x41) == false)
        #expect(try Script.of("Arab").count(in: "كتاب abc") == 4)
    }

    @Test func entriesAreEqualWhenTheySayTheSameWhateverTheirCatalog() throws {
        let first = try Catalog(tsv: Self.small)
        let second = try Catalog(tsv: Self.small)
        let language = try #require(first["xx"])
        #expect(language == second["xx"])
        #expect(Set([language, try #require(second["xx"])]).count == 1)
        #expect(first.script("Latn") == second.script("Latn"))
    }

    @Test func aCatalogOfOnesOwn() throws {
        let catalog = try Catalog(tsv: Self.small)
        #expect(catalog.date == nil)
        let language = try #require(catalog["xx"])
        #expect(language.detectScript("ABC")?.code == "Latn")
        #expect(catalog.detectScript("ABC")?.code == "Latn")
        #expect(Language["xx"] == nil)
    }

    @Test(arguments: [
        ("{}", 1),
        ("polyglossa\t1\nX\tnothing\n", 2),
        ("polyglossa\t1\nL\txx\tExample\tz\n", 2),
        ("polyglossa\t1\n\nS\tLatn\tLatin\t\t\t\t\t\t\t41-ZZ\n", 3),
    ])
    func aCatalogThatCannotBeReadSaysWhichLine(text: String, line: Int) {
        do {
            _ = try Catalog(tsv: text)
            Issue.record("\(text.debugDescription) should not be read")
        } catch let error as PolyglossaError {
            guard case .notACatalog(let reason) = error else {
                Issue.record("expected notACatalog, got \(error)")
                return
            }
            #expect(reason.hasPrefix("line \(line): "))
        } catch {
            Issue.record("expected a PolyglossaError, got \(error)")
        }
    }

    @Test func catalogsCanBeSharedBetweenTasks() async throws {
        let catalog = Catalog.bundled
        let codes = await withTaskGroup(of: String?.self) { group in
            for text in ["Москва", "語言", "Ελληνικά"] {
                group.addTask { catalog.detectScript(text)?.code }
            }
            var codes: [String?] = []
            for await code in group { codes.append(code) }
            return codes
        }
        #expect(Set(codes.compactMap { $0 }) == ["Cyrl", "Hani", "Grek"])
    }
}
