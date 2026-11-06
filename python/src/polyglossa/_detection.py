"""How Wiktionary tells which script a text is in, as the Kotlin library does.

``Language:findBestScript`` in ``Module:languages`` for a language, and ``findBestScriptWithoutLang`` for none.
"""

from __future__ import annotations

import bisect
from dataclasses import dataclass
from typing import TYPE_CHECKING, TypeAlias

from polyglossa._entries import Script, Unknown

if TYPE_CHECKING:
    from polyglossa._entries import Language, Link

HAN = ("Hani", "Hant", "Hans")
COMBINED = (("Jpan", ("Hani", "Hira", "Kana")), ("Kore", ("Hani", "Hang")))
_PUNCTUATION = frozenset(map(ord, "!\"#%&'()*,-./:;?@[\\]_{}"))
_SPACES = frozenset(
    [*range(0x9, 0xE), 0x20, 0xA0, 0x1680, *range(0x2000, 0x200B), 0x2028, 0x2029, 0x202F, 0x205F, 0x3000]
)
_IGNORED = _PUNCTUATION | _SPACES


@dataclass(frozen=True, slots=True)
class HanGroup:
    """Han, Traditional Han and Simplified Han listed side by side, as Wiktionary's ``Hants``."""


Group: TypeAlias = HanGroup | Script | None
"""One script a language lists, ``None`` for one its catalog lacks, or the three kinds of Han together."""


def groups(links: tuple[Link[Script], ...]) -> tuple[Group, ...]:
    """The scripts in the order a language lists them, Han and its two varieties side by side taken together."""
    found: list[Group] = []
    at = 0
    while at < len(links):
        codes = tuple(_code(link) for link in links[at : at + len(HAN)])
        if codes == HAN:
            found.append(HanGroup())
            at += len(HAN)
        else:
            link = links[at]
            found.append(None if isinstance(link, Unknown) else link)
            at += 1
    return tuple(found)


def _code(link: Link[Script]) -> str:
    return link.code


@dataclass(frozen=True, slots=True)
class _Combination:
    script: Script | None
    parts: tuple[Script | None, ...]


class ScriptDetection:
    """The tables detection reads, made once for a catalog's scripts.

    Every table is indexed by a script's place in the catalog. The code points are cut into segments at every
    start and end of a script's range, so the scripts that hold a code point are those of its segment.
    """

    __slots__ = (
        "_combinations",
        "_han",
        "_holders",
        "_part_sets",
        "_rank",
        "_scripts",
        "_segments",
        "_simplified",
        "_traditional",
    )

    def __init__(self, scripts: list[Script]) -> None:
        by_code = {script.code: script for script in scripts}
        self._scripts = scripts
        self._combinations = tuple(
            _Combination(by_code.get(code), tuple(by_code.get(part) for part in parts)) for code, parts in COMBINED
        )
        self._part_sets = tuple(frozenset(p._index for p in c.parts if p is not None) for c in self._combinations)
        self._han, self._traditional, self._simplified = (by_code.get(code) for code in HAN)
        combined = {code for code, _ in COMBINED}
        indexed = [script for script in scripts if script.ranges and script.code not in combined]
        sizes = [0] * len(scripts)
        for script in indexed:
            sizes[script._index] = sum(len(r) for r in script.ranges)
        for combination in self._combinations:
            if combination.script is not None:
                sizes[combination.script._index] = sum(sizes[p._index] for p in combination.parts if p is not None)
        supersets = {
            outer._index
            for outer in indexed
            if any(inner.ranges != outer.ranges and _covers(outer.ranges, inner.ranges) for inner in indexed)
        }
        self._rank = [
            (
                script._index in supersets,
                len(script.code) == 4 and script.code.startswith("Z"),
                -sizes[script._index],
                script.is_variety,
                script.code,
            )
            for script in scripts
        ]
        self._segments = sorted({bound for script in indexed for r in script.ranges for bound in (r.start, r.stop)})
        place = {start: at for at, start in enumerate(self._segments)}
        held: list[list[int]] = [[] for _ in self._segments]
        for script in indexed:
            for r in script.ranges:
                for at in range(place[r.start], place[r.stop]):
                    held[at].append(script._index)
        self._holders = [tuple(segment) for segment in held]

    def _holding(self, code_point: int) -> tuple[int, ...]:
        at = bisect.bisect_right(self._segments, code_point) - 1
        return self._holders[at] if at >= 0 else ()

    def detect(self, text: str) -> Script | None:
        counts: dict[int, int] = {}
        together = [0] * len(self._combinations)
        unmatched = 0
        for character in text:
            found = self._holding(ord(character))
            if not found:
                unmatched += 1
                continue
            for index in found:
                counts[index] = counts.get(index, 0) + 1
            for at, parts in enumerate(self._part_sets):
                if not parts.isdisjoint(found):
                    together[at] += 1
        for combination, count in zip(self._combinations, together, strict=True):
            one_part_has_all = any(
                (0 if part is None else counts.get(part._index, 0)) == count for part in combination.parts
            )
            if combination.script is not None and not one_part_has_all:
                counts[combination.script._index] = count
        if not counts:
            return None
        best = min(counts, key=lambda index: (-counts[index], self._rank[index]))
        return self._scripts[best] if counts[best] >= unmatched else None

    def detect_in(self, language: Language, text: str) -> Script | None:
        listed = _listing(language)
        if listed is None:
            return None
        links = listed._links
        if links.groups is None:
            listed_codes = {link.code for link in links.scripts}
            links.is_written_in_every_script = all(script.code in listed_codes for script in self._scripts)
            links.groups = groups(links.scripts)
        if links.is_written_in_every_script:
            return self.detect(text)
        points = [code_point for code_point in map(ord, text) if code_point not in _IGNORED]
        if not points:
            return None
        best: Script | None = None
        best_count = 0
        for group in links.groups:
            if isinstance(group, HanGroup):
                han = self._decide_han(points)
                if han is not None:
                    return han
                continue
            if group is None:
                continue
            count = sum(1 for point in points if group._holds(point))
            if count >= len(points):
                return group
            if count > best_count:
                best, best_count = group, count
        return best

    def _decide_han(self, points: list[int]) -> Script | None:
        """Traditional or Simplified Han when more Han characters mark one, Han when as many mark each."""
        han = self._han
        if han is None:
            return None
        found = False
        traditional = simplified = 0
        for point in points:
            if not han._holds(point):
                continue
            found = True
            is_traditional = self._traditional is not None and self._traditional._is_mark(point)
            is_simplified = self._simplified is not None and self._simplified._is_mark(point)
            if is_traditional and not is_simplified:
                traditional += 1
            elif is_simplified and not is_traditional:
                simplified += 1
        if not found:
            return None
        if traditional > simplified:
            return self._traditional or han
        if simplified > traditional:
            return self._simplified or han
        return han


def _listing(language: Language) -> Language | None:
    """The language itself when it lists scripts, or else the nearest of its parents that does."""
    for current in language._lineage():
        if current._links.scripts:
            return current
    return None


def _covers(outer: tuple[range, ...], inner: tuple[range, ...]) -> bool:
    return all(any(r.start <= x.start and x.stop <= r.stop for r in outer) for x in inner)
