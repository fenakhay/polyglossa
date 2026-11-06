use std::cmp::{Ordering, Reverse};

use crate::handle::{Link, ScriptId};
use crate::{Catalog, Language, Script};

const HAN: [&str; 3] = ["Hani", "Hant", "Hans"];
const COMBINED: [(&str, &[&str]); 2] = [
    ("Jpan", &["Hani", "Hira", "Kana"]),
    ("Kore", &["Hani", "Hang"]),
];

/// A script made of others, as Japanese is of Han, Hiragana and Katakana, which a text is written in when its
/// characters are of more than one of them.
#[derive(Debug)]
struct Combination {
    script: Option<ScriptId>,
    parts: Vec<Option<ScriptId>>,
}

/// How Wiktionary tells which script a text is in, as the Kotlin library does: `Language:findBestScript` in
/// `Module:languages` for a language, and `findBestScriptWithoutLang` for none.
///
/// Every table is indexed by [`ScriptId`]. The code points are cut into segments at every start and end of a
/// script's range, so the scripts that hold a code point are those of its segment.
#[derive(Debug)]
pub(crate) struct ScriptDetection {
    sizes: Vec<u64>,
    supersets: Vec<bool>,
    segments: Vec<u32>,
    holders: Vec<ScriptId>,
    offsets: Vec<usize>,
    combinations: Vec<Combination>,
    han: Option<HanScripts>,
}

#[derive(Debug, Clone, Copy)]
struct HanScripts {
    plain: ScriptId,
    traditional: Option<ScriptId>,
    simplified: Option<ScriptId>,
}

impl ScriptDetection {
    pub(crate) fn new(catalog: &Catalog) -> Self {
        let id = |code: &str| catalog.script_codes.get(code).copied();
        let combinations: Vec<Combination> = COMBINED
            .iter()
            .map(|(code, parts)| Combination {
                script: id(code),
                parts: parts.iter().map(|part| id(part)).collect(),
            })
            .collect();
        let indexed: Vec<ScriptId> = catalog
            .scripts()
            .filter(|script| {
                !script.ranges().is_empty()
                    && !COMBINED.iter().any(|(code, _)| *code == script.code())
            })
            .map(|script| script.0.id)
            .collect();

        let mut sizes = vec![0; catalog.scripts.len()];
        for &script in &indexed {
            sizes[script.0] = catalog.scripts[script.0]
                .ranges
                .iter()
                .map(|range| u64::from(range.end() - range.start()) + 1)
                .sum();
        }
        for combination in &combinations {
            if let Some(script) = combination.script {
                sizes[script.0] = combination
                    .parts
                    .iter()
                    .flatten()
                    .map(|part| sizes[part.0])
                    .sum();
            }
        }

        let mut supersets = vec![false; catalog.scripts.len()];
        for &outer in &indexed {
            let outer_ranges = &catalog.scripts[outer.0].ranges;
            supersets[outer.0] = indexed.iter().any(|&inner| {
                let inner_ranges = &catalog.scripts[inner.0].ranges;
                inner_ranges != outer_ranges
                    && inner_ranges.iter().all(|x| {
                        outer_ranges
                            .iter()
                            .any(|range| range.start() <= x.start() && x.end() <= range.end())
                    })
            });
        }

        let mut segments: Vec<u32> = indexed
            .iter()
            .flat_map(|script| catalog.scripts[script.0].ranges.iter())
            .flat_map(|range| [*range.start(), range.end() + 1])
            .collect();
        segments.sort_unstable();
        segments.dedup();
        let mut held: Vec<Vec<ScriptId>> = vec![Vec::new(); segments.len()];
        for &script in &indexed {
            for range in &catalog.scripts[script.0].ranges {
                let first = segments.partition_point(|&start| start < *range.start());
                let end = segments.partition_point(|&start| start <= *range.end());
                for segment in &mut held[first..end] {
                    segment.push(script);
                }
            }
        }
        let mut offsets = Vec::with_capacity(held.len() + 1);
        offsets.push(0);
        let mut holders = Vec::with_capacity(held.iter().map(Vec::len).sum());
        for segment in held {
            holders.extend(segment);
            offsets.push(holders.len());
        }

        let han = id(HAN[0]).map(|plain| HanScripts {
            plain,
            traditional: id(HAN[1]),
            simplified: id(HAN[2]),
        });
        ScriptDetection {
            sizes,
            supersets,
            segments,
            holders,
            offsets,
            combinations,
            han,
        }
    }

