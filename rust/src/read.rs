use std::collections::BTreeMap;
use std::ops::RangeInclusive;

use crate::{Error, LanguageType, ScriptDirection, Variety};

const HEADER: &str = "polyglossa\t1";

/// A catalog as the text has it: each entry under its code, sorted, the later of two with the same code
/// winning, and the codes that link entries not yet resolved.
pub(crate) struct RawCatalog {
    pub(crate) date: Option<String>,
    pub(crate) languages: BTreeMap<String, RawLanguage>,
    pub(crate) families: BTreeMap<String, RawFamily>,
    pub(crate) scripts: BTreeMap<String, RawScript>,
}

pub(crate) struct RawLanguage {
    pub(crate) name: String,
    pub(crate) language_type: LanguageType,
    pub(crate) wikidata: Option<String>,
    pub(crate) family: Option<String>,
    pub(crate) scripts: Vec<String>,
    pub(crate) ancestors: Vec<String>,
    pub(crate) aliases: Vec<String>,
    pub(crate) parent: Option<String>,
    pub(crate) varieties: Vec<Variety>,
}

pub(crate) struct RawFamily {
    pub(crate) name: String,
    pub(crate) wikidata: Option<String>,
    pub(crate) parent: Option<String>,
    pub(crate) proto_language: Option<String>,
    pub(crate) aliases: Vec<String>,
}

pub(crate) struct RawScript {
    pub(crate) name: String,
    pub(crate) wikidata: Option<String>,
    pub(crate) system: Option<String>,
    pub(crate) aliases: Vec<String>,
    pub(crate) parent: Option<String>,
    pub(crate) direction: ScriptDirection,
    pub(crate) local_names: Vec<(String, String)>,
    pub(crate) ranges: Vec<RangeInclusive<u32>>,
    pub(crate) marks: Vec<RangeInclusive<u32>>,
}

/// Reads `text`, a catalog as the Kotlin library's `Catalog.toTsv` writes it.
pub(crate) fn read(text: &str) -> Result<RawCatalog, Error> {
    let mut lines = text.split('\n').zip(1..);
    let header = lines.next().map_or("", |(line, _)| line);
    let date = match header.strip_prefix(HEADER) {
        Some("") => None,
        Some(rest) => match rest.strip_prefix('\t') {
            Some(date) => Some(date.to_owned()).filter(|it| !it.is_empty()),
            None => return Err(not_a_catalog(1, "no header")),
        },
        None => return Err(not_a_catalog(1, "no header")),
    };
    let mut catalog = RawCatalog {
        date,
        languages: BTreeMap::new(),
        families: BTreeMap::new(),
        scripts: BTreeMap::new(),
    };
    for (line, number) in lines.filter(|(line, _)| !line.is_empty()) {
        let mut cells = Cells(line.split('\t'));
        let kind = cells.next();
        let code = cells.next().to_owned();
        match kind {
            "L" => {
                let language =
                    language(&mut cells).map_err(|reason| not_a_catalog(number, reason))?;
                catalog.languages.insert(code, language);
            }
            "F" => {
                catalog.families.insert(code, family(&mut cells));
            }
            "S" => {
                let script = script(&mut cells).map_err(|reason| not_a_catalog(number, reason))?;
                catalog.scripts.insert(code, script);
            }
            _ => {
                return Err(not_a_catalog(
                    number,
                    "not a language, a family or a script",
                ));
            }
        }
    }
    Ok(catalog)
}

fn not_a_catalog(line: usize, reason: impl Into<String>) -> Error {
    Error::NotACatalog {
        line,
        reason: reason.into(),
    }
}

/// The cells of one line, read in order; one left off the end reads as empty.
struct Cells<'line>(std::str::Split<'line, char>);

impl<'line> Cells<'line> {
    fn next(&mut self) -> &'line str {
        self.0.next().unwrap_or_default()
    }

    fn optional(&mut self) -> Option<String> {
        Some(self.next())
            .filter(|it| !it.is_empty())
            .map(str::to_owned)
    }
}

