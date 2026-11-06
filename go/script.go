package polyglossa

import "slices"

// ScriptDirection is which way a script runs. Its value is Wiktionary's word for it.
type ScriptDirection string

// The directions a script runs in.
const (
	LeftToRight         ScriptDirection = "ltr"
	RightToLeft         ScriptDirection = "rtl"
	VerticalLeftToRight ScriptDirection = "vertical-ltr"
	VerticalRightToLeft ScriptDirection = "vertical-rtl"
)

// Range is the code points from First to Last, both included.
type Range struct {
	First rune
	Last  rune
}

// Script is one script as English Wiktionary's Module:scripts/data describes it.
type Script struct {
	index      int
	code       string
	name       string
	wikidata   string
	system     string
	aliases    []string
	parentCode string
	parent     *Script
	direction  ScriptDirection
	localNames []localName
	ranges     []Range
	marks      []Range
	catalog    *Catalog
}

type localName struct {
	languageCode string
	name         string
}

// Code is its code, as "Latn" or "fa-Arab".
func (s *Script) Code() string { return s.code }

// Name is its name.
func (s *Script) Name() string { return s.name }

// Wikidata is its Wikidata item, or "" when it has none.
func (s *Script) Wikidata() string { return s.wikidata }

// System is what kind of writing system it is, as "alphabet", or "" when Wiktionary does not say.
func (s *Script) System() string { return s.system }

// Aliases are the other names Wiktionary gives it.
func (s *Script) Aliases() []string { return slices.Clone(s.aliases) }

// Direction is which way it runs.
func (s *Script) Direction() ScriptDirection { return s.direction }

// Ranges are its characters, as sorted ranges of code points apart from one another.
func (s *Script) Ranges() []Range { return slices.Clone(s.ranges) }

// Marks are the characters that mark a text as written in it rather than in a script it shares characters
// with: those of Traditional Han that Simplified Han writes otherwise, and the other way round.
func (s *Script) Marks() []Range { return slices.Clone(s.marks) }

// Parent is the script this is a variety of, such as Han for Simplified Han, or nil.
func (s *Script) Parent() *Script { return s.parent }

// NameFor is the name language gives this script, which is [Script.Name] unless it has its own.
func (s *Script) NameFor(language *Language) string {
	for _, local := range s.localNames {
		if local.languageCode == language.code {
			return local.name
		}
	}
	return s.name
}

// LocalNames are the names some languages give it instead of [Script.Name], such as Balbodh for Marathi's
// Devanagari.
func (s *Script) LocalNames() map[*Language]string {
	names := make(map[*Language]string, len(s.localNames))
	for _, local := range s.localNames {
		if language := s.catalog.Language(local.languageCode); language != nil {
			names[language] = local.name
		}
	}
	return names
}

// Contains is whether r is one of its characters.
func (s *Script) Contains(r rune) bool { return holds(s.ranges, r) }

// Count is how many of the characters of text are its own, counting each code point once.
func (s *Script) Count(text string) int {
	count := 0
	for _, r := range text {
		if s.Contains(r) {
			count++
		}
	}
	return count
}

// String is its name and code, as "Latin (Latn)".
func (s *Script) String() string { return s.name + " (" + s.code + ")" }

func (s *Script) isMark(r rune) bool { return holds(s.marks, r) }

// holds is whether one of ranges, sorted and apart, holds r.
func holds(ranges []Range, r rune) bool {
	_, found := slices.BinarySearchFunc(ranges, r, func(candidate Range, target rune) int {
		switch {
		case target < candidate.First:
			return 1
		case target > candidate.Last:
			return -1
		default:
			return 0
		}
	})
	return found
}
