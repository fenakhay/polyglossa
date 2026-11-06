# polyglossa

English Wiktionary's languages, language families and scripts for TypeScript and JavaScript: every code
Wiktionary uses, what it calls each one, and how they relate, bundled with the package so nothing is asked
of the wiki.

[![npm](https://img.shields.io/npm/v/polyglossa)](https://www.npmjs.com/package/polyglossa)
[![CI](https://github.com/fenakhay/polyglossa/actions/workflows/ci.yml/badge.svg)](https://github.com/fenakhay/polyglossa/actions/workflows/ci.yml)
[![Docs](https://img.shields.io/badge/docs-docs.fenakhay.com%2Fpolyglossa-0A74B8.svg)](https://docs.fenakhay.com/polyglossa/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/fenakhay/polyglossa/blob/main/LICENSE)
[![Data](https://img.shields.io/badge/data-CC%20BY--SA%204.0-lightgrey.svg)](https://github.com/fenakhay/polyglossa/blob/main/NOTICE)

```ts
import { Language, LanguageFamily, Script } from "polyglossa";

const english = Language.of("en");
english.name                                     // "English"
english.family?.name                             // "Anglic"
english.ancestors.map((a) => a.name)             // ["Early Modern English"]
english.scripts[0]?.name                         // "Latin"
Language.get("xx")                               // null: no such language
Language.named("Old English")?.code              // "ang"
Language.has("ang")                              // true
Language.of("en-ear").full                       // English, for an etymology-only variety

LanguageFamily.of("ine").protoLanguage?.name     // "Proto-Indo-European"
Script.named("Arabic")?.direction                // "rtl"
Script.of("Deva").nameFor(Language.of("mr"))     // "Balbodh"
Language.of("zh").detectScript("語言")?.code      // "Hant"
```

`Language.of("xx")` throws `UnknownLanguageError`; `Language.get("xx")` returns `null`. It is an ES module
with its types included and no dependencies, for Node 22 and up, Deno, Bun and browsers.

The version is the day the data was exported from Wiktionary. The data is English Wiktionary's, under
CC BY-SA 4.0; the code is under the MIT licence. The documentation, with every example in TypeScript, is at
https://docs.fenakhay.com/polyglossa/.
