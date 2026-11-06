"""Reads the tab-separated ``catalog.tsv`` the Kotlin library writes, into entries not yet linked."""

from __future__ import annotations

import re
from dataclasses import dataclass, field

from polyglossa._errors import NotACatalogError
from polyglossa._kinds import LanguageType, ScriptDirection, Variety

HEADER = "polyglossa\t1"

_LANGUAGE_TYPES = {
    "": LanguageType.REGULAR,
    "r": LanguageType.RECONSTRUCTED,
    "a": LanguageType.APPENDIX_CONSTRUCTED,
    "e": LanguageType.ETYMOLOGY_ONLY,
}
_DIRECTIONS = {
    "": ScriptDirection.LEFT_TO_RIGHT,
    "rtl": ScriptDirection.RIGHT_TO_LEFT,
    "vertical-ltr": ScriptDirection.VERTICAL_LEFT_TO_RIGHT,
    "vertical-rtl": ScriptDirection.VERTICAL_RIGHT_TO_LEFT,
}
_RANGES = re.compile(r"(?:[0-9A-Fa-f]+(?:-[0-9A-Fa-f]+)?(?:,(?=.)|$))*")


@dataclass(slots=True)
class RawLanguage:
    """A language as the text has it, the codes it gives for other entries not yet resolved."""

    name: str
    type: LanguageType
    wikidata: str | None
    family: str | None
    scripts: tuple[str, ...]
    ancestors: tuple[str, ...]
    aliases: tuple[str, ...]
    parent: str | None
    varieties: tuple[Variety, ...]


@dataclass(slots=True)
class RawFamily:
    """A family as the text has it."""

    name: str
    wikidata: str | None
    parent: str | None
    proto_language: str | None
    aliases: tuple[str, ...]


@dataclass(slots=True)
class RawScript:
    """A script as the text has it."""

    name: str
    wikidata: str | None
    system: str | None
    aliases: tuple[str, ...]
    parent: str | None
    direction: ScriptDirection
    local_names: tuple[tuple[str, str], ...]
    ranges: tuple[range, ...]
    marks: tuple[range, ...]


@dataclass(slots=True)
class RawCatalog:
    """A catalog as the text has it: each entry under its code, the later of two with the same code winning."""

    date: str | None
    languages: dict[str, RawLanguage] = field(default_factory=dict)
    families: dict[str, RawFamily] = field(default_factory=dict)
    scripts: dict[str, RawScript] = field(default_factory=dict)


def read(text: str) -> RawCatalog:
    """Reads ``text``, a catalog as the Kotlin library's ``Catalog.toTsv`` writes it.

    Raises:
        NotACatalogError: With the line, when ``text`` is not such a catalog.
    """
    lines = text.split("\n")
    header = lines[0]
    if header != HEADER and not header.startswith(HEADER + "\t"):
        raise NotACatalogError(1, "no header")
    catalog = RawCatalog(date=header[len(HEADER) + 1 :] or None)
    for number, line in enumerate(lines[1:], start=2):
        if not line:
            continue
        cells = line.split("\t")
        cells += [""] * (_CELLS - len(cells))
        kind, code = cells[0], cells[1]
        try:
            if kind == "L":
                catalog.languages[code] = _language(cells)
            elif kind == "F":
                catalog.families[code] = _family(cells)
            elif kind == "S":
                catalog.scripts[code] = _script(cells)
            else:
                raise ValueError(f"not a language, a family or a script: {kind!r}")
        except ValueError as error:
            raise NotACatalogError(number, str(error)) from error
    return catalog


_CELLS = 12
"""The most cells a line has; one left off the end reads as empty."""


def _language(cells: list[str]) -> RawLanguage:
    _, _, name, mark, wikidata, family, scripts, ancestors, aliases, parent, varieties, *_ = cells
    language_type = _LANGUAGE_TYPES.get(mark)
    if language_type is None:
        raise ValueError(f"no language type {mark!r}")
    return RawLanguage(
        name=_unescape(name),
        type=language_type,
        wikidata=_item(wikidata),
        family=family or None,
        scripts=_codes(scripts),
        ancestors=_codes(ancestors),
        aliases=_names(aliases),
        parent=parent or None,
        varieties=tuple(_variety(written) for written in _split(varieties, ";")) if varieties else (),
    )


def _variety(written: str) -> Variety:
    name, *other_names = (_unescape(piece) for piece in _split(written, "|"))
    return Variety(name, tuple(other_names))


def _family(cells: list[str]) -> RawFamily:
    _, _, name, wikidata, parent, proto_language, aliases, *_ = cells
    return RawFamily(
        name=_unescape(name),
        wikidata=_item(wikidata),
        parent=parent or None,
        proto_language=proto_language or None,
        aliases=_names(aliases),
    )


def _script(cells: list[str]) -> RawScript:
    _, _, name, wikidata, system, aliases, parent, word, local_names, ranges, marks, *_ = cells
    direction = _DIRECTIONS.get(word)
    if direction is None:
        raise ValueError(f"no script direction {word!r}")
    return RawScript(
        name=_unescape(name),
        wikidata=_item(wikidata),
        system=_unescape(system) or None,
        aliases=_names(aliases),
        parent=parent or None,
        direction=direction,
        local_names=tuple(_local_name(entry) for entry in _names(local_names)),
        ranges=_ranges(ranges),
        marks=_ranges(marks),
    )


def _local_name(entry: str) -> tuple[str, str]:
    language, _, local_name = entry.partition("=")
    return language, local_name


def _ranges(cell: str) -> tuple[range, ...]:
    """The ranges of hexadecimal code points in ``cell``, as ``41-5A,AA``."""
    if not _RANGES.fullmatch(cell):
        raise ValueError(f"no code points {cell!r}")
    ranges = []
    for written in _codes(cell):
        first, _, last = written.partition("-")
        ranges.append(range(int(first, 16), int(last or first, 16) + 1))
    return tuple(ranges)


def _item(cell: str) -> str | None:
    return "Q" + cell if cell else None


def _codes(cell: str) -> tuple[str, ...]:
    return tuple(cell.split(",")) if cell else ()


def _names(cell: str) -> tuple[str, ...]:
    if not cell:
        return ()
    if "\\" not in cell:
        return tuple(cell.split(";"))
    return tuple(_unescape(piece) for piece in _split(cell, ";"))


def _split(cell: str, separator: str) -> list[str]:
    """``cell`` split at each ``separator`` that is not escaped, the pieces left escaped; none for an empty cell."""
    if not cell:
        return []
    if "\\" not in cell:
        return cell.split(separator)
    pieces = []
    start = 0
    at = 0
    while at < len(cell):
        character = cell[at]
        if character == "\\":
            at += 1
        elif character == separator:
            pieces.append(cell[start:at])
            start = at + 1
        at += 1
    pieces.append(cell[start:])
    return pieces


_ESCAPE = re.compile(r"\\(.?)", re.DOTALL)
_ESCAPED = {"t": "\t", "n": "\n", "": "\\"}


def _unescape(text: str) -> str:
    """``text`` with the Kotlin library's escapes undone: ``\\t`` and ``\\n``, and any other character as itself."""
    if "\\" not in text:
        return text
    return _ESCAPE.sub(lambda match: _ESCAPED.get(match.group(1), match.group(1)), text)
