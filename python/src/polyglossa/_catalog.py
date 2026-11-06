"""The catalog and what it holds, read from the tab-separated ``catalog.tsv`` the Kotlin library writes."""

from __future__ import annotations

import bisect
import enum
import functools
from dataclasses import dataclass, field
from importlib import resources
from typing import Dict, Iterator, List, Optional, Tuple

HEADER = "polyglossa\t1"


class UnknownLanguageError(KeyError):
    """Wiktionary has no language with this code."""


class UnknownFamilyError(KeyError):
    """Wiktionary has no family with this code."""


class UnknownScriptError(KeyError):
    """Wiktionary has no script with this code."""


class NotACatalogError(ValueError):
    """The text is not a catalog Polyglossa wrote."""


class LanguageType(enum.Enum):
    """What kind of language Wiktionary takes one to be, which decides where its entries may live."""

    REGULAR = "regular"
    RECONSTRUCTED = "reconstructed"
    APPENDIX_CONSTRUCTED = "appendix-constructed"
    ETYMOLOGY_ONLY = "etymology-only"


class ScriptDirection(enum.Enum):
    """Which way a script runs."""

    LEFT_TO_RIGHT = "ltr"
    RIGHT_TO_LEFT = "rtl"
    VERTICAL_LEFT_TO_RIGHT = "vertical-ltr"
    VERTICAL_RIGHT_TO_LEFT = "vertical-rtl"


@dataclass(frozen=True)
class Variety:
    """A variety of a language Wiktionary names without giving it a code, such as a dialect."""

    name: str
    other_names: Tuple[str, ...] = ()

    def __str__(self) -> str:
        return self.name if not self.other_names else f"{self.name} ({', '.join(self.other_names)})"


class _Lookup(type):
    """Lets a type be indexed and searched by code, ``Language["en"]`` and ``"en" in Language``."""

    def __getitem__(cls, code: str):
        return cls.of(code)

    def __contains__(cls, code: object) -> bool:
        return isinstance(code, str) and cls.get(code) is not None

    def __iter__(cls) -> Iterator:
        return iter(cls.all())


@dataclass(frozen=True, eq=True)
class Language(metaclass=_Lookup):
    """One language as English Wiktionary's ``Module:languages`` and ``Module:etymology languages`` describe it."""

    code: str
    name: str
    type: LanguageType
    wikidata: Optional[str]
    aliases: Tuple[str, ...]
    varieties: Tuple[Variety, ...]
    _family_code: Optional[str] = field(repr=False)
    _script_codes: Tuple[str, ...] = field(repr=False)
    _ancestor_codes: Tuple[str, ...] = field(repr=False)
    _parent_code: Optional[str] = field(repr=False)
    _catalog: "Catalog" = field(repr=False, compare=False, hash=False)

    @property
    def is_etymology_only(self) -> bool:
        """Whether it is named in etymologies only, with no section of its own in an entry."""
        return self.type is LanguageType.ETYMOLOGY_ONLY

    @property
    def family(self) -> Optional["LanguageFamily"]:
        """The family it belongs to, or ``None``; an etymology-only variety has its language's."""
        code = self._family_code or (self.parent._family_code if self.parent else None)
        return self._catalog.family(code) if code else None

    @property
    def scripts(self) -> List["Script"]:
        """The scripts it is written in, most usual first, those the catalog knows."""
        return [s for s in (self._catalog.script(c) for c in self._script_codes) if s is not None]

    @property
    def ancestors(self) -> List["Language"]:
        """The languages it descends from, those the catalog knows."""
        return [a for a in (self._catalog.get(c) for c in self._ancestor_codes) if a is not None]

    @property
    def parent(self) -> Optional["Language"]:
        """The language an etymology-only variety belongs to, or ``None`` for any other."""
        return self._catalog.get(self._parent_code) if self._parent_code else None

    @property
    def full(self) -> "Language":
        """The full language this stands for: itself, or for an etymology-only variety the one it belongs to."""
        language, seen = self, {self.code}
        while language.is_etymology_only and language.parent and language.parent.code not in seen:
            language = language.parent
            seen.add(language.code)
        return language

    def detect_script(self, text: str) -> Optional["Script"]:
        """The script ``text`` is written in, among this language's :attr:`scripts`, as Wiktionary's
        ``findBestScript`` finds it, or ``None`` when none of them has any of its characters.

        Spaces and ASCII punctuation are left out, and the scripts are tried in order: the first that has every
        character wins, or else the one with the most of them, the earlier on a tie. Chinese and the other
        languages written in Han give Traditional or Simplified Han when more of the characters mark one than the
        other, and Han when as many mark each. A language written in every script finds it as
        :meth:`Catalog.detect_script` does, and an etymology-only variety with no scripts of its own uses its
        language's. Pass plain text: wiki markup counts as characters.

        >>> Language["sh"].detect_script("Москва").code
        'Cyrl'
        >>> Language["zh"].detect_script("語言").code
        'Hant'
        >>> Language["en"].detect_script("123") is None
        True
        """
        return self._catalog._detection().detect_in(self, text)

    def __str__(self) -> str:
        return f"{self.name} ({self.code})"

    @classmethod
    def of(cls, code: str) -> "Language":
        """The language with ``code``; raises :class:`UnknownLanguageError` when Wiktionary has none."""
        language = Catalog.bundled().get(code)
        if language is None:
            raise UnknownLanguageError(code)
        return language

    @classmethod
    def get(cls, code: str) -> Optional["Language"]:
        """The language with ``code``, or ``None`` when Wiktionary has none."""
        return Catalog.bundled().get(code)

    @classmethod
    def named(cls, name: str) -> Optional["Language"]:
        """The language called ``name``, as its section in an entry is headed, or ``None``."""
        return Catalog.bundled().named(name)

    @classmethod
    def all(cls) -> List["Language"]:
        """Every language and etymology-only variety, sorted by code."""
        return Catalog.bundled().languages


