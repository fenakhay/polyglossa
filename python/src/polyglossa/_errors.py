"""The errors polyglossa raises."""

from __future__ import annotations


class PolyglossaError(Exception):
    """What every error polyglossa raises is."""


class _UnknownCodeError(PolyglossaError, KeyError):
    """A lookup by a code the catalog does not have; a :class:`KeyError`, as ``Language["xx"]`` reads."""

    _what = "code"

    def __init__(self, code: str) -> None:
        super().__init__(code)
        self.code = code

    def __str__(self) -> str:
        return f"polyglossa: unknown {self._what} {self.code!r}"


class UnknownLanguageError(_UnknownCodeError):
    """Wiktionary has no language with this code.

    Attributes:
        code: The code that was looked up.
    """

    _what = "language"


class UnknownFamilyError(_UnknownCodeError):
    """Wiktionary has no family with this code.

    Attributes:
        code: The code that was looked up.
    """

    _what = "family"


class UnknownScriptError(_UnknownCodeError):
    """Wiktionary has no script with this code.

    Attributes:
        code: The code that was looked up.
    """

    _what = "script"


class NotACatalogError(PolyglossaError, ValueError):
    """The text is not a catalog polyglossa wrote.

    Attributes:
        line: The line, counted from 1, that could not be read.
        reason: What is wrong with it.
    """

    def __init__(self, line: int, reason: str) -> None:
        super().__init__(f"polyglossa: not a catalog polyglossa wrote: line {line}: {reason}")
        self.line = line
        self.reason = reason
