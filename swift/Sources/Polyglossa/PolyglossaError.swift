/// Why a lookup that must succeed failed, or why a catalog could not be read.
public enum PolyglossaError: Error, Equatable, Sendable, CustomStringConvertible {
    /// Wiktionary has no language with this code.
    case unknownLanguage(String)
    /// Wiktionary has no family with this code.
    case unknownFamily(String)
    /// Wiktionary has no script with this code.
    case unknownScript(String)
    /// The text is not a catalog polyglossa wrote; the reason starts with the line, as `line 3: no code points`.
    case notACatalog(String)

    /// What went wrong, as `polyglossa: unknown language "xx"`.
    public var description: String {
        switch self {
        case .unknownLanguage(let code): "polyglossa: unknown language \"\(code)\""
        case .unknownFamily(let code): "polyglossa: unknown family \"\(code)\""
        case .unknownScript(let code): "polyglossa: unknown script \"\(code)\""
        case .notACatalog(let reason): "polyglossa: not a catalog polyglossa wrote: \(reason)"
        }
    }
}
