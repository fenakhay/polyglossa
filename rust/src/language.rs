use std::fmt;

use crate::handle::{FamilyId, LanguageId, Link, Ref, ScriptId};
use crate::{Catalog, Error, LanguageFamily, Script};

/// What kind of language Wiktionary takes one to be, which decides where its entries may live.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum LanguageType {
    /// A language with entries of its own.
    Regular,
    /// A reconstructed language, whose entries live in the Reconstruction namespace.
    Reconstructed,
    /// A constructed language whose entries live in the Appendix namespace.
    AppendixConstructed,
    /// A variety named in etymologies only.
    EtymologyOnly,
}

impl LanguageType {
    /// Wiktionary's word for it: `regular`, `reconstructed`, `appendix-constructed` or `etymology-only`.
    #[must_use]
    pub fn as_str(self) -> &'static str {
        match self {
            LanguageType::Regular => "regular",
            LanguageType::Reconstructed => "reconstructed",
            LanguageType::AppendixConstructed => "appendix-constructed",
            LanguageType::EtymologyOnly => "etymology-only",
        }
    }
}

impl fmt::Display for LanguageType {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

/// A variety of a language Wiktionary names without giving it a code, such as a dialect.
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct Variety {
    /// Its name.
    pub name: String,
    /// The other names it goes by.
    pub other_names: Vec<String>,
}

#[derive(Debug)]
pub(crate) struct LanguageRecord {
    pub(crate) code: String,
    pub(crate) name: String,
    pub(crate) language_type: LanguageType,
    pub(crate) wikidata: Option<String>,
    pub(crate) aliases: Vec<String>,
    pub(crate) varieties: Vec<Variety>,
    pub(crate) family: Option<Link<FamilyId>>,
    pub(crate) scripts: Vec<Link<ScriptId>>,
    pub(crate) ancestors: Vec<LanguageId>,
    pub(crate) parent: Option<LanguageId>,
}

/// One language as English Wiktionary's `Module:languages` and `Module:etymology languages` describe it.
#[derive(Clone, Copy, PartialEq, Eq, Hash)]
pub struct Language<'cat>(pub(crate) Ref<'cat, LanguageId>);

impl<'cat> Language<'cat> {
    pub(crate) fn new(catalog: &'cat Catalog, id: LanguageId) -> Self {
        Language(Ref { catalog, id })
    }

