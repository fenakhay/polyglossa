package polyglossa

import "slices"

// LanguageFamily is one language family as English Wiktionary's Module:families describes it.
type LanguageFamily struct {
	code          string
	name          string
	wikidata      string
	aliases       []string
	parent        *LanguageFamily
	protoLanguage *Language
	languages     []*Language
}

// Code is its code, as "gmw".
func (f *LanguageFamily) Code() string { return f.code }

// Name is its name.
func (f *LanguageFamily) Name() string { return f.name }

// Wikidata is its Wikidata item, or "" when it has none.
func (f *LanguageFamily) Wikidata() string { return f.wikidata }

// Aliases are the other names Wiktionary gives it.
func (f *LanguageFamily) Aliases() []string { return slices.Clone(f.aliases) }

// Parent is the family it belongs to, or nil.
func (f *LanguageFamily) Parent() *LanguageFamily { return f.parent }

// ProtoLanguage is the language it descends from: the one Wiktionary names, or else "<code>-pro" when there is
// one.
func (f *LanguageFamily) ProtoLanguage() *Language { return f.protoLanguage }

// Languages are the languages in it directly, not those of the families under it, sorted by code.
func (f *LanguageFamily) Languages() []*Language { return slices.Clone(f.languages) }

// String is its name and code, as "West Germanic (gmw)".
func (f *LanguageFamily) String() string { return f.name + " (" + f.code + ")" }
