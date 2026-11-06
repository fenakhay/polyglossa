// Package polyglossa holds English Wiktionary's languages, language families and scripts: every code
// Wiktionary uses, what it calls each one, and how they relate, built into the package so nothing is asked of
// the wiki.
//
// The package's functions look up [Bundled], the catalog it is built with; [ParseCatalog] reads another.
package polyglossa

import (
	_ "embed"
	"maps"
	"slices"
	"strings"
	"sync"
)

//go:embed catalog.tsv
var bundledText string

var bundled = sync.OnceValue(func() *Catalog {
	catalog, err := ParseCatalog(bundledText)
	if err != nil {
		panic("polyglossa: the bundled catalog cannot be read: " + err.Error())
	}
	return catalog
})

// Catalog is a set of languages, families and scripts that know one another.
type Catalog struct {
	date          string
	languages     []*Language
	families      []*LanguageFamily
	scripts       []*Script
	languageCodes map[string]*Language
	familyCodes   map[string]*LanguageFamily
	scriptCodes   map[string]*Script
	languageNames map[string]*Language
	scriptNames   map[string]*Script
	detection     func() *scriptDetection
}

// Bundled is English Wiktionary's languages, families and scripts as the package was built with them.
func Bundled() *Catalog { return bundled() }

// ParseCatalog reads the catalog text holds, as the Kotlin library's Catalog.toTsv writes it. When text is not
// one, the error is a [*ParseError] that says which line, and wraps [ErrNotACatalog].
func ParseCatalog(text string) (*Catalog, error) {
	raw, err := read(text)
	if err != nil {
		return nil, err
	}
	return link(raw), nil
}

// link makes the entries of raw and resolves every code one gives for another, once.
func link(raw *rawCatalog) *Catalog {
	c := &Catalog{
		date:          raw.date,
		languages:     make([]*Language, 0, len(raw.languages)),
		families:      make([]*LanguageFamily, 0, len(raw.families)),
		scripts:       make([]*Script, 0, len(raw.scripts)),
		languageCodes: make(map[string]*Language, len(raw.languages)),
		familyCodes:   make(map[string]*LanguageFamily, len(raw.families)),
		scriptCodes:   make(map[string]*Script, len(raw.scripts)),
	}
	for _, code := range slices.Sorted(maps.Keys(raw.languages)) {
		language := &Language{code: code, catalog: c}
		c.languages = append(c.languages, language)
		c.languageCodes[code] = language
	}
	for _, code := range slices.Sorted(maps.Keys(raw.families)) {
		family := &LanguageFamily{code: code, languages: []*Language{}}
		c.families = append(c.families, family)
		c.familyCodes[code] = family
	}
	for index, code := range slices.Sorted(maps.Keys(raw.scripts)) {
		script := &Script{index: index, code: code, catalog: c}
		c.scripts = append(c.scripts, script)
		c.scriptCodes[code] = script
	}
	for _, language := range c.languages {
		c.linkLanguage(language, raw.languages[language.code])
	}
	for _, family := range c.families {
		c.linkFamily(family, raw.families[family.code])
	}
	for _, script := range c.scripts {
		c.linkScript(script, raw.scripts[script.code])
	}
	isFull := func(l *Language) bool { return !l.IsEtymologyOnly() }
	isMain := func(s *Script) bool { return s.parentCode == "" }
	c.languageNames = namesBy(c.languages, (*Language).Name, isFull)
	c.scriptNames = namesBy(c.scripts, (*Script).Name, isMain)
	c.detection = sync.OnceValue(func() *scriptDetection { return newScriptDetection(c) })
	return c
}

func (c *Catalog) linkLanguage(language *Language, raw *rawLanguage) {
	language.name = raw.name
	language.languageType = raw.languageType
	language.wikidata = raw.wikidata
	language.aliases = raw.aliases
	language.varieties = raw.varieties
	language.familyCode = raw.familyCode
	language.family = c.familyCodes[raw.familyCode]
	language.scriptCodes = raw.scriptCodes
	language.scripts = make([]*Script, len(raw.scriptCodes))
	for at, code := range raw.scriptCodes {
		language.scripts[at] = c.scriptCodes[code]
	}
	language.ancestors = make([]*Language, 0, len(raw.ancestorCodes))
	for _, code := range raw.ancestorCodes {
		if ancestor := c.languageCodes[code]; ancestor != nil {
			language.ancestors = append(language.ancestors, ancestor)
		}
	}
	language.parent = c.languageCodes[raw.parentCode]
	if language.family != nil {
		language.family.languages = append(language.family.languages, language)
	}
}

func (c *Catalog) linkFamily(family *LanguageFamily, raw *rawFamily) {
	family.name = raw.name
	family.wikidata = raw.wikidata
	family.aliases = raw.aliases
	family.parent = c.familyCodes[raw.parentCode]
	protoLanguageCode := raw.protoLanguageCode
	if protoLanguageCode == "" {
		protoLanguageCode = family.code + "-pro"
	}
	family.protoLanguage = c.languageCodes[protoLanguageCode]
}