@dataclass(frozen=True, eq=True)
class LanguageFamily(metaclass=_Lookup):
    """One language family as English Wiktionary's ``Module:families`` describes it."""

    code: str
    name: str
    wikidata: Optional[str]
    aliases: Tuple[str, ...]
    _parent_code: Optional[str] = field(repr=False)
    _proto_language_code: Optional[str] = field(repr=False)
    _catalog: "Catalog" = field(repr=False, compare=False, hash=False)

    @property
    def parent(self) -> Optional["LanguageFamily"]:
        """The family it belongs to, or ``None``."""
        return self._catalog.family(self._parent_code) if self._parent_code else None

    @property
    def proto_language(self) -> Optional[Language]:
        """The language it descends from: the one Wiktionary names, or else ``<code>-pro`` when there is one."""
        return self._catalog.get(self._proto_language_code or f"{self.code}-pro")

    @property
    def languages(self) -> List[Language]:
        """The languages in it directly, not those of the families under it, sorted by code."""
        return self._catalog._in_family(self.code)

    def __str__(self) -> str:
        return f"{self.name} ({self.code})"

    @classmethod
    def of(cls, code: str) -> "LanguageFamily":
        """The family with ``code``; raises :class:`UnknownFamilyError` when Wiktionary has none."""
        family = Catalog.bundled().family(code)
        if family is None:
            raise UnknownFamilyError(code)
        return family

    @classmethod
    def get(cls, code: str) -> Optional["LanguageFamily"]:
        """The family with ``code``, or ``None`` when Wiktionary has none."""
        return Catalog.bundled().family(code)

    @classmethod
    def all(cls) -> List["LanguageFamily"]:
        """Every family, sorted by code."""
        return Catalog.bundled().families


