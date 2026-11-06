"""The bundled catalog answers as ``spec/expectations.json`` says, as the Kotlin library and every other package do."""

from __future__ import annotations

from typing import Any

import pytest

from polyglossa import Catalog, Language, LanguageFamily, Script

from .conftest import SPEC


def _code(entry: Language | LanguageFamily | Script | None) -> str | None:
    return None if entry is None else entry.code


def test_counts_and_date() -> None:
    catalog = Catalog.bundled()
    assert catalog.date == SPEC["date"]
    assert {
        "languages": len(catalog.languages),
        "families": len(catalog.families),
        "scripts": len(catalog.scripts),
    } == SPEC["counts"]


@pytest.mark.parametrize("code", SPEC["missing"])
def test_missing(code: str) -> None:
    assert Language.get(code) is None


@pytest.mark.parametrize(("name", "code"), SPEC["named"].items())
def test_named(name: str, code: str | None) -> None:
    assert _code(Language.named(name)) == code


@pytest.mark.parametrize("expected", SPEC["languages"], ids=lambda it: it["code"])
def test_language(expected: dict[str, Any]) -> None:
    language = Language.of(expected["code"])
    assert {
        "code": language.code,
        "name": language.name,
        "type": language.type.value,
        "wikidata": language.wikidata,
        "family": _code(language.family),
        "scripts": [s.code for s in language.scripts],
        "ancestors": [a.code for a in language.ancestors],
        "aliases": list(language.aliases),
        "parent": _code(language.parent),
        "full": language.full.code,
        "varieties": [[v.name, *v.other_names] for v in language.varieties],
    } == expected


@pytest.mark.parametrize("expected", SPEC["families"], ids=lambda it: it["code"])
def test_family(expected: dict[str, Any]) -> None:
    family = LanguageFamily.of(expected["code"])
    assert {
        "code": family.code,
        "name": family.name,
        "wikidata": family.wikidata,
        "parent": _code(family.parent),
        "protoLanguage": _code(family.proto_language),
        "aliases": list(family.aliases),
        "languages": [language.code for language in family.languages],
    } == expected


@pytest.mark.parametrize("expected", SPEC["scripts"], ids=lambda it: it["code"])
def test_script(expected: dict[str, Any]) -> None:
    script = Script.of(expected["code"])
    assert {
        "code": script.code,
        "name": script.name,
        "wikidata": script.wikidata,
        "system": script.system,
        "aliases": list(script.aliases),
        "parent": _code(script.parent),
        "direction": script.direction.value,
        "localNames": {language.code: name for language, name in script.local_names.items()},
        "characters": sum(len(r) for r in script.ranges),
        "ranges": len(script.ranges),
        "marks": sum(len(r) for r in script.marks),
    } == expected


@pytest.mark.parametrize(
    "expected",
    SPEC["detections"],
    ids=lambda it: f"{it['language'] or 'no language'}: {it['text']}",
)
def test_detection(expected: dict[str, Any]) -> None:
    code = expected["language"]
    script = Language.of(code).detect_script(expected["text"]) if code else Script.detect(expected["text"])
    assert _code(script) == expected["script"]