fn language(cells: &mut Cells<'_>) -> Result<RawLanguage, String> {
    let name = unescape(cells.next());
    let language_type = match cells.next() {
        "" => LanguageType::Regular,
        "r" => LanguageType::Reconstructed,
        "a" => LanguageType::AppendixConstructed,
        "e" => LanguageType::EtymologyOnly,
        other => return Err(format!("no language type {other}")),
    };
    Ok(RawLanguage {
        name,
        language_type,
        wikidata: item(cells.next()),
        family: cells.optional(),
        scripts: codes(cells.next()),
        ancestors: codes(cells.next()),
        aliases: names(cells.next()),
        parent: cells.optional(),
        varieties: split(cells.next(), ';').map(variety).collect(),
    })
}

fn variety(written: &str) -> Variety {
    let mut names = split(written, '|').map(unescape);
    let name = names.next().unwrap_or_default();
    Variety {
        name,
        other_names: names.collect(),
    }
}

fn family(cells: &mut Cells<'_>) -> RawFamily {
    RawFamily {
        name: unescape(cells.next()),
        wikidata: item(cells.next()),
        parent: cells.optional(),
        proto_language: cells.optional(),
        aliases: names(cells.next()),
    }
}

fn script(cells: &mut Cells<'_>) -> Result<RawScript, String> {
    Ok(RawScript {
        name: unescape(cells.next()),
        wikidata: item(cells.next()),
        system: Some(unescape(cells.next())).filter(|it| !it.is_empty()),
        aliases: names(cells.next()),
        parent: cells.optional(),
        direction: match cells.next() {
            "rtl" => ScriptDirection::RightToLeft,
            "vertical-ltr" => ScriptDirection::VerticalLeftToRight,
            "vertical-rtl" => ScriptDirection::VerticalRightToLeft,
            _ => ScriptDirection::LeftToRight,
        },
        local_names: names(cells.next())
            .into_iter()
            .map(|entry| match entry.split_once('=') {
                Some((language, local)) => (language.to_owned(), local.to_owned()),
                None => (entry, String::new()),
            })
            .collect(),
        ranges: ranges(cells.next())?,
        marks: ranges(cells.next())?,
    })
}

/// The ranges of hexadecimal code points in `cell`, as `41-5A,AA`.
fn ranges(cell: &str) -> Result<Vec<RangeInclusive<u32>>, String> {
    let hex = |written: &str| {
        u32::from_str_radix(written, 16).map_err(|_| format!("no code point {written}"))
    };
    codes(cell)
        .iter()
        .map(|written| {
            let (first, last) = written
                .split_once('-')
                .unwrap_or((written.as_str(), written.as_str()));
            Ok(hex(first)?..=hex(last)?)
        })
        .collect()
}

fn item(cell: &str) -> Option<String> {
    Some(cell)
        .filter(|it| !it.is_empty())
        .map(|it| format!("Q{it}"))
}

fn codes(cell: &str) -> Vec<String> {
    if cell.is_empty() {
        return Vec::new();
    }
    cell.split(',').map(str::to_owned).collect()
}

fn names(cell: &str) -> Vec<String> {
    split(cell, ';').map(unescape).collect()
}

/// `cell` split at each `separator` that is not escaped, the pieces left escaped; none for an empty cell.
fn split(cell: &str, separator: char) -> impl Iterator<Item = &str> {
    let mut rest = Some(cell).filter(|it| !it.is_empty());
    std::iter::from_fn(move || {
        let text = rest?;
        let mut escaped = false;
        let end = text.char_indices().find_map(|(at, character)| {
            let found = !escaped && character == separator;
            escaped = !escaped && character == '\\';
            found.then_some(at)
        });
        if let Some(at) = end {
            rest = Some(&text[at + separator.len_utf8()..]);
            Some(&text[..at])
        } else {
            rest = None;
            Some(text)
        }
    })
}

/// `text` with the Kotlin library's escapes undone: `\t` and `\n`, and any other character as itself.
fn unescape(text: &str) -> String {
    if !text.contains('\\') {
        return text.to_owned();
    }
    let mut out = String::with_capacity(text.len());
    let mut characters = text.chars();
    while let Some(character) = characters.next() {
        if character != '\\' {
            out.push(character);
            continue;
        }
        out.push(match characters.next() {
            Some('t') => '\t',
            Some('n') => '\n',
            Some(other) => other,
            None => '\\',
        });
    }
    out
}
