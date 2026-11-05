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

from polyglossa._catalog import (
    Catalog,
    Language,
    LanguageFamily,
    LanguageType,
    NotACatalogError,
    Script,
    ScriptDirection,
    UnknownFamilyError,
    UnknownLanguageError,
    UnknownScriptError,
    Variety,
)
from polyglossa._version import __version__

__all__ = [
    "Catalog",
    "Language",
    "LanguageFamily",
    "LanguageType",
    "NotACatalogError",
    "Script",
    "ScriptDirection",
    "UnknownFamilyError",
    "UnknownLanguageError",
    "UnknownScriptError",
    "Variety",
    "__version__",
]
