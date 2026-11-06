/** Every kind of language Wiktionary has, as {@link LanguageType} names them. */
export const LANGUAGE_TYPES = ["regular", "reconstructed", "appendix-constructed", "etymology-only"] as const;

/** What kind of language Wiktionary takes one to be, which decides where its entries may live. */
export type LanguageType = (typeof LANGUAGE_TYPES)[number];

/** Every way a script runs, as {@link ScriptDirection} names them. */
export const SCRIPT_DIRECTIONS = ["ltr", "rtl", "vertical-ltr", "vertical-rtl"] as const;

/** Which way a script runs. */
export type ScriptDirection = (typeof SCRIPT_DIRECTIONS)[number];

/** A Wikidata item, as `Q1860`. */
export type WikidataItem = `Q${string}`;

/** Whether `value` is one of the {@link LANGUAGE_TYPES}. */
export function isLanguageType(value: unknown): value is LanguageType {
  return LANGUAGE_TYPES.some((type) => type === value);
}

/** Whether `value` is one of the {@link SCRIPT_DIRECTIONS}. */
export function isScriptDirection(value: unknown): value is ScriptDirection {
  return SCRIPT_DIRECTIONS.some((direction) => direction === value);
}

/** A variety of a language Wiktionary names without giving it a code, such as a dialect. */
export interface Variety {
  readonly name: string;
  readonly otherNames: readonly string[];
}

/** The code points from `first` to `last`, both included. */
export interface CodePointRange {
  readonly first: number;
  readonly last: number;
}
