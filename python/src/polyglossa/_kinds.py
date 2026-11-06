"""The kinds of language and the directions of script, and a language's unnamed varieties."""

from __future__ import annotations

import enum
from dataclasses import dataclass


class LanguageType(enum.StrEnum):
    """What kind of language Wiktionary takes one to be, which decides where its entries may live.

    Each value is Wiktionary's word for it, so a member equals that word: ``LanguageType.REGULAR == "regular"``.
    """

    REGULAR = "regular"
    RECONSTRUCTED = "reconstructed"
    APPENDIX_CONSTRUCTED = "appendix-constructed"
    ETYMOLOGY_ONLY = "etymology-only"


class ScriptDirection(enum.StrEnum):
    """Which way a script runs, its value Wiktionary's word for it."""

    LEFT_TO_RIGHT = "ltr"
    RIGHT_TO_LEFT = "rtl"
    VERTICAL_LEFT_TO_RIGHT = "vertical-ltr"
    VERTICAL_RIGHT_TO_LEFT = "vertical-rtl"


@dataclass(frozen=True, slots=True)
class Variety:
    """A variety of a language Wiktionary names without giving it a code, such as a dialect.

    Attributes:
        name: Its name.
        other_names: The other names it goes by.
    """

    name: str
    other_names: tuple[str, ...] = ()

    def __str__(self) -> str:
        return self.name if not self.other_names else f"{self.name} ({', '.join(self.other_names)})"
