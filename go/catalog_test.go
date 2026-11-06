package polyglossa_test

import (
	"encoding/json"
	"errors"
	"os"
	"reflect"
	"sync"
	"testing"

	"github.com/fenakhay/polyglossa/go"
)

type spec struct {
	Date       string               `json:"date"`
	Counts     map[string]int       `json:"counts"`
	Languages  []map[string]any     `json:"languages"`
	Families   []map[string]any     `json:"families"`
	Scripts    []map[string]any     `json:"scripts"`
	Named      map[string]*string   `json:"named"`
	Missing    []string             `json:"missing"`
	Detections []map[string]*string `json:"detections"`
}

var loadSpec = sync.OnceValues(func() (spec, error) {
	var expected spec
	data, err := os.ReadFile("../spec/expectations.json")
	if err != nil {
		return expected, err
	}
	return expected, json.Unmarshal(data, &expected)
})

func expectations(t *testing.T) spec {
	t.Helper()
	expected, err := loadSpec()
	if err != nil {
		t.Fatalf("reading the spec: %v", err)
	}
	return expected
}

type coded interface{ Code() string }

func codeOf[T coded](it T) any {
	if reflect.ValueOf(it).IsNil() {
		return nil
	}
	return it.Code()
}

func codesOf[T coded](items []T) []any {
	codes := make([]any, 0, len(items))
	for _, it := range items {
		codes = append(codes, it.Code())
	}
	return codes
}

func asAny(items []string) []any {
	out := make([]any, 0, len(items))
	for _, it := range items {
		out = append(out, it)
	}
	return out
}

func orNil(text string) any {
	if text == "" {
		return nil
	}
	return text
}

func size(ranges []polyglossa.Range) float64 {
	total := 0
	for _, r := range ranges {
		total += int(r.Last-r.First) + 1
	}
	return float64(total)
}

// assertSame fails when got, as JSON reads it back, is not expected.
func assertSame(t *testing.T, got, expected map[string]any) {
	t.Helper()
	written, err := json.Marshal(got)
	if err != nil {
		t.Fatal(err)
	}
	var decoded map[string]any
	if err := json.Unmarshal(written, &decoded); err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(decoded, expected) {
		t.Errorf("got %v\nexpected %v", decoded, expected)
	}
}

func TestBundled(t *testing.T) {
	t.Parallel()
	expected := expectations(t)
	catalog := polyglossa.Bundled()
	if catalog.Date() != expected.Date {
		t.Errorf("date %q, expected %q", catalog.Date(), expected.Date)
	}
	counts := map[string]int{
		"languages": len(catalog.Languages()),
		"families":  len(catalog.Families()),
		"scripts":   len(catalog.Scripts()),
	}
	if !reflect.DeepEqual(counts, expected.Counts) {
		t.Errorf("counts %v, expected %v", counts, expected.Counts)
	}
	for _, code := range expected.Missing {
		if polyglossa.LookupLanguage(code) != nil {
			t.Errorf("%s should be missing", code)
		}
	}
	for name, code := range expected.Named {
		got := codeOf(polyglossa.LanguageNamed(name))
		isSame := (code == nil && got == nil) || (code != nil && got == *code)
		if !isSame {
			t.Errorf("named %q: %v, expected %v", name, got, code)
		}
	}
}

func TestLanguages(t *testing.T) {
	t.Parallel()
	for _, expected := range expectations(t).Languages {
		code, _ := expected["code"].(string)
		t.Run(code, func(t *testing.T) {
			t.Parallel()
			language, err := polyglossa.LanguageOf(code)
			if err != nil {
				t.Fatal(err)
			}
			varieties := make([]any, 0, len(language.Varieties()))
			for _, variety := range language.Varieties() {
				varieties = append(varieties, append([]any{variety.Name}, asAny(variety.OtherNames)...))
			}
			assertSame(t, map[string]any{
				"code":      language.Code(),
				"name":      language.Name(),
				"type":      string(language.Type()),
				"wikidata":  orNil(language.Wikidata()),
				"family":    codeOf(language.Family()),
				"scripts":   codesOf(language.Scripts()),
				"ancestors": codesOf(language.Ancestors()),
				"aliases":   asAny(language.Aliases()),
				"parent":    codeOf(language.Parent()),
				"full":      language.Full().Code(),
				"varieties": varieties,
			}, expected)
		})
	}
}

func TestFamilies(t *testing.T) {
	t.Parallel()
	for _, expected := range expectations(t).Families {
		code, _ := expected["code"].(string)
		t.Run(code, func(t *testing.T) {
			t.Parallel()
			family, err := polyglossa.FamilyOf(code)
			if err != nil {
				t.Fatal(err)
			}
			assertSame(t, map[string]any{
				"code":          family.Code(),
				"name":          family.Name(),
				"wikidata":      orNil(family.Wikidata()),
				"parent":        codeOf(family.Parent()),
				"protoLanguage": codeOf(family.ProtoLanguage()),
				"aliases":       asAny(family.Aliases()),
				"languages":     codesOf(family.Languages()),
			}, expected)
		})
	}
}

