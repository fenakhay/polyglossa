import type { Catalog } from "./catalog.js";
import type { Language, Script } from "./entries.js";
import type { CodePointRange } from "./kinds.js";
import { compareCodes, type Link } from "./link.js";

const HAN = ["Hani", "Hant", "Hans"] as const;
const COMBINED = [
  { code: "Jpan", parts: ["Hani", "Hira", "Kana"] },
  { code: "Kore", parts: ["Hani", "Hang"] },
] as const;
const PUNCTUATION: ReadonlySet<number> = new Set([..."!\"#%&'()*,-./:;?@[\\]_{}"].map((it) => it.charCodeAt(0)));
const SPACES: readonly CodePointRange[] = [
  { first: 0x9, last: 0xd },
  { first: 0x20, last: 0x20 },
  { first: 0xa0, last: 0xa0 },
  { first: 0x1680, last: 0x1680 },
  { first: 0x2000, last: 0x200a },
  { first: 0x2028, last: 0x2029 },
  { first: 0x202f, last: 0x202f },
  { first: 0x205f, last: 0x205f },
  { first: 0x3000, last: 0x3000 },
];

/**
 * A script made of others, as Japanese is of Han, Hiragana and Katakana, which a text is written in when its
 * characters are of more than one of them. A part the catalog lacks is `null`.
 */
interface Combination {
  readonly script: Script | null;
  readonly parts: readonly (Script | null)[];
}

/** How many characters of a text a script holds. */
interface Tally {
  readonly script: Script;
  count: number;
}

/**
 * How Wiktionary tells which script a text is in, as the Kotlin library does: `Language:findBestScript` in
 * `Module:languages` for a language, and `findBestScriptWithoutLang` for none.
 *
 * Its tables are indexed by a script's {@link Script.index}. The code points are cut into segments at every start
 * and end of a script's range, so the scripts that hold a code point are those of its segment: for segment `i`,
 * which starts at `segments[i]`, `holders` from `offsets[i]` to `offsets[i + 1]`.
 *
 * @internal
 */
export class ScriptDetection {
  readonly #scripts: readonly Script[];
  readonly #sizes: number[];
  readonly #isSuperset: boolean[];
  readonly #segments: number[] = [];
  readonly #offsets: number[] = [0];
  readonly #holders: Script[] = [];
  readonly #combinations: readonly Combination[];
  readonly #han: Script | null;
  readonly #traditional: Script | null;
  readonly #simplified: Script | null;

