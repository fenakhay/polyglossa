use std::fmt;

use crate::handle::{FamilyId, LanguageId, Ref};
use crate::{Catalog, Error, Language};

#[derive(Debug)]
pub(crate) struct FamilyRecord {
    pub(crate) code: String,
    pub(crate) name: String,
    pub(crate) wikidata: Option<String>,
    pub(crate) aliases: Vec<String>,
    pub(crate) parent: Option<FamilyId>,
    pub(crate) proto_language: Option<LanguageId>,
    pub(crate) languages: Vec<LanguageId>,
}

/// One language family as English Wiktionary's `Module:families` describes it.
#[derive(Clone, Copy, PartialEq, Eq, Hash)]
pub struct LanguageFamily<'cat>(pub(crate) Ref<'cat, FamilyId>);

impl<'cat> LanguageFamily<'cat> {
    pub(crate) fn new(catalog: &'cat Catalog, id: FamilyId) -> Self {
        LanguageFamily(Ref { catalog, id })
    }

    fn record(self) -> &'cat FamilyRecord {
        &self.0.catalog.families[self.0.id.0]
    }

    /// Its code, as `gmw`.
    #[must_use]
    pub fn code(self) -> &'cat str {
        &self.record().code
    }

    /// Its name.
    #[must_use]
    pub fn name(self) -> &'cat str {
        &self.record().name
    }

    /// Its Wikidata item.
    #[must_use]
    pub fn wikidata(self) -> Option<&'cat str> {
        self.record().wikidata.as_deref()
    }

    /// The other names Wiktionary gives it.
    #[must_use]
    pub fn aliases(self) -> &'cat [String] {
        &self.record().aliases
    }

    /// The family it belongs to.
    #[must_use]
    pub fn parent(self) -> Option<LanguageFamily<'cat>> {
        self.record()
            .parent
            .map(|id| LanguageFamily::new(self.0.catalog, id))
    }

    /// The language it descends from: the one Wiktionary names, or else `<code>-pro` when there is one.
    #[must_use]
    pub fn proto_language(self) -> Option<Language<'cat>> {
        self.record()
            .proto_language
            .map(|id| Language::new(self.0.catalog, id))
    }

    /// The languages in it directly, not those of the families under it, sorted by code.
    #[must_use]
    pub fn languages(self) -> impl ExactSizeIterator<Item = Language<'cat>> + DoubleEndedIterator {
        let catalog = self.0.catalog;
        self.record()
            .languages
            .iter()
            .map(move |&id| Language::new(catalog, id))
    }
}

impl LanguageFamily<'static> {
    /// The family with `code`.
    ///
    /// # Errors
    ///
    /// [`Error::UnknownFamily`] when Wiktionary has none.
    pub fn of(code: &str) -> Result<Self, Error> {
        Self::get(code).ok_or_else(|| Error::UnknownFamily(code.to_owned()))
    }

    /// The family with `code`, or none when Wiktionary has none.
    #[must_use]
    pub fn get(code: &str) -> Option<Self> {
        Catalog::bundled().family(code)
    }

    /// Every family, sorted by code.
    #[must_use]
    pub fn all() -> impl ExactSizeIterator<Item = Self> + DoubleEndedIterator {
        Catalog::bundled().families()
    }
}

impl fmt::Debug for LanguageFamily<'_> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "LanguageFamily({})", self.code())
    }
}

impl fmt::Display for LanguageFamily<'_> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{} ({})", self.name(), self.code())
    }
}