func TestScripts(t *testing.T) {
	t.Parallel()
	for _, expected := range expectations(t).Scripts {
		code, _ := expected["code"].(string)
		t.Run(code, func(t *testing.T) {
			t.Parallel()
			script, err := polyglossa.ScriptOf(code)
			if err != nil {
				t.Fatal(err)
			}
			local := map[string]any{}
			for language, name := range script.LocalNames() {
				local[language.Code()] = name
			}
			assertSame(t, map[string]any{
				"code":       script.Code(),
				"name":       script.Name(),
				"wikidata":   orNil(script.Wikidata()),
				"system":     orNil(script.System()),
				"aliases":    asAny(script.Aliases()),
				"parent":     codeOf(script.Parent()),
				"direction":  string(script.Direction()),
				"localNames": local,
				"characters": size(script.Ranges()),
				"ranges":     float64(len(script.Ranges())),
				"marks":      size(script.Marks()),
			}, expected)
		})
	}
}

func TestDetectScript(t *testing.T) {
	t.Parallel()
	for _, expected := range expectations(t).Detections {
		text := *expected["text"]
		name := "no language: " + text
		if code := expected["language"]; code != nil {
			name = *code + ": " + text
		}
		t.Run(name, func(t *testing.T) {
			t.Parallel()
			var found *polyglossa.Script
			if code := expected["language"]; code != nil {
				found = polyglossa.LookupLanguage(*code).DetectScript(text)
			} else {
				found = polyglossa.DetectScript(text)
			}
			got := codeOf(found)
			script := expected["script"]
			isSame := (script == nil && got == nil) || (script != nil && got == *script)
			if !isSame {
				t.Errorf("%v, expected %v", got, script)
			}
		})
	}
}

func TestScriptCharacters(t *testing.T) {
	t.Parallel()
	cyrillic := polyglossa.LookupScript("Cyrl")
	if !cyrillic.Contains('Ж') || cyrillic.Contains('A') {
		t.Error("Cyrl should hold Ж and not A")
	}
	if count := polyglossa.LookupScript("Arab").Count("كتاب abc"); count != 4 {
		t.Errorf("Arab counts %d, expected 4", count)
	}
}

func TestLookups(t *testing.T) {
	t.Parallel()
	tests := []struct {
		name     string
		lookup   func() error
		expected error
	}{
		{
			name: "unknown language",
			lookup: func() error {
				_, err := polyglossa.LanguageOf("xx-none")
				return err
			},
			expected: polyglossa.ErrUnknownLanguage,
		},
		{
			name: "unknown family",
			lookup: func() error {
				_, err := polyglossa.FamilyOf("xx-none")
				return err
			},
			expected: polyglossa.ErrUnknownFamily,
		},
		{
			name: "unknown script",
			lookup: func() error {
				_, err := polyglossa.ScriptOf("Xxxx-none")
				return err
			},
			expected: polyglossa.ErrUnknownScript,
		},
		{
			name: "not a catalog",
			lookup: func() error {
				_, err := polyglossa.ParseCatalog("{}")
				return err
			},
			expected: polyglossa.ErrNotACatalog,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			if err := tt.lookup(); !errors.Is(err, tt.expected) {
				t.Errorf("got %v, expected %v", err, tt.expected)
			}
		})
	}
}

func TestLinks(t *testing.T) {
	t.Parallel()
	if polyglossa.LookupLanguage("en-ear").Full() != polyglossa.LookupLanguage("en") {
		t.Error("en-ear should stand for en")
	}
	if name := polyglossa.LookupScript("Deva").NameFor(polyglossa.LookupLanguage("mr")); name != "Balbodh" {
		t.Errorf("Deva for mr: %s", name)
	}
	if polyglossa.ScriptNamed("Arabic").Direction() != polyglossa.RightToLeft {
		t.Error("Arabic should run right to left")
	}
	if polyglossa.LookupLanguage("ine-pro").Type() != polyglossa.Reconstructed {
		t.Error("ine-pro should be reconstructed")
	}
	if got := polyglossa.LookupLanguage("en").String(); got != "English (en)" {
		t.Errorf("String is %s", got)
	}
}

func TestParseCatalog(t *testing.T) {
	t.Parallel()
	const text = "polyglossa\t1\nL\txx\tExample\t\t\t\tLatn\nS\tLatn\tLatin\t\t\t\t\t\t\t41-5A\n"
	catalog, err := polyglossa.ParseCatalog(text)
	if err != nil {
		t.Fatal(err)
	}
	if catalog.Date() != "" {
		t.Errorf("date %q, expected none", catalog.Date())
	}
	if script := catalog.Language("xx").DetectScript("ABC"); script == nil || script.Code() != "Latn" {
		t.Errorf("xx detects %v, expected Latn", script)
	}
	if polyglossa.LookupLanguage("xx") != nil {
		t.Error("the bundled catalog should not have xx")
	}
	_, err = polyglossa.ParseCatalog("polyglossa\t1\nS\tLatn\tLatin\t\t\t\t\t\t\t41-ZZ\n")
	var parseError *polyglossa.ParseError
	if !errors.As(err, &parseError) || parseError.Line != 2 {
		t.Errorf("got %v, expected a parse error on line 2", err)
	}
}
