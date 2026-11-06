package polyglossa

import (
	"fmt"
	"strconv"
	"strings"
)

const header = "polyglossa\t1"

var languageTypes = map[string]LanguageType{
	"":  Regular,
	"r": Reconstructed,
	"a": AppendixConstructed,
	"e": EtymologyOnly,
}

var directions = map[string]ScriptDirection{
	"rtl":          RightToLeft,
	"vertical-ltr": VerticalLeftToRight,
	"vertical-rtl": VerticalRightToLeft,
}

// rawCatalog is a catalog as the text has it: each entry under its code, the later of two with the same code
// winning, and the codes that link entries not yet resolved.
type rawCatalog struct {
	date      string
	languages map[string]*rawLanguage
	families  map[string]*rawFamily
	scripts   map[string]*rawScript
}

type rawLanguage struct {
	name          string
	languageType  LanguageType
	wikidata      string
	familyCode    string
	scriptCodes   []string
	ancestorCodes []string
	aliases       []string
	parentCode    string
	varieties     []Variety
}

type rawFamily struct {
	name              string
	wikidata          string
	parentCode        string
	protoLanguageCode string
	aliases           []string
}

type rawScript struct {
	name       string
	wikidata   string
	system     string
	aliases    []string
	parentCode string
	direction  ScriptDirection
	localNames []localName
	ranges     []Range
	marks      []Range
}

// read reads text, a catalog as the Kotlin library's Catalog.toTsv writes it.
func read(text string) (*rawCatalog, error) {
	first, rest, _ := strings.Cut(text, "\n")
	date, isCatalog := strings.CutPrefix(first, header)
	if !isCatalog || (date != "" && date[0] != '\t') {
		return nil, &ParseError{Line: 1, Reason: "no header"}
	}
	catalog := &rawCatalog{
		date:      strings.TrimPrefix(date, "\t"),
		languages: map[string]*rawLanguage{},
		families:  map[string]*rawFamily{},
		scripts:   map[string]*rawScript{},
	}
	number := 1
	for _, line := range strings.Split(rest, "\n") {
		number++
		if line == "" {
			continue
		}
		if err := catalog.add(line); err != nil {
			return nil, &ParseError{Line: number, Reason: err.Error()}
		}
	}
	return catalog, nil
}

func (c *rawCatalog) add(line string) error {
	row := &cells{rest: line}
	kind := row.next()
	code := row.next()
	switch kind {
	case "L":
		language, err := readLanguage(row)
		if err != nil {
			return err
		}
		c.languages[code] = language
	case "F":
		c.families[code] = readFamily(row)
	case "S":
		script, err := readScript(row)
		if err != nil {
			return err
		}
		c.scripts[code] = script
	default:
		return fmt.Errorf("not a language, a family or a script: %q", kind)
	}
	return nil
}

// cells are the cells of one line, read in order; one left off the end reads as empty.
type cells struct {
	rest string
}

func (c *cells) next() string {
	cell, rest, _ := strings.Cut(c.rest, "\t")
	c.rest = rest
	return cell
}

func readLanguage(row *cells) (*rawLanguage, error) {
	name := unescape(row.next())
	mark := row.next()
	languageType, isKnown := languageTypes[mark]
	if !isKnown {
		return nil, fmt.Errorf("no language type %q", mark)
	}
	language := &rawLanguage{
		name:          name,
		languageType:  languageType,
		wikidata:      item(row.next()),
		familyCode:    row.next(),
		scriptCodes:   codes(row.next()),
		ancestorCodes: codes(row.next()),
		aliases:       names(row.next()),
		parentCode:    row.next(),
		varieties:     []Variety{},
	}
	for _, written := range split(row.next(), ';') {
		pieces := split(written, '|')
		for at, piece := range pieces {
			pieces[at] = unescape(piece)
		}
		language.varieties = append(language.varieties, Variety{Name: pieces[0], OtherNames: pieces[1:]})
	}
	return language, nil
}

func readFamily(row *cells) *rawFamily {
	return &rawFamily{
		name:              unescape(row.next()),
		wikidata:          item(row.next()),
		parentCode:        row.next(),
		protoLanguageCode: row.next(),
		aliases:           names(row.next()),
	}
}

func readScript(row *cells) (*rawScript, error) {
	script := &rawScript{
		name:       unescape(row.next()),
		wikidata:   item(row.next()),
		system:     unescape(row.next()),
		aliases:    names(row.next()),
		parentCode: row.next(),
		direction:  LeftToRight,
		localNames: []localName{},
	}
	if direction, isKnown := directions[row.next()]; isKnown {
		script.direction = direction
	}
	for _, entry := range names(row.next()) {
		languageCode, name, _ := strings.Cut(entry, "=")
		script.localNames = append(script.localNames, localName{languageCode: languageCode, name: name})
	}
	var err error
	if script.ranges, err = codePointRanges(row.next()); err != nil {
		return nil, err
	}
	if script.marks, err = codePointRanges(row.next()); err != nil {
		return nil, err
	}
	return script, nil
}

// codePointRanges are the ranges of hexadecimal code points in cell, as "41-5A,AA".
func codePointRanges(cell string) ([]Range, error) {
	written := codes(cell)
	ranges := make([]Range, 0, len(written))
	for _, one := range written {
		first, last, isRange := strings.Cut(one, "-")
		if !isRange {
			last = first
		}
		start, err := codePoint(first)
		if err != nil {
			return nil, err
		}
		end, err := codePoint(last)
		if err != nil {
			return nil, err
		}
		ranges = append(ranges, Range{First: start, Last: end})
	}
	return ranges, nil
}

func codePoint(hex string) (rune, error) {
	value, err := strconv.ParseInt(hex, 16, 32)
	if err != nil {
		return 0, fmt.Errorf("no code point %q", hex)
	}
	return rune(value), nil
}

func item(cell string) string {
	if cell == "" {
		return ""
	}
	return "Q" + cell
}

func codes(cell string) []string {
	if cell == "" {
		return []string{}
	}
	return strings.Split(cell, ",")
}

func names(cell string) []string {
	pieces := split(cell, ';')
	for at, piece := range pieces {
		pieces[at] = unescape(piece)
	}
	return pieces
}

// split is cell split at each separator that is not escaped, the pieces left escaped; none for an empty cell.
func split(cell string, separator byte) []string {
	pieces := []string{}
	if cell == "" {
		return pieces
	}
	start := 0
	for at := 0; at < len(cell); at++ {
		switch cell[at] {
		case '\\':
			at++
		case separator:
			pieces = append(pieces, cell[start:at])
			start = at + 1
		}
	}
	return append(pieces, cell[start:])
}

// unescape is text with the Kotlin library's escapes undone: \t and \n, and any other character as itself.
func unescape(text string) string {
	if !strings.Contains(text, "\\") {
		return text
	}
	var out strings.Builder
	out.Grow(len(text))
	isEscaped := false
	for _, r := range text {
		switch {
		case isEscaped && r == 't':
			out.WriteRune('\t')
		case isEscaped && r == 'n':
			out.WriteRune('\n')
		case isEscaped:
			out.WriteRune(r)
		case r == '\\':
			isEscaped = true
			continue
		default:
			out.WriteRune(r)
		}
		isEscaped = false
	}
	if isEscaped {
		out.WriteRune('\\')
	}
	return out.String()
}