    pub(crate) fn record(self) -> &'cat LanguageRecord {
        &self.0.catalog.languages[self.0.id.0]
    }

    /// Its code, as `en` or `en-US`.
    #[must_use]
    pub fn code(self) -> &'cat str {
        &self.record().code
    }

    /// Its name, as its section in an entry is headed.
    #[must_use]
    pub fn name(self) -> &'cat str {
        &self.record().name
    }

    /// What kind of language it is.
    #[must_use]
    pub fn language_type(self) -> LanguageType {
        self.record().language_type
    }

    /// Its Wikidata item, as `Q1860`.
    #[must_use]
    pub fn wikidata(self) -> Option<&'cat str> {
        self.record().wikidata.as_deref()
    }

    /// The other names Wiktionary gives it.
    #[must_use]
    pub fn aliases(self) -> &'cat [String] {
        &self.record().aliases
    }

    /// The varieties Wiktionary names without a code of their own.
    #[must_use]
    pub fn varieties(self) -> &'cat [Variety] {
        &self.record().varieties
    }

    /// Whether it is named in etymologies only, with no section of its own in an entry.
    #[must_use]
    pub fn is_etymology_only(self) -> bool {
        self.record().language_type == LanguageType::EtymologyOnly
    }

    /// The family it belongs to; an etymology-only variety with none of its own has that of the nearest of its
    /// parents that has one, as American English has English's through North American English.
    #[must_use]
    pub fn family(self) -> Option<LanguageFamily<'cat>> {
        let catalog = self.0.catalog;
        self.lineage()
            .find_map(|language| language.record().family.as_ref())
            .and_then(Link::known)
            .map(|id| LanguageFamily::new(catalog, id))
    }

    /// The scripts it is written in, most usual first, those the catalog knows.
    pub fn scripts(self) -> impl Iterator<Item = Script<'cat>> {
        let catalog = self.0.catalog;
        self.record()
            .scripts
            .iter()
            .filter_map(Link::known)
            .map(move |id| Script::new(catalog, id))
    }

    /// The languages it descends from, those the catalog knows.
    #[must_use = "iterators are lazy and do nothing unless consumed"]
    pub fn ancestors(self) -> impl ExactSizeIterator<Item = Language<'cat>> + DoubleEndedIterator {
        let catalog = self.0.catalog;
        self.record()
            .ancestors
            .iter()
            .map(move |&id| Language::new(catalog, id))
    }

    /// The language an etymology-only variety belongs to; none for any other.
    #[must_use]
    pub fn parent(self) -> Option<Language<'cat>> {
        self.record()
            .parent
            .map(|id| Language::new(self.0.catalog, id))
    }

    /// The full language this stands for: itself, or for an etymology-only variety the one it belongs to.
    #[must_use]
    pub fn full(self) -> Language<'cat> {
        self.lineage()
            .find(|language| !language.is_etymology_only() || language.parent().is_none())
            .or_else(|| self.lineage().last())
            .unwrap_or(self)
    }

    /// The script `text` is written in, among this language's [`scripts`](Self::scripts), as Wiktionary's
    /// `findBestScript` finds it, or none when none of them has any of its characters.
    ///
    /// Spaces and ASCII punctuation are left out, and the scripts are tried in order: the first that has every
    /// character wins, or else the one with the most of them, the earlier on a tie. Chinese and the other
    /// languages written in Han give Traditional or Simplified Han when more of the characters mark one than the
    /// other, and Han when as many mark each. A language written in every script finds it as
    /// [`Catalog::detect_script`] does, and an etymology-only variety with no scripts of its own uses its
    /// language's. Pass plain text: wiki markup counts as characters.
    ///
    /// ```
    /// use polyglossa::Language;
    ///
    /// assert_eq!(Language::of("sh")?.detect_script("Москва").map(|s| s.code()), Some("Cyrl"));
    /// assert_eq!(Language::of("zh")?.detect_script("語言").map(|s| s.code()), Some("Hant"));
    /// # Ok::<(), polyglossa::Error>(())
    /// ```
    #[must_use]
    pub fn detect_script(self, text: &str) -> Option<Script<'cat>> {
        self.0.catalog.detection().detect_in(self, text)
    }

    /// Itself, then its parent, its parent's parent and so on, each once.
    pub(crate) fn lineage(self) -> impl Iterator<Item = Language<'cat>> {
        let mut seen = Vec::new();
        std::iter::successors(Some(self), |language| language.parent()).take_while(
            move |language| {
                let first = !seen.contains(&language.0.id);
                seen.push(language.0.id);
                first
            },
        )
    }
}

impl Language<'static> {
    /// The language with `code`.
    ///
    /// # Errors
    ///
    /// [`Error::UnknownLanguage`] when Wiktionary has none.
    pub fn of(code: &str) -> Result<Self, Error> {
        Catalog::bundled().of(code)
    }

    /// The language with `code`, or none when Wiktionary has none.
    #[must_use]
    pub fn get(code: &str) -> Option<Self> {
        Catalog::bundled().get(code)
    }

    /// The language called `name`, as its section in an entry is headed.
    #[must_use]
    pub fn named(name: &str) -> Option<Self> {
        Catalog::bundled().named(name)
    }

    /// Every language and etymology-only variety, sorted by code.
    #[must_use = "iterators are lazy and do nothing unless consumed"]
    pub fn all() -> impl ExactSizeIterator<Item = Self> + DoubleEndedIterator {
        Catalog::bundled().languages()
    }
}

impl fmt::Debug for Language<'_> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "Language({})", self.code())
    }
}

impl fmt::Display for Language<'_> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{} ({})", self.name(), self.code())
    }
}