@dataclass(frozen=True, eq=True)
class Script(metaclass=_Lookup):
    """One script as English Wiktionary's ``Module:scripts/data`` describes it."""

    code: str
    name: str
    wikidata: Optional[str]
    system: Optional[str]
    aliases: Tuple[str, ...]
    direction: ScriptDirection
    ranges: Tuple[range, ...] = field(repr=False)
    marks: Tuple[range, ...] = field(repr=False)
    _parent_code: Optional[str] = field(repr=False)
    _local_name_codes: Tuple[Tuple[str, str], ...] = field(repr=False)
    _catalog: "Catalog" = field(repr=False, compare=False, hash=False)

    @property
    def parent(self) -> Optional["Script"]:
        """The script this is a variety of, such as Han for Simplified Han, or ``None``."""
        return self._catalog.script(self._parent_code) if self._parent_code else None

    def name_for(self, language: Language) -> str:
        """The name ``language`` gives this script, which is :attr:`name` unless it has its own."""
        return dict(self._local_name_codes).get(language.code, self.name)

    @property
    def local_names(self) -> Dict[Language, str]:
        """The names some languages give it instead of :attr:`name`, such as Balbodh for Marathi's Devanagari."""
        names = {}
        for code, local in self._local_name_codes:
            language = self._catalog.get(code)
            if language is not None:
                names[language] = local
        return names

    def __contains__(self, code_point: object) -> bool:
        """Whether ``code_point`` is one of its characters, as ``0x416 in Script.of("Cyrl")``."""
        return isinstance(code_point, int) and _holds(self.ranges, code_point)

    def count(self, text: str) -> int:
        """How many of the characters of ``text`` are its own, counting each code point once."""
        return sum(1 for char in text if ord(char) in self)

    def _is_mark(self, code_point: int) -> bool:
        return _holds(self.marks, code_point)

    def __str__(self) -> str:
        return f"{self.name} ({self.code})"

    @classmethod
    def detect(cls, text: str) -> Optional["Script"]:
        """The script ``text`` is written in, whatever its language, or ``None``; see :meth:`Catalog.detect_script`.

        >>> Script.detect("Ελληνικά").code
        'Grek'
        """
        return Catalog.bundled().detect_script(text)

    @classmethod
    def of(cls, code: str) -> "Script":
        """The script with ``code``; raises :class:`UnknownScriptError` when Wiktionary has none."""
        script = Catalog.bundled().script(code)
        if script is None:
            raise UnknownScriptError(code)
        return script

    @classmethod
    def get(cls, code: str) -> Optional["Script"]:
        """The script with ``code``, or ``None`` when Wiktionary has none."""
        return Catalog.bundled().script(code)

    @classmethod
    def named(cls, name: str) -> Optional["Script"]:
        """The script called ``name``, or ``None``; the one without a parent when several are."""
        return Catalog.bundled().script_named(name)

    @classmethod
    def all(cls) -> List["Script"]:
        """Every script, sorted by code."""
        return Catalog.bundled().scripts


class Catalog:
    """A set of languages, families and scripts that know one another.

    :meth:`bundled`, English Wiktionary's own, is the one :class:`Language`, :class:`LanguageFamily` and
    :class:`Script` look up.
    """

    def __init__(self, tsv: str) -> None:
        self.date: Optional[str] = None
        self._languages: Dict[str, Language] = {}
        self._families: Dict[str, LanguageFamily] = {}
        self._scripts: Dict[str, Script] = {}
        self._detector: Optional[_ScriptDetection] = None
        _read(tsv, self)
        names: Dict[str, Language] = {}
        for language in self._languages.values():
            if language.is_etymology_only:
                names[language.name] = language
        for language in self._languages.values():
            if not language.is_etymology_only:
                names[language.name] = language
        self._by_name = names
        script_names: Dict[str, Script] = {}
        for script in self._scripts.values():
            if script._parent_code is not None:
                script_names[script.name] = script
        for script in self._scripts.values():
            if script._parent_code is None:
                script_names[script.name] = script
        self._scripts_by_name = script_names
        by_family: Dict[str, List[Language]] = {}
        for language in sorted(self._languages.values(), key=lambda it: it.code):
            if language._family_code:
                by_family.setdefault(language._family_code, []).append(language)
        self._by_family = by_family

    @staticmethod
    @functools.lru_cache(maxsize=None)
    def bundled() -> "Catalog":
        """English Wiktionary's languages, families and scripts as the package was built with them."""
        text = resources.files("polyglossa").joinpath("catalog.tsv").read_text(encoding="utf-8")
        return Catalog(text)

    @property
    def languages(self) -> List[Language]:
        """Every language and etymology-only variety, sorted by code."""
        return sorted(self._languages.values(), key=lambda it: it.code)

    @property
    def families(self) -> List[LanguageFamily]:
        """Every family, sorted by code."""
        return sorted(self._families.values(), key=lambda it: it.code)

    @property
    def scripts(self) -> List[Script]:
        """Every script, sorted by code."""
        return sorted(self._scripts.values(), key=lambda it: it.code)

    def get(self, code: Optional[str]) -> Optional[Language]:
        """The language with ``code``, or ``None``."""
        return self._languages.get(code) if code is not None else None

    def __getitem__(self, code: str) -> Language:
        language = self.get(code)
        if language is None:
            raise UnknownLanguageError(code)
        return language

    def __contains__(self, code: object) -> bool:
        return isinstance(code, str) and code in self._languages

    def named(self, name: str) -> Optional[Language]:
        """The language called ``name``; a full language wins over an etymology-only variety of the same name."""
        return self._by_name.get(name.strip())

    def family(self, code: Optional[str]) -> Optional[LanguageFamily]:
        """The family with ``code``, or ``None``."""
        return self._families.get(code) if code is not None else None

    def script(self, code: Optional[str]) -> Optional[Script]:
        """The script with ``code``, or ``None``."""
        return self._scripts.get(code) if code is not None else None

    def script_named(self, name: str) -> Optional[Script]:
        """The script called ``name``; one that is no variety of another wins over one that is."""
        return self._scripts_by_name.get(name.strip())

    def detect_script(self, text: str) -> Optional[Script]:
        """The script ``text`` is written in, whatever its language, as Wiktionary's ``findBestScriptWithoutLang``
        finds it: the one most of its characters belong to, or ``None`` when more of them belong to no script. A
        tie goes to a script that holds no other whole, as Greek does beside Polytonic Greek, then to a script over
        notation such as ``Zmth``, then to the one with the most characters, as Han is beside Latin, then to one
        that is no variety of another.
        :meth:`Language.detect_script` knows better when the language is known.
        """
        return self._detection().detect(text)

    def _detection(self) -> "_ScriptDetection":
        if self._detector is None:
            self._detector = _ScriptDetection(self._scripts)
        return self._detector

    def _in_family(self, code: str) -> List[Language]:
        return list(self._by_family.get(code, []))


