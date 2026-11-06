//! The bundled catalog answers as `spec/expectations.json` says, as every other package does.

use std::sync::LazyLock;

use polyglossa::{Catalog, Error, Language, LanguageFamily, LanguageType, Script, ScriptDirection};
use serde_json::{Value, json};

static SPEC: LazyLock<Value> = LazyLock::new(|| {
    let text = std::fs::read_to_string(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../spec/expectations.json"
    ))
    .unwrap();
    serde_json::from_str(&text).unwrap()
});

fn size(ranges: &[std::ops::RangeInclusive<u32>]) -> u32 {
    ranges.iter().map(|r| r.end() - r.start() + 1).sum()
}

#[test]
fn counts_date_misses_and_names() {
    let catalog = Catalog::bundled();
    assert_eq!(catalog.date(), SPEC["date"].as_str());
    assert_eq!(
        catalog.languages().len() as u64,
        SPEC["counts"]["languages"]
    );
    assert_eq!(catalog.families().len() as u64, SPEC["counts"]["families"]);
    assert_eq!(catalog.scripts().len() as u64, SPEC["counts"]["scripts"]);
    for code in SPEC["missing"].as_array().unwrap() {
        assert!(Language::get(code.as_str().unwrap()).is_none(), "{code}");
    }
    for (name, code) in SPEC["named"].as_object().unwrap() {
        assert_eq!(
            Language::named(name).map(polyglossa::Language::code),
            code.as_str(),
            "{name}"
        );
    }
}

#[test]
fn sampled_languages() {
    for expected in SPEC["languages"].as_array().unwrap() {
        let language = Language::of(expected["code"].as_str().unwrap()).unwrap();
        let actual = json!({
            "code": language.code(),
            "name": language.name(),
            "type": language.language_type().as_str(),
            "wikidata": language.wikidata(),
            "family": language.family().map(polyglossa::LanguageFamily::code),
            "scripts": language.scripts().map(polyglossa::Script::code).collect::<Vec<_>>(),
            "ancestors": language.ancestors().map(polyglossa::Language::code).collect::<Vec<_>>(),
            "aliases": language.aliases(),
            "parent": language.parent().map(polyglossa::Language::code),
            "full": language.full().code(),
            "varieties": language.varieties().iter()
                .map(|v| std::iter::once(&v.name).chain(&v.other_names).collect::<Vec<_>>())
                .collect::<Vec<_>>(),
        });
        assert_eq!(&actual, expected);
    }
}

#[test]
fn sampled_families() {
    for expected in SPEC["families"].as_array().unwrap() {
        let family = LanguageFamily::of(expected["code"].as_str().unwrap()).unwrap();
        let actual = json!({
            "code": family.code(),
            "name": family.name(),
            "wikidata": family.wikidata(),
            "parent": family.parent().map(polyglossa::LanguageFamily::code),
            "protoLanguage": family.proto_language().map(polyglossa::Language::code),
            "aliases": family.aliases(),
            "languages": family.languages().map(polyglossa::Language::code).collect::<Vec<_>>(),
        });
        assert_eq!(&actual, expected);
    }
}

#[test]
fn sampled_scripts() {
    for expected in SPEC["scripts"].as_array().unwrap() {
        let script = Script::of(expected["code"].as_str().unwrap()).unwrap();
        let local: serde_json::Map<String, Value> = script
            .local_names()
            .map(|(l, name)| (l.code().to_owned(), json!(name)))
            .collect();
        let actual = json!({
            "code": script.code(),
            "name": script.name(),
            "wikidata": script.wikidata(),
            "system": script.system(),
            "aliases": script.aliases(),
            "parent": script.parent().map(polyglossa::Script::code),
            "direction": script.direction().as_str(),
            "localNames": local,
            "characters": size(script.ranges()),
            "ranges": script.ranges().len(),
            "marks": size(script.marks()),
        });
        assert_eq!(&actual, expected);
    }
}

#[test]
fn detections() {
    for expected in SPEC["detections"].as_array().unwrap() {
        let text = expected["text"].as_str().unwrap();
        let found = match expected["language"].as_str() {
            Some(code) => Language::of(code).unwrap().detect_script(text),
            None => Script::detect(text),
        };
        assert_eq!(
            found.map(polyglossa::Script::code),
            expected["script"].as_str(),
            "{expected}"
        );
    }
    let cyrillic = Script::of("Cyrl").unwrap();
    assert!(cyrillic.contains('Ж'));
    assert!(!cyrillic.contains('A'));
    assert_eq!(Script::of("Arab").unwrap().count("كتاب abc"), 4);
}

#[test]
fn lookups_read_as_rust() {
    assert_eq!(
        Language::of("xx-none"),
        Err(Error::UnknownLanguage("xx-none".to_owned()))
    );
    assert_eq!(
        LanguageFamily::of("xx-none"),
        Err(Error::UnknownFamily("xx-none".to_owned()))
    );
    assert_eq!(
        Script::of("Xxxx-none"),
        Err(Error::UnknownScript("Xxxx-none".to_owned()))
    );
    assert!(matches!(
        Catalog::from_tsv("{}"),
        Err(Error::NotACatalog { line: 1, .. })
    ));
    assert_eq!(
        Language::of("en-ear").unwrap().full(),
        Language::of("en").unwrap()
    );
    assert_eq!(
        Script::of("Deva")
            .unwrap()
            .name_for(Language::of("mr").unwrap()),
        "Balbodh"
    );
    assert_eq!(
        Script::named("Arabic").unwrap().direction(),
        ScriptDirection::RightToLeft
    );
    assert_eq!(
        Language::of("ine-pro").unwrap().language_type(),
        LanguageType::Reconstructed
    );
    assert_eq!(Language::of("en").unwrap().to_string(), "English (en)");
    assert_eq!(format!("{:?}", Language::of("en").unwrap()), "Language(en)");
    let catalog = Catalog::from_tsv(
        "polyglossa\t1\nL\txx\tExample\t\t\t\tLatn\nS\tLatn\tLatin\t\t\t\t\t\t\t41-5A\n",
    )
    .unwrap();
    assert_eq!(catalog.date(), None);
    assert_eq!(
        catalog
            .of("xx")
            .unwrap()
            .detect_script("ABC")
            .map(polyglossa::Script::code),
        Some("Latn")
    );
    assert!(Language::get("xx").is_none());
    let broken = Catalog::from_tsv("polyglossa\t1\nS\tLatn\tLatin\t\t\t\t\t\t\t41-ZZ\n");
    assert!(matches!(broken, Err(Error::NotACatalog { line: 2, .. })));
}

#[test]
fn the_version_is_the_day_of_the_data() {
    let date: Vec<u32> = SPEC["date"]
        .as_str()
        .unwrap()
        .split('-')
        .map(|it| it.parse().unwrap())
        .collect();
    assert_eq!(
        env!("CARGO_PKG_VERSION"),
        format!("{}.{}.{}", date[0], date[1], date[2])
    );
}
