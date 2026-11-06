package polyglossa_test

import (
	"fmt"

	"github.com/fenakhay/polyglossa/go"
)

func ExampleLookupLanguage() {
	english := polyglossa.LookupLanguage("en")
	fmt.Println(english)
	fmt.Println(english.Family())
	fmt.Println(english.Ancestors())
	fmt.Println(polyglossa.LookupLanguage("xx"))
	// Output:
	// English (en)
	// Anglic (gmw-ang)
	// [Early Modern English (en-ear)]
	// <nil>
}

func ExampleLanguageOf() {
	_, err := polyglossa.LanguageOf("xx")
	fmt.Println(err)
	// Output: polyglossa: unknown language "xx"
}

func ExampleLanguage_DetectScript() {
	fmt.Println(polyglossa.LookupLanguage("sh").DetectScript("Москва"))
	fmt.Println(polyglossa.LookupLanguage("zh").DetectScript("語言"))
	fmt.Println(polyglossa.LookupLanguage("zh").DetectScript("语言"))
	// Output:
	// Cyrillic (Cyrl)
	// Traditional Han (Hant)
	// Simplified Han (Hans)
}

func ExampleDetectScript() {
	fmt.Println(polyglossa.DetectScript("Ελληνικά"))
	fmt.Println(polyglossa.DetectScript("ひらがな漢字"))
	fmt.Println(polyglossa.DetectScript("ab 12345"))
	// Output:
	// Greek (Grek)
	// Japanese (Jpan)
	// <nil>
}

func ExampleLookupFamily() {
	germanic := polyglossa.LookupFamily("gmw")
	fmt.Println(germanic.Parent())
	fmt.Println(germanic.ProtoLanguage())
	// Output:
	// Germanic (gem)
	// Proto-West Germanic (gmw-pro)
}

func ExampleScript_NameFor() {
	fmt.Println(polyglossa.LookupScript("Aran").NameFor(polyglossa.LookupLanguage("pa")))
	// Output: Shahmukhi
}
