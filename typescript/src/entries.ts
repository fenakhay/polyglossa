import { Catalog } from "./catalog.js";
import { UnknownFamilyError, UnknownScriptError } from "./errors.js";
import type { CodePointRange, LanguageType, ScriptDirection, Variety, WikidataItem } from "./kinds.js";
import { holds, known, type Link } from "./link.js";
import type { RawFamily, RawLanguage, RawScript } from "./read.js";

/** One language as English Wiktionary's `Module:languages` and `Module:etymology languages` describe it. */
export class Language {
  /** Its code, as `en` or `en-US`. */
  readonly code: string;
  /** Its name, as its section in an entry is headed. */
  readonly name: string;
  /** What kind of language it is. */
  readonly type: LanguageType;
  /** Its Wikidata item, as `Q1860`, or `null`. */
  readonly wikidata: WikidataItem | null;
  /** The other names Wiktionary gives it. */
  readonly aliases: readonly string[];
  /** The varieties Wiktionary names without a code of their own. */
  readonly varieties: readonly Variety[];

  #family: Link<LanguageFamily> | null = null;
  #scripts: readonly Link<Script>[] = [];
  #ancestors: readonly Language[] = [];
  #parent: Language | null = null;
  readonly #catalog: Catalog;

  /** @internal */
  constructor(code: string, raw: RawLanguage, catalog: Catalog) {
    this.code = code;
    this.name = raw.name;
    this.type = raw.type;
    this.wikidata = raw.wikidata;
    this.aliases = raw.aliases;
    this.varieties = raw.varieties;
    this.#catalog = catalog;
  }

  /** @internal Resolves the codes `raw` gives for other entries, once the catalog has them all. */
  link(raw: RawLanguage, links: Linker): void {
    this.#family = raw.family === null ? null : links.family(raw.family);
    this.#scripts = Object.freeze(raw.scripts.map((code) => links.script(code)));
    const ancestors = raw.ancestors.map((code) => known(links.language(code)));
    this.#ancestors = Object.freeze(ancestors.filter((it) => it !== null));
    this.#parent = raw.parent === null ? null : known(links.language(raw.parent));
  }

  /** Whether it is named in etymologies only, with no section of its own in an entry. */
  get isEtymologyOnly(): boolean {
    return this.type === "etymology-only";
  }

  /**
   * The family it belongs to, or `null`; an etymology-only variety with none of its own has that of the nearest
   * of its parents that has one, as American English has English's through North American English.
   */
  get family(): LanguageFamily | null {
    return known(this.#nearest((language) => language.#family));
  }

  /** The scripts it is written in, most usual first, those the catalog knows. */
  get scripts(): readonly Script[] {
    return this.#scripts.map(known).filter((it) => it !== null);
  }

  /** The languages it descends from, those the catalog knows. */
  get ancestors(): readonly Language[] {
    return this.#ancestors;
  }

  /** The language an etymology-only variety belongs to, or `null` for any other. */
  get parent(): Language | null {
    return this.#parent;
  }

  /** The full language this stands for: itself, or for an etymology-only variety the one it belongs to. */
  get full(): Language {
    let full: Language = this;
    this.#nearest((language) => {
      full = language;
      return language.isEtymologyOnly ? null : language;
    });
    return full;
  }

  /**
   * The script `text` is written in, among this language's {@link scripts}, as Wiktionary's `findBestScript`
   * finds it, or `null` when none of them has any of its characters.
   *
   * Spaces and ASCII punctuation are left out, and the scripts are tried in order: the first that has every
   * character wins, or else the one with the most of them, the earlier on a tie. Chinese and the other languages
   * written in Han give Traditional or Simplified Han when more of the characters mark one than the other, and Han
   * when as many mark each. A language written in every script finds it as {@link Catalog.detectScript} does, and
   * an etymology-only variety with no scripts of its own uses its language's. Pass plain text: wiki markup counts
   * as characters.
   *
   * ```ts
   * Language.of("sh").detectScript("Москва")?.code  // "Cyrl"
   * Language.of("zh").detectScript("語言")?.code    // "Hant"
   * ```
   */
  detectScript(text: string): Script | null {
    return this.#catalog.detection.detectIn(this, text);
  }

  /** @internal The scripts it lists, or for one that lists none those of the nearest of its parents that does. */
  get listedScripts(): readonly Link<Script>[] {
    return this.#nearest((language) => (language.#scripts.length > 0 ? language.#scripts : null)) ?? [];
  }

  /**
   * What `pick` finds first in itself, then its parent, its parent's parent and so on, each once, or `null`. The
   * chain is short, so the ones seen are kept in a list rather than a set.
   */
  #nearest<T>(pick: (language: Language) => T | null): T | null {
    const seen: Language[] = [];
    for (let language: Language | null = this; language !== null; language = language.#parent) {
      if (seen.includes(language)) return null;
      const found = pick(language);
      if (found !== null) return found;
      seen.push(language);
    }
    return null;
  }

  toString(): string {
    return `${this.name} (${this.code})`;
  }

  /** The language with `code`; throws {@link UnknownLanguageError} when Wiktionary has none. */
  static of(code: string): Language {
    return Catalog.bundled.of(code);
  }

  /** The language with `code`, or `null` when Wiktionary has none. */
  static get(code: string): Language | null {
    return Catalog.bundled.get(code);
  }

  /** Whether Wiktionary has a language with `code`. */
  static has(code: string): boolean {
    return Catalog.bundled.has(code);
  }

  /** The language called `name`, as its section in an entry is headed, or `null`. */
  static named(name: string): Language | null {
    return Catalog.bundled.named(name);
  }

  /** Every language and etymology-only variety, sorted by code. */
  static get all(): readonly Language[] {
    return Catalog.bundled.languages;
  }
}

