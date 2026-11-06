//! English Wiktionary's languages, language families and scripts: every code Wiktionary uses, what it calls
//! each one, and how they relate, built into the crate so nothing is asked of the wiki.
//!
//! ```
//! use polyglossa::Language;
//!
//! let english = Language::of("en")?;
//! assert_eq!(english.name(), "English");
//! assert_eq!(english.family().map(|f| f.name()), Some("Anglic"));
//! assert!(Language::get("xx").is_none());
//! # Ok::<(), polyglossa::Error>(())
//! ```
//!
//! Every lookup gives a handle, [`Language`], [`LanguageFamily`] or [`Script`], that borrows the [`Catalog`]
//! it comes from: `'static` for the bundled one. Handles are `Copy`, and two are equal when they are the same
//! entry of the same catalog.

mod catalog;
mod detection;
mod error;
mod family;
mod handle;
mod language;
mod read;
mod script;

pub use catalog::Catalog;
pub use error::Error;
pub use family::LanguageFamily;
pub use language::{Language, LanguageType, Variety};
pub use script::{Script, ScriptDirection};
