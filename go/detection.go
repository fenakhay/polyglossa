package polyglossa

import (
	"cmp"
	"slices"
)

var hanCodes = []string{"Hani", "Hant", "Hans"}

var combinedCodes = []struct {
	code  string
	parts []string
}{
	{code: "Jpan", parts: []string{"Hani", "Hira", "Kana"}},
	{code: "Kore", parts: []string{"Hani", "Hang"}},
}

// combination is a script made of others, as Japanese is of Han, Hiragana and Katakana, which a text is written
// in when its characters are of more than one of them. A part the catalog lacks is nil.
type combination struct {
	script *Script
	parts  []*Script
}

// scriptDetection is how Wiktionary tells which script a text is in, as the Kotlin library does:
// Language:findBestScript in Module:languages for a language, and findBestScriptWithoutLang for none.
//
// Its tables are indexed by a script's place in the catalog. The code points are cut into segments at every
// start and end of a script's range, so the scripts that hold a code point are those of its segment:
// holders[offsets[i]:offsets[i+1]] for segment i, which starts at segments[i].
type scriptDetection struct {
	catalog      *Catalog
	sizes        []int
	isSuperset   []bool
	segments     []rune
	offsets      []int
	holders      []*Script
	combinations []combination
	han          *Script
	traditional  *Script
	simplified   *Script
}

func newScriptDetection(c *Catalog) *scriptDetection {
	d := &scriptDetection{
		catalog:     c,
		sizes:       make([]int, len(c.scripts)),
		isSuperset:  make([]bool, len(c.scripts)),
		han:         c.scriptCodes[hanCodes[0]],
		traditional: c.scriptCodes[hanCodes[1]],
		simplified:  c.scriptCodes[hanCodes[2]],
	}
	isCombined := map[string]bool{}
	for _, combined := range combinedCodes {
		isCombined[combined.code] = true
		parts := make([]*Script, len(combined.parts))
		for at, code := range combined.parts {
			parts[at] = c.scriptCodes[code]
		}
		d.combinations = append(d.combinations, combination{script: c.scriptCodes[combined.code], parts: parts})
	}
	indexed := make([]*Script, 0, len(c.scripts))
	for _, script := range c.scripts {
		if len(script.ranges) > 0 && !isCombined[script.code] {
			indexed = append(indexed, script)
		}
	}
	d.measure(indexed)
	d.segment(indexed)
	return d
}

// measure counts each script's characters, a combination's as the sum of its parts', and marks the scripts
// that hold another whole.
func (d *scriptDetection) measure(indexed []*Script) {
	for _, script := range indexed {
		for _, r := range script.ranges {
			d.sizes[script.index] += int(r.Last-r.First) + 1
		}
	}
	for _, combined := range d.combinations {
		if combined.script == nil {
			continue
		}
		for _, part := range combined.parts {
			if part != nil {
				d.sizes[combined.script.index] += d.sizes[part.index]
			}
		}
	}
	for _, outer := range indexed {
		d.isSuperset[outer.index] = slices.ContainsFunc(indexed, func(inner *Script) bool {
			return !slices.Equal(inner.ranges, outer.ranges) && covers(outer.ranges, inner.ranges)
		})
	}
}

// segment cuts the code points at every start and end of a range, and lists the scripts that hold each piece.
func (d *scriptDetection) segment(indexed []*Script) {
	for _, script := range indexed {
		for _, r := range script.ranges {
			d.segments = append(d.segments, r.First, r.Last+1)
		}
	}
	slices.Sort(d.segments)
	d.segments = slices.Compact(d.segments)
	held := make([][]*Script, len(d.segments))
	for _, script := range indexed {
		for _, r := range script.ranges {
			first, _ := slices.BinarySearch(d.segments, r.First)
			end, _ := slices.BinarySearch(d.segments, r.Last+1)
			for at := first; at < end; at++ {
				held[at] = append(held[at], script)
			}
		}
	}
	d.offsets = make([]int, 0, len(held)+1)
	d.offsets = append(d.offsets, 0)
	for _, scripts := range held {
		d.holders = append(d.holders, scripts...)
		d.offsets = append(d.offsets, len(d.holders))
	}
}

// holding is the scripts that hold r.
func (d *scriptDetection) holding(r rune) []*Script {
	at, isStart := slices.BinarySearch(d.segments, r)
	if isStart {
		at++
	}
	if at == 0 {
		return nil
	}
	return d.holders[d.offsets[at-1]:d.offsets[at]]
}

// tally is how many characters of a text a script holds.
type tally struct {
	script *Script
	count  int
}

func (d *scriptDetection) detect(text string) *Script {
	tallies := make([]tally, 0, 4)
	together := make([]int, len(d.combinations))
	unmatched := 0
	for _, r := range text {
		found := d.holding(r)
		if len(found) == 0 {
			unmatched++
		}
		for _, script := range found {
			tallies = add(tallies, script, 1)
		}
		for at, combined := range d.combinations {
			if slices.ContainsFunc(found, func(s *Script) bool { return slices.Contains(combined.parts, s) }) {
				together[at]++
			}
		}
	}
	for at, combined := range d.combinations {
		if combined.script != nil && !onePartHasAll(tallies, combined.parts, together[at]) {
			tallies = append(tallies, tally{script: combined.script, count: together[at]})
		}
	}
	if len(tallies) == 0 {
		return nil
	}
	best := slices.MinFunc(tallies, d.compare)
	if best.count < unmatched {
		return nil
	}
	return best.script
}