  constructor(scripts: readonly Script[], catalog: Catalog) {
    this.#scripts = scripts;
    this.#sizes = scripts.map(() => 0);
    this.#isSuperset = scripts.map(() => false);
    this.#combinations = COMBINED.map(({ code, parts }) => ({
      script: catalog.script(code),
      parts: parts.map((part) => catalog.script(part)),
    }));
    this.#han = catalog.script(HAN[0]);
    this.#traditional = catalog.script(HAN[1]);
    this.#simplified = catalog.script(HAN[2]);
    const combined = new Set<string>(COMBINED.map(({ code }) => code));
    const indexed = scripts.filter((script) => script.ranges.length > 0 && !combined.has(script.code));
    this.#measure(indexed);
    this.#segment(indexed);
  }

  /** Counts each script's characters, a combination's as the sum of its parts', and marks the supersets. */
  #measure(indexed: readonly Script[]): void {
    for (const script of indexed) {
      this.#sizes[script.index] = script.ranges.reduce((sum, range) => sum + range.last - range.first + 1, 0);
    }
    for (const { script, parts } of this.#combinations) {
      if (script === null) continue;
      this.#sizes[script.index] = parts.reduce((sum, part) => sum + (part === null ? 0 : this.#size(part)), 0);
    }
    for (const outer of indexed) {
      this.#isSuperset[outer.index] = indexed.some(
        (inner) => !sameRanges(inner.ranges, outer.ranges) && covers(outer.ranges, inner.ranges),
      );
    }
  }

  /** Cuts the code points at every start and end of a range, and lists the scripts that hold each piece. */
  #segment(indexed: readonly Script[]): void {
    const bounds = new Set<number>();
    for (const script of indexed) {
      for (const range of script.ranges) bounds.add(range.first).add(range.last + 1);
    }
    this.#segments.push(...[...bounds].sort((a, b) => a - b));
    const held: Script[][] = this.#segments.map(() => []);
    for (const script of indexed) {
      for (const range of script.ranges) {
        const end = this.#segmentOf(range.last + 1);
        for (let at = this.#segmentOf(range.first); at < end; at++) held[at]?.push(script);
      }
    }
    for (const scripts of held) {
      this.#holders.push(...scripts);
      this.#offsets.push(this.#holders.length);
    }
  }

  /** The place of the segment that starts at `codePoint` or, when none does, of the one that holds it. */
  #segmentOf(codePoint: number): number {
    let low = 0;
    let high = this.#segments.length;
    while (low < high) {
      const middle = (low + high) >>> 1;
      if ((this.#segments[middle] ?? Infinity) <= codePoint) low = middle + 1;
      else high = middle;
    }
    return low - 1;
  }

  #size(script: Script): number {
    return this.#sizes[script.index] ?? 0;
  }


  detect(text: string): Script | null {
    const tallies: Tally[] = [];
    const together = this.#combinations.map(() => 0);
    let unmatched = 0;
    for (let at = 0; at < text.length; ) {
      const codePoint = text.codePointAt(at) ?? 0;
      at += codePoint > 0xffff ? 2 : 1;
      const segment = this.#segmentOf(codePoint);
      const from = segment < 0 ? 0 : (this.#offsets[segment] ?? 0);
      const to = segment < 0 ? 0 : (this.#offsets[segment + 1] ?? 0);
      if (from === to) unmatched++;
      for (let holder = from; holder < to; holder++) {
        const script = this.#holders[holder];
        if (script !== undefined) add(tallies, script);
      }
      for (let combination = 0; combination < this.#combinations.length; combination++) {
        const parts = this.#combinations[combination]?.parts ?? [];
        if (this.#holdsPart(from, to, parts)) together[combination] = (together[combination] ?? 0) + 1;
      }
    }
    this.#combinations.forEach(({ script, parts }, at) => {
      const count = together[at] ?? 0;
      const onePartHasAll = parts.some((part) => countOf(tallies, part) === count);
      if (script !== null && !onePartHasAll) tallies.push({ script, count });
    });
    const best = tallies.reduce<Tally | null>((a, b) => (a === null || this.#compare(b, a) < 0 ? b : a), null);
    return best !== null && best.count >= unmatched ? best.script : null;
  }

  /** Whether one of the holders from `from` to before `to` is one of `parts`. */
  #holdsPart(from: number, to: number, parts: readonly (Script | null)[]): boolean {
    for (let at = from; at < to; at++) {
      if (parts.includes(this.#holders[at] ?? null)) return true;
    }
    return false;
  }

  /**
   * Ranks `a` before `b` when it holds more characters, then when it holds no other script whole, is over no
   * notation, has more characters of its own, is no variety of another, and last has the lower code.
   */
  #compare(a: Tally, b: Tally): number {
    return (
      b.count - a.count ||
      Number(this.#isSuperset[a.script.index]) - Number(this.#isSuperset[b.script.index]) ||
      Number(isNotation(a.script)) - Number(isNotation(b.script)) ||
      this.#size(b.script) - this.#size(a.script) ||
      Number(a.script.isVariety) - Number(b.script.isVariety) ||
      compareCodes(a.script.code, b.script.code)
    );
  }

  detectIn(language: Language, text: string): Script | null {
    const listed = language.listedScripts;
    if (listed.length === 0) return null;
    if (this.#isEveryScript(listed)) return this.detect(text);
    const codePoints: number[] = [];
    for (let at = 0; at < text.length; ) {
      const codePoint = text.codePointAt(at) ?? 0;
      at += codePoint > 0xffff ? 2 : 1;
      if (!isIgnored(codePoint)) codePoints.push(codePoint);
    }
    if (codePoints.length === 0) return null;
    let best: Script | null = null;
    let bestCount = 0;
    for (let at = 0; at < listed.length; at++) {
      if (isHan(listed, at)) {
        at += HAN.length - 1;
        const han = this.#decideHan(codePoints);
        if (han !== null) return han;
        continue;
      }
      const link = listed[at];
      if (link?.kind !== "known") continue;
      const script = link.entry;
      let count = 0;
      for (const codePoint of codePoints) {
        if (script.contains(codePoint)) count++;
      }
      if (count >= codePoints.length) return script;
      if (count > bestCount) {
        best = script;
        bestCount = count;
      }
    }
    return best;
  }

  /** Whether `listed` lists every script of the catalog, as Translingual's does. */
  #isEveryScript(listed: readonly Link<Script>[]): boolean {
    if (listed.length < this.#scripts.length) return false;
    const isListed = this.#scripts.map(() => false);
    for (const link of listed) {
      if (link.kind === "known") isListed[link.entry.index] = true;
    }
    return isListed.every((it) => it);
  }

  /**
   * Traditional or Simplified Han when more of the Han characters mark one than the other, Han when as many mark
   * each, and `null` when no character is Han.
   */
  #decideHan(codePoints: readonly number[]): Script | null {
    const han = this.#han;
    if (han === null) return null;
    let isFound = false;
    let traditional = 0;
    let simplified = 0;
    for (const codePoint of codePoints) {
      if (!han.contains(codePoint)) continue;
      isFound = true;
      const isTraditional = this.#traditional?.isMark(codePoint) ?? false;
      const isSimplified = this.#simplified?.isMark(codePoint) ?? false;
      if (isTraditional && !isSimplified) traditional++;
      if (isSimplified && !isTraditional) simplified++;
    }
    if (!isFound) return null;
    if (traditional > simplified) return this.#traditional ?? han;
    if (simplified > traditional) return this.#simplified ?? han;
    return han;
  }
}

/** Counts one more character for `script`. */
function add(tallies: Tally[], script: Script): void {
  const tally = tallies.find((it) => it.script === script);
  if (tally === undefined) tallies.push({ script, count: 1 });
  else tally.count++;
}

function countOf(tallies: readonly Tally[], script: Script | null): number {
  return tallies.find((it) => it.script === script)?.count ?? 0;
}

/** Whether `listed` has Han, Traditional Han and Simplified Han side by side at `at`, as Wiktionary's `Hants`. */
function isHan(listed: readonly Link<Script>[], at: number): boolean {
  return HAN.every((code, offset) => {
    const link = listed[at + offset];
    return link !== undefined && codeOf(link) === code;
  });
}

function codeOf(link: Link<Script>): string {
  return link.kind === "known" ? link.entry.code : link.code;
}

function isNotation(script: Script): boolean {
  return script.code.length === 4 && script.code.startsWith("Z");
}

function sameRanges(a: readonly CodePointRange[], b: readonly CodePointRange[]): boolean {
  return a.length === b.length && a.every((range, at) => range.first === b[at]?.first && range.last === b[at]?.last);
}

function covers(outer: readonly CodePointRange[], inner: readonly CodePointRange[]): boolean {
  return inner.every((x) => outer.some((range) => range.first <= x.first && x.last <= range.last));
}

/** Whether `codePoint` is left out of a text whose language is known: spaces, and ASCII punctuation. */
function isIgnored(codePoint: number): boolean {
  if (codePoint < 0x80) return (0x9 <= codePoint && codePoint <= 0xd) || codePoint === 0x20 || PUNCTUATION.has(codePoint);
  return SPACES.some((range) => range.first <= codePoint && codePoint <= range.last);
}
