use std::collections::HashMap;
use std::sync::OnceLock;

use crate::detection::ScriptDetection;
use crate::family::FamilyRecord;
use crate::handle::{FamilyId, LanguageId, Link, ScriptId};
use crate::language::LanguageRecord;
use crate::read::{self, RawCatalog};
use crate::script::ScriptRecord;
use crate::{Error, Language, LanguageFamily, Script};

const BUNDLED_TSV: &str = include_str!("../catalog.tsv");

/// A set of languages, families and scripts that know one another.
///
/// [`Catalog::bundled`], English Wiktionary's own, is the one [`Language`], [`LanguageFamily`] and [`Script`]
/// look up.
#[derive(Debug)]
pub struct Catalog {
    date: Option<String>,
    pub(crate) languages: Vec<LanguageRecord>,
    pub(crate) families: Vec<FamilyRecord>,
    pub(crate) scripts: Vec<ScriptRecord>,
    language_codes: HashMap<String, LanguageId>,
    family_codes: HashMap<String, FamilyId>,
    pub(crate) script_codes: HashMap<String, ScriptId>,
    language_names: HashMap<String, LanguageId>,
    script_names: HashMap<String, ScriptId>,
    detection: OnceLock<ScriptDetection>,
}

impl Catalog {
    /// English Wiktionary's languages, families and scripts as the crate was built with them.
    ///
    /// # Panics
    ///
    /// Never: the bundled catalog is read by the crate's own tests.
    #[must_use]
    pub fn bundled() -> &'static Catalog {
        static BUNDLED: OnceLock<Catalog> = OnceLock::new();
        BUNDLED.get_or_init(|| Catalog::from_tsv(BUNDLED_TSV).expect("the bundled catalog reads"))
    }

    /// The catalog `text` holds, as the Kotlin library's `Catalog.toTsv` writes it.
    ///
    /// # Errors
    ///
    /// [`Error::NotACatalog`], with the line, when `text` is not such a catalog.
    pub fn from_tsv(text: &str) -> Result<Catalog, Error> {
        read::read(text).map(Catalog::link)
    }

    /// Resolves every code one entry gives for another, once, so that a handle follows a link by its place.
    fn link(raw: RawCatalog) -> Catalog {
        let language_codes: HashMap<String, LanguageId> = places(raw.languages.keys(), LanguageId);
        let family_codes: HashMap<String, FamilyId> = places(raw.families.keys(), FamilyId);
        let script_codes: HashMap<String, ScriptId> = places(raw.scripts.keys(), ScriptId);
        let language = |code: &str| language_codes.get(code).copied();
        let script_link = |code: String| match script_codes.get(&code) {
            Some(&id) => Link::Known(id),
            None => Link::Unknown(code),
        };

        let languages: Vec<LanguageRecord> = raw
            .languages
            .into_iter()
            .map(|(code, raw)| LanguageRecord {
                code,
                name: raw.name,
                language_type: raw.language_type,
                wikidata: raw.wikidata,
                aliases: raw.aliases,
                varieties: raw.varieties,
                family: raw.family.map(|code| match family_codes.get(&code) {
                    Some(&id) => Link::Known(id),
                    None => Link::Unknown(code),
                }),
                scripts: raw.scripts.into_iter().map(script_link).collect(),
                ancestors: raw
                    .ancestors
                    .iter()
                    .filter_map(|code| language(code))
                    .collect(),
                parent: raw.parent.as_deref().and_then(language),
            })
            .collect();

        let mut members: Vec<Vec<LanguageId>> = vec![Vec::new(); raw.families.len()];
        for (at, record) in languages.iter().enumerate() {
            if let Some(Link::Known(family)) = record.family {
                members[family.0].push(LanguageId(at));
            }
        }
        let families = raw
            .families
            .into_iter()
            .zip(members)
            .map(|((code, raw), languages)| FamilyRecord {
                proto_language: match &raw.proto_language {
                    Some(proto) => language(proto),
                    None => language(&format!("{code}-pro")),
                },
                parent: raw
                    .parent
                    .as_deref()
                    .and_then(|parent| family_codes.get(parent).copied()),
                code,
                name: raw.name,
                wikidata: raw.wikidata,
                aliases: raw.aliases,
                languages,
            })
            .collect();

        let scripts: Vec<ScriptRecord> = raw
            .scripts
            .into_iter()
            .map(|(code, raw)| ScriptRecord {
                code,
                name: raw.name,
                wikidata: raw.wikidata,
                system: raw.system,
                aliases: raw.aliases,
                parent: raw.parent.map(script_link),
                direction: raw.direction,
                local_names: raw.local_names,
                ranges: raw.ranges,
                marks: raw.marks,
            })
            .collect();

        let language_names = names_by_preference(
            &languages,
            |record| record.language_type != crate::LanguageType::EtymologyOnly,
            |record| &record.name,
            LanguageId,
        );
        let script_names = names_by_preference(
            &scripts,
            |record| record.parent.is_none(),
            |record| &record.name,
            ScriptId,
        );

        Catalog {
            date: raw.date,
            languages,
            families,
            scripts,
            language_codes,
            family_codes,
            script_codes,
            language_names,
            script_names,
            detection: OnceLock::new(),
        }
    }

    /// The day Wiktionary's data was exported, as `2026-11-06`, when the catalog says.
    #[must_use]
    pub fn date(&self) -> Option<&str> {
        self.date.as_deref()
    }

    /// Every language and etymology-only variety, sorted by code.
    #[must_use]
    pub fn languages(&self) -> impl ExactSizeIterator<Item = Language<'_>> + DoubleEndedIterator {
        (0..self.languages.len()).map(|at| Language::new(self, LanguageId(at)))
    }

    /// Every family, sorted by code.
    #[must_use]
    pub fn families(
        &self,
    ) -> impl ExactSizeIterator<Item = LanguageFamily<'_>> + DoubleEndedIterator {
        (0..self.families.len()).map(|at| LanguageFamily::new(self, FamilyId(at)))
    }

    /// Every script, sorted by code.
    #[must_use]
    pub fn scripts(&self) -> impl ExactSizeIterator<Item = Script<'_>> + DoubleEndedIterator {
        (0..self.scripts.len()).map(|at| Script::new(self, ScriptId(at)))
    }

    /// The language with `code`.
    #[must_use]
    pub fn get(&self, code: &str) -> Option<Language<'_>> {
        let id = *self.language_codes.get(code)?;
        Some(Language::new(self, id))
    }

    /// The language with `code`.
    ///
    /// # Errors
    ///
    /// [`Error::UnknownLanguage`] when the catalog has none.
    pub fn of(&self, code: &str) -> Result<Language<'_>, Error> {
        self.get(code)
            .ok_or_else(|| Error::UnknownLanguage(code.to_owned()))
    }

    /// The language called `name`; a full language wins over an etymology-only variety of the same name.
    #[must_use]
    pub fn named(&self, name: &str) -> Option<Language<'_>> {
        let id = *self.language_names.get(name.trim())?;
        Some(Language::new(self, id))
    }

    /// The family with `code`.
    #[must_use]
    pub fn family(&self, code: &str) -> Option<LanguageFamily<'_>> {
        let id = *self.family_codes.get(code)?;
        Some(LanguageFamily::new(self, id))
    }

    /// The script with `code`.
    #[must_use]
    pub fn script(&self, code: &str) -> Option<Script<'_>> {
        let id = *self.script_codes.get(code)?;
        Some(Script::new(self, id))
    }

    /// The script called `name`; one that is no variety of another wins over one that is.
    #[must_use]
    pub fn script_named(&self, name: &str) -> Option<Script<'_>> {
        let id = *self.script_names.get(name.trim())?;
        Some(Script::new(self, id))
    }

    /// The script `text` is written in, whatever its language, as Wiktionary's `findBestScriptWithoutLang`
    /// finds it: the one most of its characters belong to, or none when more of them belong to no script. A tie
    /// goes to a script that holds no other whole, as Greek does beside Polytonic Greek, then to a script over
    /// notation such as `Zmth`, then to the one with the most characters, as Han is beside Latin, then to one that
    /// is no variety of another. [`Language::detect_script`] knows better when the language is known.
    #[must_use]
    pub fn detect_script(&self, text: &str) -> Option<Script<'_>> {
        self.detection().detect(self, text)
    }

    pub(crate) fn detection(&self) -> &ScriptDetection {
        self.detection.get_or_init(|| ScriptDetection::new(self))
    }
}

/// Each code with its place among `codes`, which are sorted and each once.
fn places<'raw, Id>(
    codes: impl Iterator<Item = &'raw String>,
    id: impl Fn(usize) -> Id,
) -> HashMap<String, Id> {
    codes
        .enumerate()
        .map(|(at, code)| (code.clone(), id(at)))
        .collect()
}

/// Each name of `records` with the place of the record that has it, one that `wins` beating one that does not,
/// and the later beating the earlier otherwise.
fn names_by_preference<Record, Id>(
    records: &[Record],
    wins: impl Fn(&Record) -> bool,
    name: impl Fn(&Record) -> &String,
    id: impl Fn(usize) -> Id,
) -> HashMap<String, Id> {
    let mut names = HashMap::with_capacity(records.len());
    for winners in [false, true] {
        for (at, record) in records.iter().enumerate() {
            if wins(record) == winners {
                names.insert(name(record).clone(), id(at));
            }
        }
    }
    names
}
