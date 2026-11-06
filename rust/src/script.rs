use std::fmt;
use std::ops::RangeInclusive;

use crate::handle::{Link, Ref, ScriptId, holds};
use crate::{Catalog, Error, Language};

/// Which way a script runs.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum ScriptDirection {
    /// Left to right.
    LeftToRight,
    /// Right to left.
    RightToLeft,
    /// Top to bottom, the lines left to right.
    VerticalLeftToRight,
    /// Top to bottom, the lines right to left.
    VerticalRightToLeft,
}

impl ScriptDirection {
    /// Wiktionary's word for it: `ltr`, `rtl`, `vertical-ltr` or `vertical-rtl`.
    #[must_use]
    pub fn as_str(self) -> &'static str {
        match self {
            ScriptDirection::LeftToRight => "ltr",
            ScriptDirection::RightToLeft => "rtl",
            ScriptDirection::VerticalLeftToRight => "vertical-ltr",
            ScriptDirection::VerticalRightToLeft => "vertical-rtl",
        }
    }
}

impl fmt::Display for ScriptDirection {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

#[derive(Debug)]
pub(crate) struct ScriptRecord {
    pub(crate) code: String,
    pub(crate) name: String,
    pub(crate) wikidata: Option<String>,
    pub(crate) system: Option<String>,
    pub(crate) aliases: Vec<String>,
    pub(crate) parent: Option<Link<ScriptId>>,
    pub(crate) direction: ScriptDirection,
    pub(crate) local_names: Vec<(String, String)>,
    pub(crate) ranges: Vec<RangeInclusive<u32>>,
    pub(crate) marks: Vec<RangeInclusive<u32>>,
}

/// One script as English Wiktionary's `Module:scripts/data` describes it.
#[derive(Clone, Copy, PartialEq, Eq, Hash)]
pub struct Script<'cat>(pub(crate) Ref<'cat, ScriptId>);

impl<'cat> Script<'cat> {
    pub(crate) fn new(catalog: &'cat Catalog, id: ScriptId) -> Self {
        Script(Ref { catalog, id })
    }

    pub(crate) fn record(self) -> &'cat ScriptRecord {
        &self.0.catalog.scripts[self.0.id.0]
    }

    /// Its code, as `Latn` or `fa-Arab`.
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

    /// What kind of writing system it is, as `alphabet`.
    #[must_use]
    pub fn system(self) -> Option<&'cat str> {
        self.record().system.as_deref()
    }

    /// The other names Wiktionary gives it.
    #[must_use]
    pub fn aliases(self) -> &'cat [String] {
        &self.record().aliases
    }

    /// Which way it runs.
    #[must_use]
    pub fn direction(self) -> ScriptDirection {
        self.record().direction
    }

    /// Its characters, as sorted ranges of code points apart from one another.
    #[must_use]
    pub fn ranges(self) -> &'cat [RangeInclusive<u32>] {
        &self.record().ranges
    }

    /// The characters that mark a text as written in it rather than in a script it shares characters with:
    /// those of Traditional Han that Simplified Han writes otherwise, and the other way round.
    #[must_use]
    pub fn marks(self) -> &'cat [RangeInclusive<u32>] {
        &self.record().marks
    }

    /// The script this is a variety of, such as Han for Simplified Han.
    #[must_use]
    pub fn parent(self) -> Option<Script<'cat>> {
        let id = self.record().parent.as_ref()?.known()?;
        Some(Script::new(self.0.catalog, id))
    }

    /// The name `language` gives this script, which is [`name`](Self::name) unless it has its own.
    #[must_use]
    pub fn name_for(self, language: Language<'_>) -> &'cat str {
        let record = self.record();
        record
            .local_names
            .iter()
            .find(|(code, _)| code == language.code())
            .map_or(&record.name, |(_, local)| local)
    }

    /// The names some languages give it instead of [`name`](Self::name), such as Balbodh for Marathi's
    /// Devanagari.
    pub fn local_names(self) -> impl Iterator<Item = (Language<'cat>, &'cat str)> {
        let catalog = self.0.catalog;
        self.record()
            .local_names
            .iter()
            .filter_map(move |(code, local)| Some((catalog.get(code)?, local.as_str())))
    }

    /// Whether `character` is one of its characters, as `Script::of("Cyrl")?.contains('Ж')`.
    #[must_use]
    pub fn contains(self, character: char) -> bool {
        holds(&self.record().ranges, u32::from(character))
    }

    /// How many of the characters of `text` are its own, counting each code point once.
    #[must_use]
    pub fn count(self, text: &str) -> usize {
        text.chars()
            .filter(|&character| self.contains(character))
            .count()
    }

    pub(crate) fn is_mark(self, character: char) -> bool {
        holds(&self.record().marks, u32::from(character))
    }
}

impl Script<'static> {
    /// The script `text` is written in, whatever its language; see [`Catalog::detect_script`].
    ///
    /// ```
    /// use polyglossa::Script;
    ///
    /// assert_eq!(Script::detect("Ελληνικά").map(|s| s.code()), Some("Grek"));
    /// ```
    #[must_use]
    pub fn detect(text: &str) -> Option<Self> {
        Catalog::bundled().detect_script(text)
    }

    /// The script with `code`.
    ///
    /// # Errors
    ///
    /// [`Error::UnknownScript`] when Wiktionary has none.
    pub fn of(code: &str) -> Result<Self, Error> {
        Self::get(code).ok_or_else(|| Error::UnknownScript(code.to_owned()))
    }

    /// The script with `code`, or none when Wiktionary has none.
    #[must_use]
    pub fn get(code: &str) -> Option<Self> {
        Catalog::bundled().script(code)
    }

    /// The script called `name`; the one without a parent when several are.
    #[must_use]
    pub fn named(name: &str) -> Option<Self> {
        Catalog::bundled().script_named(name)
    }

    /// Every script, sorted by code.
    #[must_use = "iterators are lazy and do nothing unless consumed"]
    pub fn all() -> impl ExactSizeIterator<Item = Self> + DoubleEndedIterator {
        Catalog::bundled().scripts()
    }
}

impl fmt::Debug for Script<'_> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "Script({})", self.code())
    }
}

impl fmt::Display for Script<'_> {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{} ({})", self.name(), self.code())
    }
}