// compare ranks a before b when it holds more characters, then when it holds no other script whole, is over
// no notation, has more characters of its own, is no variety of another, and last has the lower code.
func (d *scriptDetection) compare(a, b tally) int {
	return cmp.Or(
		cmp.Compare(b.count, a.count),
		compareBool(d.isSuperset[a.script.index], d.isSuperset[b.script.index]),
		compareBool(isNotation(a.script), isNotation(b.script)),
		cmp.Compare(d.sizes[b.script.index], d.sizes[a.script.index]),
		compareBool(a.script.parentCode != "", b.script.parentCode != ""),
		cmp.Compare(a.script.code, b.script.code),
	)
}

func (d *scriptDetection) detectIn(language *Language, text string) *Script {
	codes, scripts := scriptsOf(language)
	if len(codes) == 0 {
		return nil
	}
	if d.isEveryScript(scripts) {
		return d.detect(text)
	}
	characters := make([]rune, 0, len(text))
	for _, r := range text {
		if !isIgnored(r) {
			characters = append(characters, r)
		}
	}
	if len(characters) == 0 {
		return nil
	}
	var best *Script
	bestCount := 0
	for at := 0; at < len(codes); at++ {
		if isHan(codes[at:]) {
			at += len(hanCodes) - 1
			if found := d.decideHan(characters); found != nil {
				return found
			}
			continue
		}
		script := scripts[at]
		if script == nil {
			continue
		}
		count := 0
		for _, r := range characters {
			if script.Contains(r) {
				count++
			}
		}
		if count >= len(characters) {
			return script
		}
		if count > bestCount {
			best, bestCount = script, count
		}
	}
	return best
}

// isEveryScript is whether scripts lists every script of the catalog, as Translingual's does.
func (d *scriptDetection) isEveryScript(scripts []*Script) bool {
	if len(scripts) < len(d.catalog.scripts) {
		return false
	}
	isListed := make([]bool, len(d.catalog.scripts))
	for _, script := range scripts {
		if script != nil {
			isListed[script.index] = true
		}
	}
	return !slices.Contains(isListed, false)
}

// decideHan is Traditional or Simplified Han when more of the Han characters mark one than the other, Han when as
// many mark each, and nil when no character is Han.
func (d *scriptDetection) decideHan(characters []rune) *Script {
	if d.han == nil {
		return nil
	}
	isFound := false
	traditional, simplified := 0, 0
	for _, r := range characters {
		if !d.han.Contains(r) {
			continue
		}
		isFound = true
		isTraditional := d.traditional != nil && d.traditional.isMark(r)
		isSimplified := d.simplified != nil && d.simplified.isMark(r)
		switch {
		case isTraditional && !isSimplified:
			traditional++
		case isSimplified && !isTraditional:
			simplified++
		}
	}
	switch {
	case !isFound:
		return nil
	case traditional > simplified && d.traditional != nil:
		return d.traditional
	case simplified > traditional && d.simplified != nil:
		return d.simplified
	default:
		return d.han
	}
}

// scriptsOf is the codes of the scripts a language lists, with the scripts the catalog knows by them, or for
// one that lists none those of the nearest of its parents that does.
func scriptsOf(language *Language) ([]string, []*Script) {
	for current := range language.lineage() {
		if len(current.scriptCodes) > 0 {
			return current.scriptCodes, current.scripts
		}
	}
	return nil, nil
}

// isHan is whether codes start with Han, Traditional Han and Simplified Han side by side, as Wiktionary's Hants.
func isHan(codes []string) bool {
	return len(codes) >= len(hanCodes) && slices.Equal(codes[:len(hanCodes)], hanCodes)
}

// add counts n more characters for script.
func add(tallies []tally, script *Script, n int) []tally {
	for at := range tallies {
		if tallies[at].script == script {
			tallies[at].count += n
			return tallies
		}
	}
	return append(tallies, tally{script: script, count: n})
}

// onePartHasAll is whether one part of a combination holds every character the combination does.
func onePartHasAll(tallies []tally, parts []*Script, count int) bool {
	for _, part := range parts {
		partCount := 0
		for _, t := range tallies {
			if part != nil && t.script == part {
				partCount = t.count
			}
		}
		if partCount == count {
			return true
		}
	}
	return false
}

func isNotation(script *Script) bool { return len(script.code) == 4 && script.code[0] == 'Z' }

func covers(outer, inner []Range) bool {
	for _, x := range inner {
		isCovered := slices.ContainsFunc(outer, func(r Range) bool { return r.First <= x.First && x.Last <= r.Last })
		if !isCovered {
			return false
		}
	}
	return true
}

// isIgnored is whether r is left out of a text whose language is known: spaces, and ASCII punctuation.
func isIgnored(r rune) bool {
	switch r {
	case ' ', ' ', ' ', ' ', ' ', ' ', ' ', '　',
		'!', '"', '#', '%', '&', '\'', '(', ')', '*', ',', '-', '.', '/', ':', ';', '?', '@', '[', '\\', ']', '_',
		'{', '}':
		return true
	}
	return ('\t' <= r && r <= '\r') || (' ' <= r && r <= ' ')
}

func compareBool(a, b bool) int {
	switch {
	case a == b:
		return 0
	case a:
		return 1
	default:
		return -1
	}
}
