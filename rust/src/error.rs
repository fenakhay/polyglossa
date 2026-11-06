use std::fmt;

/// What went wrong looking something up or reading a catalog.
#[derive(Debug, Clone, PartialEq, Eq)]
#[non_exhaustive]
pub enum Error {
    /// Wiktionary has no language with this code.
    UnknownLanguage(String),
    /// Wiktionary has no family with this code.
    UnknownFamily(String),
    /// Wiktionary has no script with this code.
    UnknownScript(String),
    /// The text is not a catalog polyglossa wrote.
    NotACatalog {
        /// The line, counted from 1, that could not be read.
        line: usize,
        /// What is wrong with it.
        reason: String,
    },
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Error::UnknownLanguage(code) => write!(f, "no language has the code {code}"),
            Error::UnknownFamily(code) => write!(f, "no family has the code {code}"),
            Error::UnknownScript(code) => write!(f, "no script has the code {code}"),
            Error::NotACatalog { line, reason } => {
                write!(f, "not a catalog polyglossa wrote: line {line}: {reason}")
            }
        }
    }
}

impl std::error::Error for Error {}
