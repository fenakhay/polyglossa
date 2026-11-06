import { NotACatalogError } from "./errors.js";
import type { CodePointRange, LanguageType, ScriptDirection, Variety, WikidataItem } from "./kinds.js";

const HEADER = "polyglossa\t1";

const LANGUAGE_TYPE_MARKS: ReadonlyMap<string, LanguageType> = new Map([
  ["", "regular"],
  ["r", "reconstructed"],
  ["a", "appendix-constructed"],
  ["e", "etymology-only"],
]);

const DIRECTION_WORDS: ReadonlyMap<string, ScriptDirection> = new Map([
  ["rtl", "rtl"],
  ["vertical-ltr", "vertical-ltr"],
  ["vertical-rtl", "vertical-rtl"],
]);

/** @internal A language as the text has it, the codes it gives for other entries not yet resolved. */
export interface RawLanguage {
  readonly name: string;
  readonly type: LanguageType;
  readonly wikidata: WikidataItem | null;
  readonly family: string | null;
  readonly scripts: readonly string[];
  readonly ancestors: readonly string[];
  readonly aliases: readonly string[];
  readonly parent: string | null;
  readonly varieties: readonly Variety[];
}

/** @internal A family as the text has it. */
export interface RawFamily {
  readonly name: string;
  readonly wikidata: WikidataItem | null;
  readonly parent: string | null;
  readonly protoLanguage: string | null;
  readonly aliases: readonly string[];
}

/** @internal A script as the text has it. */
export interface RawScript {
  readonly name: string;
  readonly wikidata: WikidataItem | null;
  readonly system: string | null;
  readonly aliases: readonly string[];
  readonly parent: string | null;
  readonly direction: ScriptDirection;
  readonly localNames: readonly (readonly [code: string, name: string])[];
  readonly ranges: readonly CodePointRange[];
  readonly marks: readonly CodePointRange[];
}

/** @internal A catalog as the text has it: each entry under its code, the later of two with the same code winning. */
export interface RawCatalog {
  readonly date: string | null;
  readonly languages: ReadonlyMap<string, RawLanguage>;
  readonly families: ReadonlyMap<string, RawFamily>;
  readonly scripts: ReadonlyMap<string, RawScript>;
}

/** @internal Reads `text`, a catalog as the Kotlin library's `Catalog.toTsv` writes it. */
export function read(text: string): RawCatalog {
  const lines = text.split("\n");
  const header = lines[0] ?? "";
  if (header !== HEADER && !header.startsWith(`${HEADER}\t`)) throw new NotACatalogError(1, "no header");
  const languages = new Map<string, RawLanguage>();
  const families = new Map<string, RawFamily>();
  const scripts = new Map<string, RawScript>();
  lines.forEach((line, at) => {
    if (at === 0 || line === "") return;
    const cells = new Cells(line);
    const kind = cells.next();
    const code = cells.next();
    try {
      if (kind === "L") languages.set(code, readLanguage(cells));
      else if (kind === "F") families.set(code, readFamily(cells));
      else if (kind === "S") scripts.set(code, readScript(cells));
      else throw new Error("not a language, a family or a script");
    } catch (error) {
      throw new NotACatalogError(at + 1, error instanceof Error ? error.message : String(error));
    }
  });
  return { date: header.slice(HEADER.length + 1) || null, languages, families, scripts };
}

/** The cells of one line, read in order; one left off the end reads as empty. */
class Cells {
  readonly #cells: readonly string[];
  #at = 0;

  constructor(line: string) {
    this.#cells = line.split("\t");
  }

  next(): string {
    return this.#cells[this.#at++] ?? "";
  }

  optional(): string | null {
    return this.next() || null;
  }
}

function readLanguage(cells: Cells): RawLanguage {
  const name = unescape(cells.next());
  const mark = cells.next();
  const type = LANGUAGE_TYPE_MARKS.get(mark);
  if (type === undefined) throw new Error(`no language type ${JSON.stringify(mark)}`);
  return {
    name,
    type,
    wikidata: item(cells.next()),
    family: cells.optional(),
    scripts: codes(cells.next()),
    ancestors: codes(cells.next()),
    aliases: names(cells.next()),
    parent: cells.optional(),
    varieties: split(cells.next(), ";").map(variety),
  };
}

function variety(written: string): Variety {
  const [name = "", ...otherNames] = split(written, "|").map(unescape);
  return Object.freeze({ name, otherNames: Object.freeze(otherNames) });
}

function readFamily(cells: Cells): RawFamily {
  return {
    name: unescape(cells.next()),
    wikidata: item(cells.next()),
    parent: cells.optional(),
    protoLanguage: cells.optional(),
    aliases: names(cells.next()),
  };
}

function readScript(cells: Cells): RawScript {
  return {
    name: unescape(cells.next()),
    wikidata: item(cells.next()),
    system: unescape(cells.next()) || null,
    aliases: names(cells.next()),
    parent: cells.optional(),
    direction: DIRECTION_WORDS.get(cells.next()) ?? "ltr",
    localNames: names(cells.next()).map((entry) => {
      const at = entry.indexOf("=");
      return Object.freeze([at < 0 ? entry : entry.slice(0, at), at < 0 ? "" : entry.slice(at + 1)] as const);
    }),
    ranges: codePointRanges(cells.next()),
    marks: codePointRanges(cells.next()),
  };
}

/** The ranges of hexadecimal code points in `cell`, as `41-5A,AA`. */
function codePointRanges(cell: string): readonly CodePointRange[] {
  if (!RANGES.test(cell)) throw new Error(`no code points ${JSON.stringify(cell)}`);
  return Object.freeze(
    codes(cell).map((written) => {
      const dash = written.indexOf("-");
      const first = Number.parseInt(dash < 0 ? written : written.slice(0, dash), 16);
      const last = dash < 0 ? first : Number.parseInt(written.slice(dash + 1), 16);
      return Object.freeze({ first, last });
    }),
  );
}

/** Hexadecimal code points and ranges of them, apart by commas, or none. */
const RANGES = /^(?:[0-9A-Fa-f]+(?:-[0-9A-Fa-f]+)?(?:,(?=.)|$))*$/;

function item(cell: string): WikidataItem | null {
  return cell === "" ? null : `Q${cell}`;
}

function codes(cell: string): readonly string[] {
  return Object.freeze(cell === "" ? [] : cell.split(","));
}

function names(cell: string): readonly string[] {
  return Object.freeze(split(cell, ";").map(unescape));
}

/** `cell` split at each `separator` that is not escaped, the pieces left escaped; none for an empty cell. */
function split(cell: string, separator: string): string[] {
  if (cell === "") return [];
  const pieces: string[] = [];
  let start = 0;
  for (let at = 0; at < cell.length; at++) {
    const character = cell[at];
    if (character === "\\") at++;
    else if (character === separator) {
      pieces.push(cell.slice(start, at));
      start = at + 1;
    }
  }
  pieces.push(cell.slice(start));
  return pieces;
}

/** `text` with the Kotlin library's escapes undone: `\t` and `\n`, and any other character as itself. */
function unescape(text: string): string {
  if (!text.includes("\\")) return text;
  return text.replace(/\\(.?)/gsu, (_, next: string) => (next === "t" ? "\t" : next === "n" ? "\n" : next || "\\"));
}