    /// The scripts that hold `character`.
    fn holding(&self, character: char) -> &[ScriptId] {
        match self
            .segments
            .partition_point(|&start| start <= u32::from(character))
        {
            0 => &[],
            at => &self.holders[self.offsets[at - 1]..self.offsets[at]],
        }
    }

    pub(crate) fn detect<'cat>(&self, catalog: &'cat Catalog, text: &str) -> Option<Script<'cat>> {
        let mut counts = vec![0usize; catalog.scripts.len()];
        let mut seen = Vec::new();
        let mut together = vec![0usize; self.combinations.len()];
        let mut unmatched = 0;
        for character in text.chars() {
            let found = self.holding(character);
            if found.is_empty() {
                unmatched += 1;
            }
            for script in found {
                if counts[script.0] == 0 {
                    seen.push(*script);
                }
                counts[script.0] += 1;
            }
            for (combination, count) in self.combinations.iter().zip(&mut together) {
                if found
                    .iter()
                    .any(|script| combination.parts.contains(&Some(*script)))
                {
                    *count += 1;
                }
            }
        }
        for (combination, &count) in self.combinations.iter().zip(&together) {
            let part_count = |part: &Option<ScriptId>| part.map_or(0, |part| counts[part.0]);
            let one_part_has_all = combination
                .parts
                .iter()
                .any(|part| part_count(part) == count);
            if let Some(script) = combination.script.filter(|_| !one_part_has_all) {
                if counts[script.0] == 0 {
                    seen.push(script);
                }
                counts[script.0] = count;
            }
        }
        let best = seen.into_iter().min_by(|&a, &b| {
            self.rank(catalog, a, &counts)
                .cmp(&self.rank(catalog, b, &counts))
        })?;
        (counts[best.0] >= unmatched).then_some(Script::new(catalog, best))
    }

    /// What `script` is ranked by: most characters first, then one that holds no other whole, one over no
    /// notation, the one with most characters of its own, one that is no variety, and last the lower code.
    fn rank<'cat>(&self, catalog: &'cat Catalog, script: ScriptId, counts: &[usize]) -> Rank<'cat> {
        let record = &catalog.scripts[script.0];
        (
            Reverse(counts[script.0]),
            self.supersets[script.0],
            is_notation(&record.code),
            Reverse(self.sizes[script.0]),
            record.parent.is_some(),
            record.code.as_str(),
        )
    }

    pub(crate) fn detect_in<'cat>(
        &self,
        language: Language<'cat>,
        text: &str,
    ) -> Option<Script<'cat>> {
        let catalog = language.0.catalog;
        let scripts = script_links(language);
        if scripts.is_empty() {
            return None;
        }
        if written_in_every_script(catalog, scripts) {
            return self.detect(catalog, text);
        }
        let characters: Vec<char> = text
            .chars()
            .filter(|&character| !ignored(character))
            .collect();
        if characters.is_empty() {
            return None;
        }
        let mut best = None;
        let mut best_count = 0;
        for group in groups(catalog, scripts) {
            let script = match group {
                Group::Han => match self.han.and_then(|han| han.decide(catalog, &characters)) {
                    Some(found) => return Some(found),
                    None => continue,
                },
                Group::One(Link::Known(id)) => Script::new(catalog, *id),
                Group::One(Link::Unknown(_)) => continue,
            };
            let count = characters
                .iter()
                .filter(|&&character| script.contains(character))
                .count();
            if count >= characters.len() {
                return Some(script);
            }
            if count > best_count {
                best = Some(script);
                best_count = count;
            }
        }
        best
    }
}

