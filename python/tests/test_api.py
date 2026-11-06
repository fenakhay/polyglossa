"""The package reads as Python: lookups, errors, kinds, and what the public import path leads to."""

from __future__ import annotations

import pytest

import polyglossa
from polyglossa import (
    Catalog,
    Language,
    LanguageFamily,
    LanguageType,
    NotACatalogError,
    PolyglossaError,
    Script,
    ScriptDirection,
    UnknownFamilyError,
    UnknownLanguageError,
    UnknownScriptError,
    __version__,
)

from .conftest import SPEC

SMALL = "polyglossa\t1\nL\txx\tExample\t\t\t\tLatn\nS\tLatn\tLatin\t\t\t\t\t\t\t41-5A\n"


def test_public_names_are_the_implementations() -> None:
    from polyglossa import _catalog, _entries, _errors, _kinds

    assert polyglossa.Catalog is _catalog.Catalog
    assert polyglossa.Language is _entries.Language
    assert polyglossa.NotACatalogError is _errors.NotACatalogError
    assert polyglossa.LanguageType is _kinds.LanguageType
    assert sorted(polyglossa.__all__) == sorted(name for name in polyglossa.__all__ if hasattr(polyglossa, name))


@pytest.mark.parametrize(
    ("lookup", "error"),
    [
        (lambda: Language["xx-none"], UnknownLanguageError),
        (lambda: LanguageFamily["xx-none"], UnknownFamilyError),
        (lambda: Script["Xxxx-none"], UnknownScriptError),
        (lambda: Catalog.bundled()["xx-none"], UnknownLanguageError),
    ],
    ids=["language", "family", "script", "catalog"],
)
def test_a_lookup_that_must_succeed_raises(lookup: object, error: type[Exception]) -> None:
    with pytest.raises(error) as raised:
        lookup()  # type: ignore[operator]
    assert isinstance(raised.value, KeyError)
    assert isinstance(raised.value, PolyglossaError)
    assert str(raised.value).startswith("polyglossa: unknown ")


def test_misses_and_membership() -> None:
    assert Language.get("xx-none") is None
    assert LanguageFamily.get("xx-none") is None
    assert Script.get("Xxxx-none") is None
    assert "ang" in Language
    assert "xx-none" not in Language
    assert 42 not in Language
    assert "en" in Catalog.bundled()


def test_links() -> None:
    assert Language["en-ear"].full is Language["en"]
    assert Script["Deva"].name_for(Language["mr"]) == "Balbodh"
    assert Script.named("Arabic") is not None
    assert Script.named("Arabic").direction is ScriptDirection.RIGHT_TO_LEFT  # type: ignore[union-attr]
    assert Language["ine-pro"].type is LanguageType.RECONSTRUCTED
    assert str(Language.of("ine-pro").type) == "reconstructed"
    assert str(Language["en"]) == "English (en)"
    assert repr(Language["en"]) == "Language(code='en', name='English')"
    assert {Language["en"]: 1}[Language.of("en")] == 1


def test_what_a_lookup_returns_is_a_copy() -> None:
    ancestors = Language["en"].ancestors
    ancestors.clear()
    assert Language["en"].ancestors


def test_code_points() -> None:
    assert 0x416 in Script["Cyrl"]
    assert 0x41 not in Script["Cyrl"]
    assert "Ж" not in Script["Cyrl"]
    assert Script["Arab"].count("كتاب abc") == 4


def test_a_catalog_of_ones_own() -> None:
    catalog = Catalog(SMALL)
    assert catalog.date is None
    assert catalog["xx"].detect_script("ABC") is catalog.script("Latn")
    assert catalog.detect_script("ABC") is catalog.script("Latn")
    assert Language.get("xx") is None


@pytest.mark.parametrize(
    ("text", "line"),
    [
        ("{}", 1),
        ("polyglossa\t1\nX\tnothing\n", 2),
        ("polyglossa\t1\nL\txx\tExample\tz\n", 2),
        ("polyglossa\t1\n\nS\tLatn\tLatin\t\t\t\t\t\t\t41-ZZ\n", 3),
        ("polyglossa\t1\nS\tLatn\tLatin\t\t\t\t\tsideways\n", 2),
    ],
    ids=["no header", "unknown kind", "unknown language type", "bad code point", "unknown direction"],
)
def test_a_catalog_that_cannot_be_read_says_which_line(text: str, line: int) -> None:
    with pytest.raises(NotACatalogError) as raised:
        Catalog(text)
    assert raised.value.line == line
    assert isinstance(raised.value, ValueError)


def test_the_version_is_the_day_of_the_data() -> None:
    assert __version__ == ".".join(str(int(part)) for part in SPEC["date"].split("-"))