_HAN = ("Hani", "Hant", "Hans")
_HANTS = "Hants"
_COMBINED = (("Jpan", ("Hani", "Hira", "Kana")), ("Kore", ("Hani", "Hang")))
_PUNCTUATION = frozenset(ord(char) for char in "!\"#%&'()*,-./:;?@[\\]_{}")
_SPACES = (range(0x9, 0xE), range(0x20, 0x21), range(0xA0, 0xA1), range(0x1680, 0x1681), range(0x2000, 0x200B),
           range(0x2028, 0x202A), range(0x202F, 0x2030), range(0x205F, 0x2060), range(0x3000, 0x3001))


class _ScriptDetection:
    """How Wiktionary tells which script a text is in, as the Kotlin library does: ``Language:findBestScript``
    in ``Module:languages`` for a language, and ``findBestScriptWithoutLang`` for none."""

    def __init__(self, scripts: Dict[str, Script]) -> None:
        combined = {code for code, _ in _COMBINED}
        self._scripts = scripts
        indexed = [s for s in scripts.values() if s.ranges and s.code not in combined]
        self._sizes = {s.code: sum(len(r) for r in s.ranges) for s in indexed}
        self._supersets = {
            outer.code for outer in indexed
            if any(inner.ranges != outer.ranges and _covers(outer, inner) for inner in indexed)
        }
        self._starts = sorted({bound for s in indexed for r in s.ranges for bound in (r.start, r.stop)})
        index = {start: at for at, start in enumerate(self._starts)}
        holders: List[List[Script]] = [[] for _ in self._starts]
        for script in indexed:
            for r in script.ranges:
                for at in range(index[r.start], index[r.stop]):
                    holders[at].append(script)
        self._holders = holders

    def _holding(self, code_point: int) -> List[Script]:
        at = bisect.bisect_right(self._starts, code_point) - 1
        return self._holders[at] if at >= 0 else []

    def _size(self, script: Script) -> int:
        if script.code in self._sizes:
            return self._sizes[script.code]
        parts = dict(_COMBINED).get(script.code, ())
        return sum(self._sizes.get(part, 0) for part in parts)

    def detect(self, text: str) -> Optional[Script]:
        counts: Dict[str, int] = {}
        combined = [0] * len(_COMBINED)
        unmatched = 0
        for char in text:
            found = self._holding(ord(char))
            if not found:
                unmatched += 1
            for script in found:
                counts[script.code] = counts.get(script.code, 0) + 1
            for at, (_, parts) in enumerate(_COMBINED):
                if any(script.code in parts for script in found):
                    combined[at] += 1
        for at, (code, parts) in enumerate(_COMBINED):
            shares = [counts.get(part, 0) for part in parts]
            if code in self._scripts and combined[at] not in shares:
                counts[code] = combined[at]
        ranked = sorted(
            ((self._scripts[code], count) for code, count in counts.items() if code in self._scripts),
            key=lambda it: (-it[1], it[0].code in self._supersets, _is_notation(it[0]), -self._size(it[0]),
                            it[0]._parent_code is not None, it[0].code),
        )
        if not ranked or ranked[0][1] < unmatched:
            return None
        return ranked[0][0]

    def detect_in(self, language: Language, text: str) -> Optional[Script]:
        codes = self._script_codes(language)
        if not codes:
            return None
        if len(codes) >= len(self._scripts) and set(codes) >= set(self._scripts):
            return self.detect(text)
        points = [ord(char) for char in text if not _ignored(ord(char))]
        if not points:
            return None
        best: Optional[Script] = None
        best_count = 0
        for code in self._groups(codes):
            if code == _HANTS:
                han = self._han(points)
                if han is not None:
                    return han
                continue
            script = self._scripts.get(code)
            if script is None:
                continue
            count = sum(1 for point in points if point in script)
            if count >= len(points):
                return script
            if count > best_count:
                best, best_count = script, count
        return best

    @staticmethod
    def _script_codes(language: Language) -> Tuple[str, ...]:
        current: Optional[Language] = language
        seen = set()
        while current is not None and current.code not in seen:
            seen.add(current.code)
            if current._script_codes:
                return current._script_codes
            current = current.parent
        return ()

    @staticmethod
    def _groups(codes: Tuple[str, ...]) -> List[str]:
        groups, at = [], 0
        while at < len(codes):
            if tuple(codes[at:at + len(_HAN)]) == _HAN:
                groups.append(_HANTS)
                at += len(_HAN)
            else:
                groups.append(codes[at])
                at += 1
        return groups

    def _han(self, points: List[int]) -> Optional[Script]:
        han = self._scripts.get("Hani")
        if han is None:
            return None
        traditional, simplified = self._scripts.get("Hant"), self._scripts.get("Hans")
        marked, found = 0, False
        for point in points:
            if point not in han:
                continue
            found = True
            is_traditional = traditional is not None and traditional._is_mark(point)
            is_simplified = simplified is not None and simplified._is_mark(point)
            if is_traditional != is_simplified:
                marked += 1 if is_traditional else -1
        if not found:
            return None
        if marked > 0:
            return traditional or han
        if marked < 0:
            return simplified or han
        return han


