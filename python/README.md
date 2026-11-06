# Polyglossa

English Wiktionary's languages, language families and scripts for Python: every code Wiktionary uses,
what it calls each one, and how they relate, bundled with the package so nothing is asked of the wiki.

[![PyPI](https://img.shields.io/pypi/v/polyglossa)](https://pypi.org/project/polyglossa/)
[![Python](https://img.shields.io/badge/python-3.11%2B-3776AB.svg?logo=python&logoColor=white)](https://pypi.org/project/polyglossa/)
[![CI](https://github.com/fenakhay/polyglossa/actions/workflows/ci.yml/badge.svg)](https://github.com/fenakhay/polyglossa/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/fenakhay/polyglossa/blob/main/LICENSE)
[![Data](https://img.shields.io/badge/data-CC%20BY--SA%204.0-lightgrey.svg)](https://github.com/fenakhay/polyglossa/blob/main/NOTICE)

```python
from polyglossa import Language, LanguageFamily, Script

english = Language["en"]
english.name                              # 'English'
english.family.name                       # 'Anglic'
[a.name for a in english.ancestors]       # ['Early Modern English']
english.scripts[0].name                   # 'Latin'
Language.get("xx")                        # None: no such language
Language.named("Old English").code        # 'ang'
"ang" in Language                         # True
Language["en-ear"].full                   # English, for an etymology-only variety

LanguageFamily["ine"].proto_language.name # 'Proto-Indo-European'
Script.named("Arabic").direction          # ScriptDirection.RIGHT_TO_LEFT
Script["Deva"].name_for(Language["mr"])   # 'Balbodh'
```

`Language["xx"]` raises `UnknownLanguageError`, a `KeyError`; `Language.get("xx")` returns `None`. It
needs Python 3.11 or later and nothing else.

The version is the day the data was exported from Wiktionary. The data is English Wiktionary's, under
CC BY-SA 4.0; the code is under the MIT licence. See https://github.com/fenakhay/polyglossa.
