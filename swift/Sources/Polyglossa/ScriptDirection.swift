/// Which way a script runs, as Wiktionary's `Module:scripts/data` has it.
public enum ScriptDirection: String, Sendable, CaseIterable {
    /// Left to right, as most scripts run.
    case leftToRight = "ltr"
    /// Right to left, as Arabic and Hebrew run.
    case rightToLeft = "rtl"
    /// Top to bottom, columns left to right, as Mongolian runs.
    case verticalLeftToRight = "vertical-ltr"
    /// Top to bottom, columns right to left.
    case verticalRightToLeft = "vertical-rtl"
}
