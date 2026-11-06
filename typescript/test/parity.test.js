import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, test } from "node:test";

import {
  Catalog,
  LANGUAGE_TYPES,
  SCRIPT_DIRECTIONS,
  isLanguageType,
  isScriptDirection,
  PolyglossaError,
  Language,
  LanguageFamily,
  NotACatalogError,
  Script,
  UnknownFamilyError,
  UnknownLanguageError,
  UnknownScriptError,
} from "polyglossa";

const SPEC = JSON.parse(readFileSync(new URL("../../spec/expectations.json", import.meta.url), "utf8"));
const PACKAGE = JSON.parse(readFileSync(new URL("../package.json", import.meta.url), "utf8"));

const codeOf = (it) => it?.code ?? null;
const size = (ranges) => ranges.reduce((sum, it) => sum + it.last - it.first + 1, 0);

describe("the bundled catalog answers as spec/expectations.json says", () => {
  test("counts, date, misses and names", () => {
    const catalog = Catalog.bundled;
    assert.equal(catalog.date, SPEC.date);
    assert.equal(catalog.languages.length, SPEC.counts.languages);
    assert.equal(catalog.families.length, SPEC.counts.families);
    assert.equal(catalog.scripts.length, SPEC.counts.scripts);
    for (const code of SPEC.missing) assert.equal(Language.get(code), null, code);
    for (const [name, code] of Object.entries(SPEC.named)) assert.equal(codeOf(Language.named(name)), code, name);
  });

  test("sampled languages", () => {
    for (const expected of SPEC.languages) {
      const language = Language.of(expected.code);
      const actual = {
        code: language.code,
        name: language.name,
        type: language.type,
        wikidata: language.wikidata,
        family: codeOf(language.family),
        scripts: language.scripts.map(codeOf),
        ancestors: language.ancestors.map(codeOf),
        aliases: [...language.aliases],
        parent: codeOf(language.parent),
        full: language.full.code,
        varieties: language.varieties.map((it) => [it.name, ...it.otherNames]),
      };
      assert.deepEqual(actual, expected, expected.code);
    }
  });

  test("sampled families", () => {
    for (const expected of SPEC.families) {
      const family = LanguageFamily.of(expected.code);
      const actual = {
        code: family.code,
        name: family.name,
        wikidata: family.wikidata,
        parent: codeOf(family.parent),
        protoLanguage: codeOf(family.protoLanguage),
        aliases: [...family.aliases],
        languages: family.languages.map(codeOf),
      };
      assert.deepEqual(actual, expected, expected.code);
    }
  });

  test("sampled scripts", () => {
    for (const expected of SPEC.scripts) {
      const script = Script.of(expected.code);
      const actual = {
        code: script.code,
        name: script.name,
        wikidata: script.wikidata,
        system: script.system,
        aliases: [...script.aliases],
        parent: codeOf(script.parent),
        direction: script.direction,
        localNames: Object.fromEntries([...script.localNames].map(([language, name]) => [language.code, name])),
        characters: size(script.ranges),
        ranges: script.ranges.length,
        marks: size(script.marks),
      };
      assert.deepEqual(actual, expected, expected.code);
    }
  });

  test("detections", () => {
    for (const expected of SPEC.detections) {
      const { language, text } = expected;
      const script = language ? Language.of(language).detectScript(text) : Script.detect(text);
      assert.equal(codeOf(script), expected.script, `${language ?? "none"}: ${text}`);
    }
    assert.ok(Script.of("Cyrl").contains(0x416));
    assert.ok(!Script.of("Cyrl").contains(0x41));
    assert.equal(Script.of("Arab").count("كتاب abc"), 4);
  });
});

describe("lookups read as TypeScript", () => {
  test("misses", () => {
    assert.throws(() => Language.of("xx-none"), UnknownLanguageError);
    assert.throws(() => LanguageFamily.of("xx-none"), UnknownFamilyError);
    assert.throws(() => Script.of("Xxxx-none"), UnknownScriptError);
    assert.throws(() => Catalog.fromTsv("{}"), NotACatalogError);
    assert.equal(LanguageFamily.get("xx-none"), null);
    assert.equal(Script.get("Xxxx-none"), null);
  });

  test("members", () => {
    assert.ok(Language.has("ang"));
    assert.ok(!Language.has("xx-none"));
    assert.equal(Language.of("en-ear").full, Language.of("en"));
    assert.equal(Script.of("Deva").nameFor(Language.of("mr")), "Balbodh");
    assert.equal(Script.named("Arabic").direction, "rtl");
    assert.equal(Language.of("ine-pro").type, "reconstructed");
    assert.equal(String(Language.of("en")), "English (en)");
    assert.equal(Language.of("en"), Language.get("en"));
    assert.equal(Language.all, Catalog.bundled.languages);
    assert.ok(Object.isFrozen(Language.all));
  });

  test("a catalog of one's own", () => {
    const catalog = Catalog.fromTsv("polyglossa\t1\nL\txx\tExample\t\t\t\tLatn\nS\tLatn\tLatin\t\t\t\t\t\t\t41-5A\n");
    assert.equal(catalog.date, null);
    assert.equal(catalog.of("xx").detectScript("ABC")?.code, "Latn");
    assert.equal(catalog.detectScript("ABC")?.code, "Latn");
    assert.equal(Language.has("xx"), false);
  });

  test("a catalog that cannot be read says which line", () => {
    const broken = "polyglossa\t1\nS\tLatn\tLatin\t\t\t\t\t\t\t41-ZZ\n";
    assert.throws(() => Catalog.fromTsv(broken), (error) => {
      assert.ok(error instanceof NotACatalogError);
      assert.ok(error instanceof PolyglossaError);
      assert.equal(error.line, 2);
      return true;
    });
  });

  test("kinds and their guards", () => {
    assert.deepEqual([...LANGUAGE_TYPES], ["regular", "reconstructed", "appendix-constructed", "etymology-only"]);
    assert.ok(LANGUAGE_TYPES.every(isLanguageType));
    assert.ok(SCRIPT_DIRECTIONS.every(isScriptDirection));
    assert.equal(isLanguageType("dialect"), false);
    assert.equal(isScriptDirection(null), false);
    assert.ok(Object.isFrozen(Language.of("en").ancestors));
    assert.ok(Object.isFrozen(Language.of("en").aliases));
  });

  test("the version is the day of the data", () => {
    assert.equal(PACKAGE.version, SPEC.date.split("-").map(Number).join("."));
  });
});
