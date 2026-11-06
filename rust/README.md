# polyglossa

English Wiktionary's languages, language families and scripts for Rust: every code Wiktionary uses, what
it calls each one, and how they relate, built into the crate so nothing is asked of the wiki.

[![crates.io](https://img.shields.io/crates/v/polyglossa)](https://crates.io/crates/polyglossa)
[![docs.rs](https://img.shields.io/docsrs/polyglossa)](https://docs.rs/polyglossa)
[![CI](https://github.com/fenakhay/polyglossa/actions/workflows/ci.yml/badge.svg)](https://github.com/fenakhay/polyglossa/actions/workflows/ci.yml)
[![Docs](https://img.shields.io/badge/docs-docs.fenakhay.com%2Fpolyglossa-0A74B8.svg)](https://docs.fenakhay.com/polyglossa/)

```rust
use polyglossa::{Language, LanguageFamily, Script, ScriptDirection};

let english = Language::of("en")?;
assert_eq!(english.name(), "English");
assert_eq!(english.family().map(|f| f.name()), Some("Anglic"));
assert!(Language::get("xx").is_none());
assert_eq!(Language::named("Old English").map(|l| l.code()), Some("ang"));
assert_eq!(LanguageFamily::of("ine")?.proto_language().map(|l| l.name()), Some("Proto-Indo-European"));
assert_eq!(Script::named("Arabic").map(|s| s.direction()), Some(ScriptDirection::RightToLeft));
assert_eq!(Language::of("zh")?.detect_script("語言").map(|s| s.code()), Some("Hant"));
```

`Language::of("xx")` returns `Err(Error::UnknownLanguage("xx"))`. The crate needs Rust 1.85 or later and has
no dependencies. The version is the day the data was exported from Wiktionary.

The data is English Wiktionary's, under CC BY-SA 4.0; the code is under the MIT licence. The documentation,
with every example in Rust, is at https://docs.fenakhay.com/polyglossa/.
