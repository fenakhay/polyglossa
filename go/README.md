# polyglossa for Go

English Wiktionary's languages, language families and scripts for Go: every code Wiktionary uses, what it
calls each one, and how they relate, built into the module so nothing is asked of the wiki.

[![Go Reference](https://pkg.go.dev/badge/github.com/fenakhay/polyglossa/go.svg)](https://pkg.go.dev/github.com/fenakhay/polyglossa/go)
[![CI](https://github.com/fenakhay/polyglossa/actions/workflows/ci.yml/badge.svg)](https://github.com/fenakhay/polyglossa/actions/workflows/ci.yml)
[![Docs](https://img.shields.io/badge/docs-docs.fenakhay.com%2Fpolyglossa-0A74B8.svg)](https://docs.fenakhay.com/polyglossa/)

```bash
go get github.com/fenakhay/polyglossa/go
```

```go
import "github.com/fenakhay/polyglossa/go"

english := polyglossa.LookupLanguage("en")
english.Name()                                                // "English"
english.Family().Name()                                       // "Anglic"
polyglossa.LookupLanguage("xx")                               // nil: no such language
polyglossa.LanguageNamed("Old English").Code()                // "ang"
polyglossa.LookupFamily("ine").ProtoLanguage().Name()         // "Proto-Indo-European"
polyglossa.ScriptNamed("Arabic").Direction()                  // polyglossa.RightToLeft
polyglossa.LookupLanguage("zh").DetectScript("語言").Code()    // "Hant"
```

`LanguageOf("xx")` returns an error wrapping `ErrUnknownLanguage`. The module needs Go 1.23 or later and
nothing but the standard library. Its versions are `v1.` and the day the data was exported from
Wiktionary, as `v1.20261106.0`.

The data is English Wiktionary's, under CC BY-SA 4.0; the code is under the MIT licence. The documentation,
with every example in Go, is at https://docs.fenakhay.com/polyglossa/.