/** One language family as English Wiktionary's `Module:families` describes it. */
export class LanguageFamily {
  /** Its code, as `gmw`. */
  readonly code: string;
  /** Its name. */
  readonly name: string;
  /** Its Wikidata item, or `null`. */
  readonly wikidata: WikidataItem | null;
  /** The other names Wiktionary gives it. */
  readonly aliases: readonly string[];

  #parent: LanguageFamily | null = null;
  #protoLanguage: Language | null = null;
  #languages: readonly Language[] = [];

  /** @internal */
  constructor(code: string, raw: RawFamily) {
    this.code = code;
    this.name = raw.name;
    this.wikidata = raw.wikidata;
    this.aliases = raw.aliases;
  }

  /** @internal Resolves the codes `raw` gives for other entries, once the catalog has them all. */
  link(raw: RawFamily, links: Linker, languages: readonly Language[]): void {
    this.#parent = raw.parent === null ? null : known(links.family(raw.parent));
    this.#protoLanguage = known(links.language(raw.protoLanguage ?? `${this.code}-pro`));
    this.#languages = Object.freeze(languages);
  }

  /** The family it belongs to, or `null`. */
  get parent(): LanguageFamily | null {
    return this.#parent;
  }

  /** The language it descends from: the one Wiktionary names, or else `<code>-pro` when there is one. */
  get protoLanguage(): Language | null {
    return this.#protoLanguage;
  }

  /** The languages in it directly, not those of the families under it, sorted by code. */
  get languages(): readonly Language[] {
    return this.#languages;
  }

  toString(): string {
    return `${this.name} (${this.code})`;
  }

  /** The family with `code`; throws {@link UnknownFamilyError} when Wiktionary has none. */
  static of(code: string): LanguageFamily {
    const family = Catalog.bundled.family(code);
    if (family === null) throw new UnknownFamilyError(code);
    return family;
  }

  /** The family with `code`, or `null` when Wiktionary has none. */
  static get(code: string): LanguageFamily | null {
    return Catalog.bundled.family(code);
  }

  /** Whether Wiktionary has a family with `code`. */
  static has(code: string): boolean {
    return Catalog.bundled.family(code) !== null;
  }

  /** Every family, sorted by code. */
  static get all(): readonly LanguageFamily[] {
    return Catalog.bundled.families;
  }
}

