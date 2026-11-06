/// What kind of language Wiktionary takes one to be, which decides where its entries may live.
public enum LanguageType: String, Sendable, CaseIterable {
    /// Attested, with entries in the main namespace.
    case regular
    /// Reconstructed, such as Proto-Indo-European, with entries in the Reconstruction namespace.
    case reconstructed
    /// Constructed and kept in the Appendix namespace, such as Klingon.
    case appendixConstructed = "appendix-constructed"
    /// A variety named in etymologies only, such as Limousin, which has no entries of its own.
    case etymologyOnly = "etymology-only"
}
