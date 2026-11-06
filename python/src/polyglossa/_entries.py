"""A catalog's languages, families and scripts, which know one another."""

from __future__ import annotations

import bisect
from collections.abc import Iterator
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any, TypeAlias, TypeVar

from polyglossa._errors import UnknownFamilyError, UnknownLanguageError, UnknownScriptError
from polyglossa._kinds import LanguageType, ScriptDirection, Variety

if TYPE_CHECKING:
    from polyglossa._catalog import Catalog
    from polyglossa._detection import Group

_Entry = TypeVar("_Entry")


@dataclass(frozen=True, slots=True)
class Unknown:
    """A code one entry gives for another that its catalog does not have."""

    code: str


Link: TypeAlias = _Entry | Unknown
"""An entry a code leads to, or :class:`Unknown` when the catalog does not have it."""


class _Lookup(type):
    """Lets a type be indexed and searched by code, as ``Language["en"]`` and ``"en" in Language``."""

    def __getitem__(cls, code: str) -> Any:
        return cls.of(code)  # type: ignore[attr-defined]

    def __contains__(cls, code: object) -> bool:
        return isinstance(code, str) and cls.get(code) is not None  # type: ignore[attr-defined]

    def __iter__(cls) -> Iterator[Any]:
        return iter(cls.all())  # type: ignore[attr-defined]


@dataclass(slots=True)
class _LanguageLinks:
    family: Link[LanguageFamily] | None = None
    scripts: tuple[Link[Script], ...] = ()
    ancestors: tuple[Language, ...] = ()
    parent: Language | None = None
    groups: tuple[Group, ...] | None = None
    is_written_in_every_script: bool = False


@dataclass(frozen=True, slots=True, eq=False, repr=False)
class Language(metaclass=_Lookup):
    """One language as English Wiktionary's ``Module:languages`` and ``Module:etymology languages`` describe it.

    Attributes:
        code: Its code, as ``en`` or ``en-US``.
        name: Its name, as its section in an entry is headed.
        type: What kind of language it is.
        wikidata: Its Wikidata item, as ``Q1860``, or ``None``.
        aliases: The other names Wiktionary gives it.
        varieties: The varieties Wiktionary names without a code of their own.
    """

    code: str
    name: str
    type: LanguageType
    wikidata: str | None
    aliases: tuple[str, ...]
    varieties: tuple[Variety, ...]
    _catalog: Catalog
    _links: _LanguageLinks = field(default_factory=_LanguageLinks)

    @property
    def is_etymology_only(self) -> bool:
        """Whether it is named in etymologies only, with no section of its own in an entry."""
        return self.type is LanguageType.ETYMOLOGY_ONLY

    @property
    def family(self) -> LanguageFamily | None:
        """The family it belongs to, or ``None``.

        An etymology-only variety with none of its own has that of the nearest of its parents that has one, as
        American English has English's through North American English.
        """
        for language in self._lineage():
            link = language._links.family
            if link is not None:
                return None if isinstance(link, Unknown) else link
        return None

    @property
    def scripts(self) -> list[Script]:
        """The scripts it is written in, most usual first, those the catalog knows."""
        return [link for link in self._links.scripts if not isinstance(link, Unknown)]

    @property
    def ancestors(self) -> list[Language]:
        """The languages it descends from, those the catalog knows."""
        return list(self._links.ancestors)

    @property
    def parent(self) -> Language | None:
        """The language an etymology-only variety belongs to, or ``None`` for any other."""
        return self._links.parent

    @property
    def full(self) -> Language:
        """The full language this stands for: itself, or for an etymology-only variety the one it belongs to."""
        full = self
        for language in self._lineage():
            full = language
            if not language.is_etymology_only:
                break
        return full

    def detect_script(self, text: str) -> Script | None:
        """Tells which of its scripts ``text`` is written in, as Wiktionary's ``findBestScript`` does.

        Spaces and ASCII punctuation are left out, and the scripts are tried in order: the first that has every
        character wins, or else the one with the most of them, the earlier on a tie. Chinese and the other
        languages written in Han give Traditional or Simplified Han when more of the characters mark one than the
        other, and Han when as many mark each. A language written in every script finds it as
        :meth:`Catalog.detect_script` does, and an etymology-only variety with no scripts of its own uses its
        language's.

        Args:
            text: Plain text; wiki markup counts as characters.

        Returns:
            The script, or ``None`` when none of the language's scripts has any of its characters.

        Example:
            >>> Language["sh"].detect_script("Москва").code
            'Cyrl'
            >>> Language["zh"].detect_script("語言").code
            'Hant'
            >>> Language["en"].detect_script("123") is None
            True
        """
        return self._catalog._detection.detect_in(self, text)

    def _lineage(self) -> Iterator[Language]:
        """Itself, then its parent, its parent's parent and so on, each once."""
        seen: list[Language] = []
        language: Language | None = self
        while language is not None and language not in seen:
            seen.append(language)
            yield language
            language = language._links.parent

    def __str__(self) -> str:
        return f"{self.name} ({self.code})"

    def __repr__(self) -> str:
        return f"Language(code={self.code!r}, name={self.name!r})"

    @classmethod
    def of(cls, code: str) -> Language:
        """The language with ``code``.

        Raises:
            UnknownLanguageError: When Wiktionary has none, as ``Language["xx"]`` does.
        """
        language = cls.get(code)
        if language is None:
            raise UnknownLanguageError(code)
        return language

    @classmethod
    def get(cls, code: str) -> Language | None:
        """The language with ``code``, or ``None`` when Wiktionary has none."""
        return _bundled().get(code)

    @classmethod
    def named(cls, name: str) -> Language | None:
        """The language called ``name``, as its section in an entry is headed, or ``None``."""
        return _bundled().named(name)

    @classmethod
    def all(cls) -> list[Language]:
        """Every language and etymology-only variety, sorted by code."""
        return _bundled().languages


