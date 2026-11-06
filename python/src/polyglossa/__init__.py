"""English Wiktionary's languages, language families and scripts.

>>> english = Language["en"]
>>> english.name
'English'
>>> english.family.name
'Anglic'
>>> Language.get("xx") is None
True
>>> "ang" in Language
True
"""

from polyglossa._catalog import Catalog
from polyglossa._entries import Language, LanguageFamily, Script
from polyglossa._errors import (
    NotACatalogError,
    PolyglossaError,
    UnknownFamilyError,
    UnknownLanguageError,
    UnknownScriptError,
)
from polyglossa._kinds import LanguageType, ScriptDirection, Variety
from polyglossa._version import __version__

__all__ = [
    "Catalog",
    "Language",
    "LanguageFamily",
    "LanguageType",
    "NotACatalogError",
    "PolyglossaError",
    "Script",
    "ScriptDirection",
    "UnknownFamilyError",
    "UnknownLanguageError",
    "UnknownScriptError",
    "Variety",
    "__version__",
]