def _is_notation(script: Script) -> bool:
    return len(script.code) == 4 and script.code.startswith("Z")


def _covers(outer: Script, inner: Script) -> bool:
    return all(any(r.start <= x.start and x.stop <= r.stop for r in outer.ranges) for x in inner.ranges)


def _ignored(code_point: int) -> bool:
    return _holds(_SPACES, code_point) or code_point in _PUNCTUATION


def _holds(ranges: Tuple[range, ...], code_point: int) -> bool:
    """Whether one of ``ranges``, sorted and apart, holds ``code_point``."""
    at = bisect.bisect_right(ranges, code_point, key=lambda r: r.start) - 1
    return at >= 0 and code_point in ranges[at]


_TYPES = {"": LanguageType.REGULAR, "r": LanguageType.RECONSTRUCTED, "a": LanguageType.APPENDIX_CONSTRUCTED,
          "e": LanguageType.ETYMOLOGY_ONLY}
_DIRECTIONS = {"": ScriptDirection.LEFT_TO_RIGHT, "rtl": ScriptDirection.RIGHT_TO_LEFT,
               "vertical-ltr": ScriptDirection.VERTICAL_LEFT_TO_RIGHT,
               "vertical-rtl": ScriptDirection.VERTICAL_RIGHT_TO_LEFT}