type Rank<'cat> = (Reverse<usize>, bool, bool, Reverse<u64>, bool, &'cat str);

impl HanScripts {
    /// Traditional or Simplified Han when more of the Han characters mark one than the other, Han when as many
    /// mark each, and none when no character is Han.
    fn decide<'cat>(self, catalog: &'cat Catalog, characters: &[char]) -> Option<Script<'cat>> {
        let han = Script::new(catalog, self.plain);
        let traditional = self.traditional.map(|id| Script::new(catalog, id));
        let simplified = self.simplified.map(|id| Script::new(catalog, id));
        let mut found = false;
        let mut marked = (0usize, 0usize);
        for &character in characters
            .iter()
            .filter(|&&character| han.contains(character))
        {
            found = true;
            let is_traditional = traditional.is_some_and(|it| it.is_mark(character));
            let is_simplified = simplified.is_some_and(|it| it.is_mark(character));
            match (is_traditional, is_simplified) {
                (true, false) => marked.0 += 1,
                (false, true) => marked.1 += 1,
                _ => {}
            }
        }
        found.then(|| match marked.0.cmp(&marked.1) {
            Ordering::Greater => traditional.unwrap_or(han),
            Ordering::Less => simplified.unwrap_or(han),
            Ordering::Equal => han,
        })
    }
}

/// The scripts a language lists, or for one that lists none the nearest of its parents that does.
fn script_links(language: Language<'_>) -> &[Link<ScriptId>] {
    language
        .lineage()
        .map(|language| language.record().scripts.as_slice())
        .find(|scripts| !scripts.is_empty())
        .unwrap_or_default()
}

/// Whether `scripts` lists every script of the catalog, as Translingual's does.
fn written_in_every_script(catalog: &Catalog, scripts: &[Link<ScriptId>]) -> bool {
    if scripts.len() < catalog.scripts.len() {
        return false;
    }
    let mut listed = vec![false; catalog.scripts.len()];
    for id in scripts.iter().filter_map(Link::known) {
        listed[id.0] = true;
    }
    listed.into_iter().all(|it| it)
}

enum Group<'cat> {
    Han,
    One(&'cat Link<ScriptId>),
}

/// The scripts in the order a language lists them, with Han, Traditional Han and Simplified Han listed side
/// by side taken together, as Wiktionary's `Hants`.
fn groups<'cat>(catalog: &Catalog, scripts: &'cat [Link<ScriptId>]) -> Vec<Group<'cat>> {
    let code = |link: &'cat Link<ScriptId>| -> &str {
        match link {
            Link::Known(id) => &catalog.scripts[id.0].code,
            Link::Unknown(code) => code,
        }
    };
    let mut found = Vec::with_capacity(scripts.len());
    let mut rest = scripts;
    loop {
        rest = match rest {
            [a, b, c, tail @ ..] if [code(a), code(b), code(c)] == HAN => {
                found.push(Group::Han);
                tail
            }
            [one, tail @ ..] => {
                found.push(Group::One(one));
                tail
            }
            [] => return found,
        };
    }
}

fn is_notation(code: &str) -> bool {
    code.len() == 4 && code.starts_with('Z')
}

/// Whether `character` is left out of a text whose language is known: spaces, and ASCII punctuation.
fn ignored(character: char) -> bool {
    matches!(
        character,
        '\t'..='\r'
            | ' '
            | '\u{a0}'
            | '\u{1680}'
            | '\u{2000}'..='\u{200a}'
            | '\u{2028}'..='\u{2029}'
            | '\u{202f}'
            | '\u{205f}'
            | '\u{3000}'
            | '!'
            | '"'
            | '#'
            | '%'
            | '&'
            | '\''
            | '('
            | ')'
            | '*'
            | ','
            | '-'
            | '.'
            | '/'
            | ':'
            | ';'
            | '?'
            | '@'
            | '['
            | '\\'
            | ']'
            | '_'
            | '{'
            | '}'
    )
}
