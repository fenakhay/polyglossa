import tsv from "./data.js";
import { ScriptDetection } from "./detection.js";
import { Language, LanguageFamily, Script, type Linker } from "./entries.js";
import { UnknownLanguageError } from "./errors.js";
import { compareCodes, type Link } from "./link.js";
import { read, type RawCatalog } from "./read.js";

/**
 * A set of languages, families and scripts that know one another.
 *
 * {@link Catalog.bundled}, English Wiktionary's own, is the one {@link Language}, {@link LanguageFamily} and
 * {@link Script} look up.
 */
export class Catalog {
  static #bundled: Catalog | undefined;

  /** The day Wiktionary's data was exported, as `2026-11-06`, or `null` when the catalog does not say. */
  readonly date: string | null;
  /** Every language and etymology-only variety, sorted by code. */
  readonly languages: readonly Language[];
  /** Every family, sorted by code. */
  readonly families: readonly LanguageFamily[];
  /** Every script, sorted by code. */
  readonly scripts: readonly Script[];

  readonly #languages: ReadonlyMap<string, Language>;
  readonly #families: ReadonlyMap<string, LanguageFamily>;
  readonly #scripts: ReadonlyMap<string, Script>;
  readonly #languageNames: ReadonlyMap<string, Language>;
  readonly #scriptNames: ReadonlyMap<string, Script>;
  #detection: ScriptDetection | undefined;

  private constructor(raw: RawCatalog) {
    this.date = raw.date;
    this.languages = Object.freeze(byCode(raw.languages).map(([code, it]) => new Language(code, it, this)));
    this.families = Object.freeze(byCode(raw.families).map(([code, it]) => new LanguageFamily(code, it)));
    this.scripts = Object.freeze(byCode(raw.scripts).map(([code, it], at) => new Script(code, it, at, this)));
    this.#languages = new Map(this.languages.map((it) => [it.code, it]));
    this.#families = new Map(this.families.map((it) => [it.code, it]));
    this.#scripts = new Map(this.scripts.map((it) => [it.code, it]));

    const links: Linker = {
      language: linker(this.#languages),
      family: linker(this.#families),
      script: linker(this.#scripts),
    };
    const members = new Map<string, Language[]>();
    for (const language of this.languages) {
      const own = rawOf(raw.languages, language.code);
      language.link(own, links);
      if (own.family === null) continue;
      let familyMembers = members.get(own.family);
      if (familyMembers === undefined) {
        familyMembers = [];
        members.set(own.family, familyMembers);
      }
      familyMembers.push(language);
    }
    for (const family of this.families) {
      family.link(rawOf(raw.families, family.code), links, members.get(family.code) ?? []);
    }
    for (const script of this.scripts) {
      script.link(rawOf(raw.scripts, script.code), links);
    }
    this.#languageNames = namesBy(this.languages, (it) => !it.isEtymologyOnly);
    this.#scriptNames = namesBy(this.scripts, (it) => !it.isVariety);
  }

  /** English Wiktionary's languages, families and scripts as the package was built with them. */
  static get bundled(): Catalog {
    Catalog.#bundled ??= new Catalog(read(tsv));
    return Catalog.#bundled;
  }

  /**
   * The catalog `text` holds, as the Kotlin library's `Catalog.toTsv` writes it; throws {@link NotACatalogError},
   * with the line, when it is not one.
   */
  static fromTsv(text: string): Catalog {
    return new Catalog(read(text));
  }

  /** The language with `code`, or `null`. */
  get(code: string): Language | null {
    return this.#languages.get(code) ?? null;
  }

  /** The language with `code`; throws {@link UnknownLanguageError} when there is none. */
  of(code: string): Language {
    const language = this.get(code);
    if (language === null) throw new UnknownLanguageError(code);
    return language;
  }

  /** Whether there is a language with `code`. */
  has(code: string): boolean {
    return this.#languages.has(code);
  }

  /** The language called `name`; a full language wins over an etymology-only variety of the same name. */
  named(name: string): Language | null {
    return this.#languageNames.get(name.trim()) ?? null;
  }

  /** The family with `code`, or `null`. */
  family(code: string): LanguageFamily | null {
    return this.#families.get(code) ?? null;
  }

  /** The script with `code`, or `null`. */
  script(code: string): Script | null {
    return this.#scripts.get(code) ?? null;
  }

  /** The script called `name`; one that is no variety of another wins over one that is. */
  scriptNamed(name: string): Script | null {
    return this.#scriptNames.get(name.trim()) ?? null;
  }

  /**
   * The script `text` is written in, whatever its language, as Wiktionary's `findBestScriptWithoutLang` finds it:
   * the one most of its characters belong to, or `null` when more of them belong to no script. A tie goes to a
   * script that holds no other whole, as Greek does beside Polytonic Greek, then to a script over notation such as
   * `Zmth`, then to the one with the most characters, as Han is beside Latin, then to one that is no variety of
   * another. {@link Language.detectScript} knows better when the language is known.
   */
  detectScript(text: string): Script | null {
    return this.detection.detect(text);
  }

  /** @internal */
  get detection(): ScriptDetection {
    this.#detection ??= new ScriptDetection(this.scripts, this);
    return this.#detection;
  }
}

function byCode<T>(entries: ReadonlyMap<string, T>): [string, T][] {
  return [...entries].sort(([a], [b]) => compareCodes(a, b));
}

/** How to link a code to one of `entries`, the same link for the same code, as many entries give the same. */
function linker<T>(entries: ReadonlyMap<string, T>): (code: string) => Link<T> {
  const links = new Map<string, Link<T>>();
  return (code) => {
    let found = links.get(code);
    if (found === undefined) {
      const entry = entries.get(code);
      found = Object.freeze(entry === undefined ? { kind: "unknown", code } : { kind: "known", entry });
      links.set(code, found);
    }
    return found;
  };
}

function rawOf<T>(entries: ReadonlyMap<string, T>, code: string): T {
  const entry = entries.get(code);
  if (entry === undefined) throw new Error(`polyglossa: ${code} was read and then lost`);
  return entry;
}

/**
 * Each name of `entries` with the entry that has it, one that `wins` beating one that does not, and the later
 * beating the earlier otherwise.
 */
function namesBy<T extends { readonly name: string }>(
  entries: readonly T[],
  wins: (entry: T) => boolean,
): ReadonlyMap<string, T> {
  const names = new Map<string, T>();
  for (const isWinner of [false, true]) {
    for (const entry of entries) {
      if (wins(entry) === isWinner) names.set(entry.name, entry);
    }
  }
  return names;
}
