"""The bundled catalog answers as ``spec/expectations.json`` says, as the Kotlin library and the Swift package do."""

import json
import pathlib
import unittest

from polyglossa import (
    Catalog,
    Language,
    LanguageFamily,
    LanguageType,
    NotACatalogError,
    Script,
    ScriptDirection,
    UnknownLanguageError,
    __version__,
)

SPEC = json.loads((pathlib.Path(__file__).resolve().parents[2] / "spec" / "expectations.json").read_text("utf-8"))


class ParityTest(unittest.TestCase):
    def test_counts_date_misses_and_names(self):
        catalog = Catalog.bundled()
        self.assertEqual(catalog.date, SPEC["date"])
        self.assertEqual(len(catalog.languages), SPEC["counts"]["languages"])
        self.assertEqual(len(catalog.families), SPEC["counts"]["families"])
        self.assertEqual(len(catalog.scripts), SPEC["counts"]["scripts"])
        for code in SPEC["missing"]:
            self.assertIsNone(Language.get(code), code)
        for name, code in SPEC["named"].items():
            found = Language.named(name)
            self.assertEqual(found.code if found else None, code, name)

    def test_sampled_languages(self):
        for expected in SPEC["languages"]:
            language = Language[expected["code"]]
            with self.subTest(language.code):
                self.assertEqual(language.name, expected["name"])
                self.assertEqual(language.type.value, expected["type"])
                self.assertEqual(language.wikidata, expected["wikidata"])
                self.assertEqual(language.family.code if language.family else None, expected["family"])
                self.assertEqual([s.code for s in language.scripts], expected["scripts"])
                self.assertEqual([a.code for a in language.ancestors], expected["ancestors"])
                self.assertEqual(list(language.aliases), expected["aliases"])
                self.assertEqual(language.parent.code if language.parent else None, expected["parent"])
                self.assertEqual(language.full.code, expected["full"])
                self.assertEqual([[v.name, *v.other_names] for v in language.varieties], expected["varieties"])

    def test_sampled_families_and_scripts(self):
        for expected in SPEC["families"]:
            family = LanguageFamily[expected["code"]]
            with self.subTest(family.code):
                self.assertEqual(family.name, expected["name"])
                self.assertEqual(family.wikidata, expected["wikidata"])
                self.assertEqual(family.parent.code if family.parent else None, expected["parent"])
                self.assertEqual(family.proto_language.code if family.proto_language else None, expected["protoLanguage"])
                self.assertEqual(list(family.aliases), expected["aliases"])
                self.assertEqual([l.code for l in family.languages], expected["languages"])
        for expected in SPEC["scripts"]:
            script = Script[expected["code"]]
            with self.subTest(script.code):
                self.assertEqual(script.name, expected["name"])
                self.assertEqual(script.wikidata, expected["wikidata"])
                self.assertEqual(script.system, expected["system"])
                self.assertEqual(list(script.aliases), expected["aliases"])
                self.assertEqual(script.parent.code if script.parent else None, expected["parent"])
                self.assertEqual(script.direction.value, expected["direction"])
                self.assertEqual({l.code: n for l, n in script.local_names.items()}, expected["localNames"])

    def test_lookups_read_as_python(self):
        with self.assertRaises(UnknownLanguageError):
            Language["xx-none"]
        with self.assertRaises(KeyError):
            Language["xx-none"]
        self.assertIn("ang", Language)
        self.assertNotIn("xx-none", Language)
        self.assertEqual(Language["en-ear"].full, Language["en"])
        self.assertEqual(Script["Deva"].name_for(Language["mr"]), "Balbodh")
        self.assertIs(Script.named("Arabic").direction, ScriptDirection.RIGHT_TO_LEFT)
        self.assertIs(Language["ine-pro"].type, LanguageType.RECONSTRUCTED)
        self.assertEqual(str(Language["en"]), "English (en)")
        self.assertEqual({Language["en"]: 1}[Language.of("en")], 1)
        self.assertEqual(__version__, ".".join(str(int(part)) for part in SPEC["date"].split("-")))
        with self.assertRaises(NotACatalogError):
            Catalog("{}")


if __name__ == "__main__":
    unittest.main()