@dataclass(slots=True)
class _FamilyLinks:
    parent: LanguageFamily | None = None
    proto_language: Language | None = None
    languages: tuple[Language, ...] = ()


@dataclass(frozen=True, slots=True, eq=False, repr=False)
class LanguageFamily(metaclass=_Lookup):
    """One language family as English Wiktionary's ``Module:families`` describes it.

    Attributes:
        code: Its code, as ``gmw``.
        name: Its name.
        wikidata: Its Wikidata item, or ``None``.
        aliases: The other names Wiktionary gives it.
    """

    code: str
    name: str
    wikidata: str | None
    aliases: tuple[str, ...]
    _links: _FamilyLinks = field(default_factory=_FamilyLinks)

    @property
    def parent(self) -> LanguageFamily | None:
        """The family it belongs to, or ``None``."""
        return self._links.parent

    @property
    def proto_language(self) -> Language | None:
        """The language it descends from: the one Wiktionary names, or else ``<code>-pro`` when there is one."""
        return self._links.proto_language

    @property
    def languages(self) -> list[Language]:
        """The languages in it directly, not those of the families under it, sorted by code."""
        return list(self._links.languages)

    def __str__(self) -> str:
        return f"{self.name} ({self.code})"

    def __repr__(self) -> str:
        return f"LanguageFamily(code={self.code!r}, name={self.name!r})"

    @classmethod
    def of(cls, code: str) -> LanguageFamily:
        """The family with ``code``.

        Raises:
            UnknownFamilyError: When Wiktionary has none.
        """
        family = cls.get(code)
        if family is None:
            raise UnknownFamilyError(code)
        return family

    @classmethod
    def get(cls, code: str) -> LanguageFamily | None:
        """The family with ``code``, or ``None`` when Wiktionary has none."""
        return _bundled().family(code)

    @classmethod
    def all(cls) -> list[LanguageFamily]:
        """Every family, sorted by code."""
        return _bundled().families


@dataclass(slots=True)
class _ScriptLinks:
    parent: Link[Script] | None = None


