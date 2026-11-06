package polyglossa

import (
	"iter"
	"slices"
)

// LanguageType is what kind of language Wiktionary takes one to be, which decides where its entries may live.
// Its value is Wiktionary's word for it.
type LanguageType string

// The kinds of language.
const (
	Regular             LanguageType = "regular"
	Reconstructed       LanguageType = "reconstructed"
	AppendixConstructed LanguageType = "appendix-constructed"
	EtymologyOnly       LanguageType = "etymology-only"
)

// Variety is a variety of a language Wiktionary names without giving it a code, such as a dialect.
type Variety struct {
	Name       string
	OtherNames []string
}

// Language is one language as English Wiktionary's Module:languages and Module:etymology languages describe it.
type Language struct {
	code         string
	name         string
	wikidata     string
	languageType LanguageType
	aliases      []string
	varieties    []Variety
	familyCode   string
	family       *LanguageFamily
	scriptCodes  []string
	scripts      []*Script
	ancestors    []*Language
	parent       *Language
	catalog      *Catalog
}

// Code is its code, as "en" or "en-US".
func (l *Language) Code() string { return l.code }

// Name is its name, as its section in an entry is headed.
func (l *Language) Name() string { return l.name }

// Type is what kind of language it is.
func (l *Language) Type() LanguageType { return l.languageType }

// Wikidata is its Wikidata item, as "Q1860", or "" when it has none.
func (l *Language) Wikidata() string { return l.wikidata }

// Aliases are the other names Wiktionary gives it.
func (l *Language) Aliases() []string { return slices.Clone(l.aliases) }

// Varieties are the varieties Wiktionary names without a code of their own.
func (l *Language) Varieties() []Variety {
	varieties := make([]Variety, 0, len(l.varieties))
	for _, variety := range l.varieties {
		varieties = append(varieties, Variety{Name: variety.Name, OtherNames: slices.Clone(variety.OtherNames)})
	}
	return varieties
}

// IsEtymologyOnly is whether it is named in etymologies only, with no section of its own in an entry.
func (l *Language) IsEtymologyOnly() bool { return l.languageType == EtymologyOnly }

// Family is the family it belongs to, or nil; an etymology-only variety with none of its own has that of the
// nearest of its parents that has one, as American English has English's through North American English.
func (l *Language) Family() *LanguageFamily {
	for language := range l.lineage() {
		if language.familyCode != "" {
			return language.family
		}
	}
	return nil
}

// Scripts are the scripts it is written in, most usual first, those the catalog knows.
func (l *Language) Scripts() []*Script {
	scripts := make([]*Script, 0, len(l.scripts))
	for _, script := range l.scripts {
		if script != nil {
			scripts = append(scripts, script)
		}
	}
	return scripts
}

// Ancestors are the languages it descends from, those the catalog knows.
func (l *Language) Ancestors() []*Language { return slices.Clone(l.ancestors) }

// Parent is the language an etymology-only variety belongs to, or nil for any other.
func (l *Language) Parent() *Language { return l.parent }

// Full is the full language this stands for: itself, or for an etymology-only variety the one it belongs to.
func (l *Language) Full() *Language {
	full := l
	for language := range l.lineage() {
		full = language
		if !language.IsEtymologyOnly() {
			break
		}
	}
	return full
}

// DetectScript is the script text is written in, among this language's [Language.Scripts], as Wiktionary's
// findBestScript finds it, or nil when none of them has any of its characters.
//
// Spaces and ASCII punctuation are left out, and the scripts are tried in order: the first that has every
// character wins, or else the one with the most of them, the earlier on a tie. Chinese and the other languages
// written in Han give Traditional or Simplified Han when more of the characters mark one than the other, and Han
// when as many mark each. A language written in every script finds it as [Catalog.DetectScript] does, and an
// etymology-only variety with no scripts of its own uses its language's. Pass plain text: wiki markup counts as
// characters.
func (l *Language) DetectScript(text string) *Script { return l.catalog.detection().detectIn(l, text) }

// String is its name and code, as "English (en)".
func (l *Language) String() string { return l.name + " (" + l.code + ")" }

// lineage is the language, then its parent, its parent's parent and so on, each once.
func (l *Language) lineage() iter.Seq[*Language] {
	return func(yield func(*Language) bool) {
		var seen [8]*Language
		visited := seen[:0]
		for language := l; language != nil && !slices.Contains(visited, language); language = language.parent {
			visited = append(visited, language)
			if !yield(language) {
				return
			}
		}
	}
}