func (c *Catalog) linkScript(script *Script, raw *rawScript) {
	script.name = raw.name
	script.wikidata = raw.wikidata
	script.system = raw.system
	script.aliases = raw.aliases
	script.parentCode = raw.parentCode
	script.parent = c.scriptCodes[raw.parentCode]
	script.direction = raw.direction
	script.localNames = raw.localNames
	script.ranges = raw.ranges
	script.marks = raw.marks
}

// namesBy is each name of entries with the entry that has it, one that wins beating one that does not, and
// the later beating the earlier otherwise.
func namesBy[T any](entries []T, name func(T) string, wins func(T) bool) map[string]T {
	names := make(map[string]T, len(entries))
	for _, isWinner := range []bool{false, true} {
		for _, entry := range entries {
			if wins(entry) == isWinner {
				names[name(entry)] = entry
			}
		}
	}
	return names
}

// Date is the day Wiktionary's data was exported, as "2026-11-06", or "" when the catalog does not say.
func (c *Catalog) Date() string { return c.date }

// Languages are every language and etymology-only variety, sorted by code.
func (c *Catalog) Languages() []*Language { return slices.Clone(c.languages) }

// Families are every family, sorted by code.
func (c *Catalog) Families() []*LanguageFamily { return slices.Clone(c.families) }

// Scripts are every script, sorted by code.
func (c *Catalog) Scripts() []*Script { return slices.Clone(c.scripts) }

// Language is the language with code, or nil.
func (c *Catalog) Language(code string) *Language { return c.languageCodes[code] }

// LanguageOf is the language with code, or an error wrapping [ErrUnknownLanguage].
func (c *Catalog) LanguageOf(code string) (*Language, error) {
	if language := c.Language(code); language != nil {
		return language, nil
	}
	return nil, unknown(ErrUnknownLanguage, code)
}

// Named is the language called name; a full language wins over an etymology-only variety of the same name.
func (c *Catalog) Named(name string) *Language { return c.languageNames[strings.TrimSpace(name)] }

// Family is the family with code, or nil.
func (c *Catalog) Family(code string) *LanguageFamily { return c.familyCodes[code] }

// Script is the script with code, or nil.
func (c *Catalog) Script(code string) *Script { return c.scriptCodes[code] }

// ScriptNamed is the script called name; one that is no variety of another wins over one that is.
func (c *Catalog) ScriptNamed(name string) *Script { return c.scriptNames[strings.TrimSpace(name)] }

// DetectScript is the script text is written in, whatever its language, as Wiktionary's
// findBestScriptWithoutLang finds it: the one most of its characters belong to, or nil when more of them belong
// to no script. A tie goes to a script that holds no other whole, as Greek does beside Polytonic Greek, then to a
// script over notation such as Zmth, then to the one with the most characters, as Han is beside Latin, then to
// one that is no variety of another. [Language.DetectScript] knows better when the language is known.
func (c *Catalog) DetectScript(text string) *Script { return c.detection().detect(text) }

// LookupLanguage is the language with code, or nil when Wiktionary has none.
func LookupLanguage(code string) *Language { return Bundled().Language(code) }

// LanguageOf is the language with code, or an error wrapping [ErrUnknownLanguage] when Wiktionary has none.
func LanguageOf(code string) (*Language, error) { return Bundled().LanguageOf(code) }

// LanguageNamed is the language called name, as its section in an entry is headed, or nil.
func LanguageNamed(name string) *Language { return Bundled().Named(name) }

// Languages are every language and etymology-only variety, sorted by code.
func Languages() []*Language { return Bundled().Languages() }

// LookupFamily is the family with code, or nil when Wiktionary has none.
func LookupFamily(code string) *LanguageFamily { return Bundled().Family(code) }

// FamilyOf is the family with code, or an error wrapping [ErrUnknownFamily] when Wiktionary has none.
func FamilyOf(code string) (*LanguageFamily, error) {
	if family := LookupFamily(code); family != nil {
		return family, nil
	}
	return nil, unknown(ErrUnknownFamily, code)
}

// Families are every family, sorted by code.
func Families() []*LanguageFamily { return Bundled().Families() }

// LookupScript is the script with code, or nil when Wiktionary has none.
func LookupScript(code string) *Script { return Bundled().Script(code) }

// ScriptOf is the script with code, or an error wrapping [ErrUnknownScript] when Wiktionary has none.
func ScriptOf(code string) (*Script, error) {
	if script := LookupScript(code); script != nil {
		return script, nil
	}
	return nil, unknown(ErrUnknownScript, code)
}

// ScriptNamed is the script called name, or nil; the one without a parent when several are.
func ScriptNamed(name string) *Script { return Bundled().ScriptNamed(name) }

// Scripts are every script, sorted by code.
func Scripts() []*Script { return Bundled().Scripts() }

// DetectScript is the script text is written in, whatever its language, or nil; see [Catalog.DetectScript].
func DetectScript(text string) *Script { return Bundled().DetectScript(text) }