@dataclass(frozen=True, slots=True, eq=False, repr=False)
class Script(metaclass=_Lookup):
    """One script as English Wiktionary's ``Module:scripts/data`` describes it.

    Attributes:
        code: Its code, as ``Latn`` or ``fa-Arab``.
        name: Its name.
        wikidata: Its Wikidata item, or ``None``.
        system: What kind of writing system it is, as ``alphabet``, or ``None``.
        aliases: The other names Wiktionary gives it.
        direction: Which way it runs.
        ranges: Its characters, as sorted ranges of code points apart from one another.
        marks: The characters that mark a text as written in it rather than in a script it shares characters
            with: those of Traditional Han that Simplified Han writes otherwise, and the other way round.
    """

    code: str
    name: str
    wikidata: str | None
    system: str | None
    aliases: tuple[str, ...]
    direction: ScriptDirection
    ranges: tuple[range, ...]
    marks: tuple[range, ...]
    _index: int
    _local_names: tuple[tuple[str, str], ...]
    _catalog: Catalog
    _range_starts: tuple[int, ...] = field(init=False)
    _mark_starts: tuple[int, ...] = field(init=False)
    _links: _ScriptLinks = field(default_factory=_ScriptLinks)

    def __post_init__(self) -> None:
        object.__setattr__(self, "_range_starts", tuple(r.start for r in self.ranges))
        object.__setattr__(self, "_mark_starts", tuple(r.start for r in self.marks))

    @property
    def parent(self) -> Script | None:
        """The script this is a variety of, such as Han for Simplified Han, or ``None``."""
        link = self._links.parent
        return None if link is None or isinstance(link, Unknown) else link

    @property
    def is_variety(self) -> bool:
        """Whether it names a script it is a variety of, whether or not its catalog has it."""
        return self._links.parent is not None

    def name_for(self, language: Language) -> str:
        """The name ``language`` gives this script, which is :attr:`name` unless it has its own.

        Example:
            >>> Script["Deva"].name_for(Language["mr"])
            'Balbodh'
        """
        return next((local for code, local in self._local_names if code == language.code), self.name)

    @property
    def local_names(self) -> dict[Language, str]:
        """The names some languages give it instead of :attr:`name`, such as Balbodh for Marathi's Devanagari."""
        names = {}
        for code, local in self._local_names:
            language = self._catalog.get(code)
            if language is not None:
                names[language] = local
        return names

    def __contains__(self, code_point: object) -> bool:
        """Whether ``code_point`` is one of its characters, as ``0x416 in Script["Cyrl"]``."""
        return isinstance(code_point, int) and _holds(self.ranges, self._range_starts, code_point)

    def count(self, text: str) -> int:
        """How many of the characters of ``text`` are its own, counting each code point once.

        Example:
            >>> Script["Arab"].count("كتاب abc")
            4
        """
        ranges, starts = self.ranges, self._range_starts
        return sum(1 for character in text if _holds(ranges, starts, ord(character)))

    def _holds(self, code_point: int) -> bool:
        return _holds(self.ranges, self._range_starts, code_point)

    def _is_mark(self, code_point: int) -> bool:
        return _holds(self.marks, self._mark_starts, code_point)

    def __str__(self) -> str:
        return f"{self.name} ({self.code})"

    def __repr__(self) -> str:
        return f"Script(code={self.code!r}, name={self.name!r})"

    @classmethod
    def detect(cls, text: str) -> Script | None:
        """Tells which script ``text`` is written in, whatever its language; see :meth:`Catalog.detect_script`.

        Example:
            >>> Script.detect("Ελληνικά").code
            'Grek'
        """
        return _bundled().detect_script(text)

    @classmethod
    def of(cls, code: str) -> Script:
        """The script with ``code``.

        Raises:
            UnknownScriptError: When Wiktionary has none.
        """
        script = cls.get(code)
        if script is None:
            raise UnknownScriptError(code)
        return script

    @classmethod
    def get(cls, code: str) -> Script | None:
        """The script with ``code``, or ``None`` when Wiktionary has none."""
        return _bundled().script(code)

    @classmethod
    def named(cls, name: str) -> Script | None:
        """The script called ``name``, or ``None``; the one without a parent when several are."""
        return _bundled().script_named(name)

    @classmethod
    def all(cls) -> list[Script]:
        """Every script, sorted by code."""
        return _bundled().scripts


def _holds(ranges: tuple[range, ...], starts: tuple[int, ...], code_point: int) -> bool:
    """Whether one of ``ranges``, sorted and apart and starting at ``starts``, holds ``code_point``."""
    at = bisect.bisect_right(starts, code_point) - 1
    return at >= 0 and code_point < ranges[at].stop


def _bundled() -> Catalog:
    from polyglossa._catalog import Catalog

    return Catalog.bundled()