/** One script as English Wiktionary's `Module:scripts/data` describes it. */
export class Script {
  /** Its code, as `Latn` or `fa-Arab`. */
  readonly code: string;
  /** Its name. */
  readonly name: string;
  /** Its Wikidata item, or `null`. */
  readonly wikidata: WikidataItem | null;
  /** What kind of writing system it is, as `alphabet`, or `null`. */
  readonly system: string | null;
  /** The other names Wiktionary gives it. */
  readonly aliases: readonly string[];
  /** Which way it runs. */
  readonly direction: ScriptDirection;
  /** Its characters, as sorted ranges of code points apart from one another. */
  readonly ranges: readonly CodePointRange[];
  /**
   * The characters that mark a text as written in it rather than in a script it shares characters with: those
   * of Traditional Han that Simplified Han writes otherwise, and the other way round.
   */
  readonly marks: readonly CodePointRange[];
  /** @internal Its place in its catalog's scripts. */
  readonly index: number;

  #parent: Link<Script> | null = null;
  readonly #localNames: readonly (readonly [code: string, name: string])[];
  readonly #catalog: Catalog;

  /** @internal */
  constructor(code: string, raw: RawScript, index: number, catalog: Catalog) {
    this.code = code;
    this.name = raw.name;
    this.wikidata = raw.wikidata;
    this.system = raw.system;
    this.aliases = raw.aliases;
    this.direction = raw.direction;
    this.ranges = raw.ranges;
    this.marks = raw.marks;
    this.index = index;
    this.#localNames = raw.localNames;
    this.#catalog = catalog;
  }

  /** @internal Resolves the codes `raw` gives for other entries, once the catalog has them all. */
  link(raw: RawScript, links: Linker): void {
    this.#parent = raw.parent === null ? null : links.script(raw.parent);
  }

  /** The script this is a variety of, such as Han for Simplified Han, or `null`. */
  get parent(): Script | null {
    return known(this.#parent);
  }

  /** @internal Whether it names a parent, whether or not its catalog has it. */
  get isVariety(): boolean {
    return this.#parent !== null;
  }

  /** The name `language` gives this script, which is {@link name} unless it has its own. */
  nameFor(language: Language): string {
    return this.#localNames.find(([code]) => code === language.code)?.[1] ?? this.name;
  }

  /** The names some languages give it instead of {@link name}, such as Balbodh for Marathi's Devanagari. */
  get localNames(): ReadonlyMap<Language, string> {
    const names = new Map<Language, string>();
    for (const [code, local] of this.#localNames) {
      const language = this.#catalog.get(code);
      if (language !== null) names.set(language, local);
    }
    return names;
  }

  /** Whether `codePoint` is one of its characters, as `Script.of("Cyrl").contains(0x416)`. */
  contains(codePoint: number): boolean {
    return holds(this.ranges, codePoint);
  }

  /** How many of the characters of `text` are its own, counting each code point once. */
  count(text: string): number {
    let count = 0;
    for (const character of text) {
      if (this.contains(character.codePointAt(0) ?? -1)) count++;
    }
    return count;
  }

  /** @internal Whether `codePoint` marks a text as written in it. */
  isMark(codePoint: number): boolean {
    return holds(this.marks, codePoint);
  }

  toString(): string {
    return `${this.name} (${this.code})`;
  }

  /**
   * The script `text` is written in, whatever its language, or `null`; see {@link Catalog.detectScript}.
   *
   * ```ts
   * Script.detect("Ελληνικά")?.code  // "Grek"
   * ```
   */
  static detect(text: string): Script | null {
    return Catalog.bundled.detectScript(text);
  }

  /** The script with `code`; throws {@link UnknownScriptError} when Wiktionary has none. */
  static of(code: string): Script {
    const script = Catalog.bundled.script(code);
    if (script === null) throw new UnknownScriptError(code);
    return script;
  }

  /** The script with `code`, or `null` when Wiktionary has none. */
  static get(code: string): Script | null {
    return Catalog.bundled.script(code);
  }

  /** Whether Wiktionary has a script with `code`. */
  static has(code: string): boolean {
    return Catalog.bundled.script(code) !== null;
  }

  /** The script called `name`, or `null`; the one without a parent when several are. */
  static named(name: string): Script | null {
    return Catalog.bundled.scriptNamed(name);
  }

  /** Every script, sorted by code. */
  static get all(): readonly Script[] {
    return Catalog.bundled.scripts;
  }
}

/** @internal How an entry resolves the codes it gives for others. */
export interface Linker {
  language(code: string): Link<Language>;
  family(code: string): Link<LanguageFamily>;
  script(code: string): Link<Script>;
}
