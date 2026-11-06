"""The catalog: the languages, families and scripts read from one ``catalog.tsv``, linked to one another."""

from __future__ import annotations

import functools
from collections.abc import Callable, Iterable, Mapping
from importlib import resources
from typing import TypeVar

from polyglossa._detection import ScriptDetection
from polyglossa._entries import Language, LanguageFamily, Script, Unknown
from polyglossa._errors import UnknownLanguageError
from polyglossa._read import HEADER, RawCatalog, read

__all__ = ["HEADER", "Catalog"]

_Entry = TypeVar("_Entry")
_Named = TypeVar("_Named", Language, Script)


class Catalog:
    """A set of languages, families and scripts that know one another.

    :meth:`bundled`, English Wiktionary's own, is the one :class:`Language`, :class:`LanguageFamily` and
    :class:`Script` look up.

    Args:
        tsv: A catalog as the Kotlin library's ``Catalog.toTsv`` writes it.

    Raises:
        NotACatalogError: With the line, when ``tsv`` is not such a catalog.

    Attributes:
        date: The day Wiktionary's data was exported, as ``2026-11-06``, or ``None`` when the catalog does not say.
    """

    __slots__ = (
        "__weakref__",
        "_detection_cache",
        "_families",
        "_languages",
        "_names",
        "_script_names",
        "_scripts",
        "date",
    )

    def __init__(self, tsv: str) -> None:
        raw = read(tsv)
        self.date: str | None = raw.date
        self._languages: dict[str, Language] = {
            code: Language(code, it.name, it.type, it.wikidata, it.aliases, it.varieties, self)
            for code, it in sorted(raw.languages.items())
        }
        self._families: dict[str, LanguageFamily] = {
            code: LanguageFamily(code, it.name, it.wikidata, it.aliases) for code, it in sorted(raw.families.items())
        }
        self._scripts: dict[str, Script] = {
            code: Script(
                code,
                it.name,
                it.wikidata,
                it.system,
                it.aliases,
                it.direction,
                it.ranges,
                it.marks,
                at,
                it.local_names,
                self,
            )
            for at, (code, it) in enumerate(sorted(raw.scripts.items()))
        }
        self._detection_cache: ScriptDetection | None = None
        self._link(raw)
        self._names = _names_by(self._languages.values(), lambda language: not language.is_etymology_only)
        self._script_names = _names_by(self._scripts.values(), lambda script: not script.is_variety)

    def _link(self, raw: RawCatalog) -> None:
        """Resolves every code one entry gives for another, once, and decides how each language is detected."""
        members: dict[str, list[Language]] = {}
        for code, language in self._languages.items():
            it = raw.languages[code]
            links = language._links
            links.family = None if it.family is None else _link(self._families, it.family)
            links.scripts = tuple(_link(self._scripts, script) for script in it.scripts)
            links.ancestors = tuple(self._languages[a] for a in it.ancestors if a in self._languages)
            links.parent = None if it.parent is None else self._languages.get(it.parent)
            if it.family is not None:
                members.setdefault(it.family, []).append(language)
        for code, family in self._families.items():
            it_family = raw.families[code]
            family._links.parent = None if it_family.parent is None else self._families.get(it_family.parent)
            family._links.proto_language = self._languages.get(it_family.proto_language or f"{code}-pro")
            family._links.languages = tuple(members.get(code, ()))
        for code, script in self._scripts.items():
            parent = raw.scripts[code].parent
            script._links.parent = None if parent is None else _link(self._scripts, parent)

    @staticmethod
    @functools.cache
    def bundled() -> Catalog:
        """English Wiktionary's languages, families and scripts as the package was built with them."""
        text = resources.files("polyglossa").joinpath("catalog.tsv").read_text(encoding="utf-8")
        return Catalog(text)

    @property
    def languages(self) -> list[Language]:
        """Every language and etymology-only variety, sorted by code."""
        return list(self._languages.values())

    @property
    def families(self) -> list[LanguageFamily]:
        """Every family, sorted by code."""
        return list(self._families.values())

    @property
    def scripts(self) -> list[Script]:
        """Every script, sorted by code."""
        return list(self._scripts.values())

    def get(self, code: str | None) -> Language | None:
        """The language with ``code``, or ``None``."""
        return None if code is None else self._languages.get(code)

    def __getitem__(self, code: str) -> Language:
        language = self.get(code)
        if language is None:
            raise UnknownLanguageError(code)
        return language

    def __contains__(self, code: object) -> bool:
        return isinstance(code, str) and code in self._languages

    def named(self, name: str) -> Language | None:
        """The language called ``name``; a full language wins over an etymology-only variety of the same name."""
        return self._names.get(name.strip())

    def family(self, code: str | None) -> LanguageFamily | None:
        """The family with ``code``, or ``None``."""
        return None if code is None else self._families.get(code)

    def script(self, code: str | None) -> Script | None:
        """The script with ``code``, or ``None``."""
        return None if code is None else self._scripts.get(code)

    def script_named(self, name: str) -> Script | None:
        """The script called ``name``; one that is no variety of another wins over one that is."""
        return self._script_names.get(name.strip())

    def detect_script(self, text: str) -> Script | None:
        """Tells which script ``text`` is written in, whatever its language, as ``findBestScriptWithoutLang`` does.

        The script most of its characters belong to wins. A tie goes to a script that holds no other whole, as
        Greek does beside Polytonic Greek, then to a script over notation such as ``Zmth``, then to the one with
        the most characters, as Han is beside Latin, then to one that is no variety of another.
        :meth:`Language.detect_script` knows better when the language is known.

        Returns:
            The script, or ``None`` when more of the characters belong to no script.
        """
        return self._detection.detect(text)

    @property
    def _detection(self) -> ScriptDetection:
        if self._detection_cache is None:
            self._detection_cache = ScriptDetection(self.scripts)
        return self._detection_cache


def _link(entries: Mapping[str, _Entry], code: str) -> _Entry | Unknown:
    entry = entries.get(code)
    return Unknown(code) if entry is None else entry


def _names_by(entries: Iterable[_Named], wins: Callable[[_Named], bool]) -> dict[str, _Named]:
    """Each name of ``entries`` with the entry that has it, one that ``wins`` beating one that does not, and the
    later beating the earlier otherwise."""
    ordered = list(entries)
    names: dict[str, _Named] = {}
    for is_winner in (False, True):
        for entry in ordered:
            if wins(entry) == is_winner:
                names[entry.name] = entry
    return names
