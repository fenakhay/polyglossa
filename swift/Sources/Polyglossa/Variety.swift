/// A variety of a language Wiktionary names without giving it a code, such as a dialect.
public struct Variety: Hashable, Sendable, CustomStringConvertible {
    /// Its name, such as `Bavarian German`.
    public let name: String
    /// Other names it goes by.
    public let otherNames: [String]

    /// Its name, then its other names in brackets, as `Bavarian German (Austro-Bavarian)`.
    public var description: String { otherNames.isEmpty ? name : "\(name) (\(otherNames.joined(separator: ", ")))" }
}