def _read(text: str, catalog: Catalog) -> None:
    lines = text.split("\n")
    header = lines[0] if lines else ""
    if header != HEADER and not header.startswith(HEADER + "\t"):
        raise NotACatalogError("not a catalog Polyglossa wrote")
    catalog.date = header[len(HEADER) + 1:] or None
    for line in lines[1:]:
        if not line:
            continue
        cells = _Cells(line.split("\t"))
        kind = cells.next()
        if kind == "L":
            language = _language(cells, catalog)
            catalog._languages[language.code] = language
        elif kind == "F":
            family = _family(cells, catalog)
            catalog._families[family.code] = family
        elif kind == "S":
            script = _script(cells, catalog)
            catalog._scripts[script.code] = script
        else:
            raise NotACatalogError(f"not a language, a family or a script: {line}")


class _Cells:
    """The cells of one line, read in order; one left off the end reads as empty."""

    def __init__(self, cells: List[str]) -> None:
        self._cells = cells
        self._at = 0

    def next(self) -> str:
        cell = self._cells[self._at] if self._at < len(self._cells) else ""
        self._at += 1
        return cell


def _language(cells: _Cells, catalog: Catalog) -> Language:
    code = cells.next()
    name = _unescape(cells.next())
    mark = cells.next()
    if mark not in _TYPES:
        raise NotACatalogError(f"no language type {mark}")
    wikidata = _item(cells.next())
    family = cells.next() or None
    scripts = _codes(cells.next())
    ancestors = _codes(cells.next())
    aliases = _names(cells.next())
    parent = cells.next() or None
    varieties = []
    for variety in _split(cells.next(), ";"):
        names = [_unescape(it) for it in _split(variety, "|")]
        varieties.append(Variety(names[0], tuple(names[1:])))
    return Language(code, name, _TYPES[mark], wikidata, aliases, tuple(varieties), family, scripts, ancestors,
                    parent, catalog)


def _family(cells: _Cells, catalog: Catalog) -> LanguageFamily:
    code = cells.next()
    name = _unescape(cells.next())
    wikidata = _item(cells.next())
    parent = cells.next() or None
    proto = cells.next() or None
    aliases = _names(cells.next())
    return LanguageFamily(code, name, wikidata, aliases, parent, proto, catalog)


def _script(cells: _Cells, catalog: Catalog) -> Script:
    code = cells.next()
    name = _unescape(cells.next())
    wikidata = _item(cells.next())
    system = _unescape(cells.next()) or None
    aliases = _names(cells.next())
    parent = cells.next() or None
    direction = _DIRECTIONS.get(cells.next(), ScriptDirection.LEFT_TO_RIGHT)
    local = []
    for entry in _names(cells.next()):
        language, _, local_name = entry.partition("=")
        local.append((language, local_name))
    ranges = _ranges(cells.next())
    marks = _ranges(cells.next())
    return Script(code, name, wikidata, system, aliases, direction, ranges, marks, parent, tuple(local), catalog)


def _ranges(cell: str) -> Tuple[range, ...]:
    """The ranges of hexadecimal code points in ``cell``, as ``41-5A,AA``."""
    ranges = []
    for written in _codes(cell):
        first, _, last = written.partition("-")
        ranges.append(range(int(first, 16), int(last or first, 16) + 1))
    return tuple(ranges)


def _item(cell: str) -> Optional[str]:
    return "Q" + cell if cell else None


def _codes(cell: str) -> Tuple[str, ...]:
    return tuple(cell.split(",")) if cell else ()


def _names(cell: str) -> Tuple[str, ...]:
    return tuple(_unescape(it) for it in _split(cell, ";"))


def _split(cell: str, separator: str) -> List[str]:
    """``cell`` split at each ``separator`` that is not escaped, the pieces left escaped; none for an empty cell."""
    if not cell:
        return []
    pieces, current, escaped = [], [], False
    for character in cell:
        if escaped:
            current.append(character)
            escaped = False
        elif character == "\\":
            current.append(character)
            escaped = True
        elif character == separator:
            pieces.append("".join(current))
            current = []
        else:
            current.append(character)
    pieces.append("".join(current))
    return pieces


def _unescape(text: str) -> str:
    """``text`` with the Kotlin library's escapes undone: ``\\t`` and ``\\n``, and any other character as itself."""
    if "\\" not in text:
        return text
    out, escaped = [], False
    for character in text:
        if escaped:
            out.append({"t": "\t", "n": "\n"}.get(character, character))
            escaped = False
        elif character == "\\":
            escaped = True
        else:
            out.append(character)
    if escaped:
        out.append("\\")
    return "".join(out)
