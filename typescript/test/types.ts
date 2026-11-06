import {
  Catalog,
  LANGUAGE_TYPES,
  Language,
  LanguageFamily,
  NotACatalogError,
  PolyglossaError,
  Script,
  UnknownLanguageError,
  isLanguageType,
  type LanguageType,
  type ScriptDirection,
  type WikidataItem,
} from "../src/index.js";

type Equal<A, B> = [A] extends [B] ? ([B] extends [A] ? true : false) : false;
type Expect<T extends true> = T;
type IsMutableArray<T> = T extends unknown[] ? true : false;

export type Kinds = [
  Expect<Equal<LanguageType, "regular" | "reconstructed" | "appendix-constructed" | "etymology-only">>,
  Expect<Equal<ScriptDirection, "ltr" | "rtl" | "vertical-ltr" | "vertical-rtl">>,
  Expect<Equal<(typeof LANGUAGE_TYPES)[number], LanguageType>>,
  Expect<Equal<WikidataItem, `Q${string}`>>,
];

export type Lookups = [
  Expect<Equal<ReturnType<typeof Language.get>, Language | null>>,
  Expect<Equal<ReturnType<typeof Language.of>, Language>>,
  Expect<Equal<ReturnType<typeof Script.detect>, Script | null>>,
  Expect<Equal<ReturnType<Language["detectScript"]>, Script | null>>,
  Expect<Equal<ReturnType<Catalog["family"]>, LanguageFamily | null>>,
  Expect<Equal<Language["wikidata"], WikidataItem | null>>,
];

export type ReadOnly = [
  Expect<Equal<IsMutableArray<Language["ancestors"]>, false>>,
  Expect<Equal<IsMutableArray<Language["scripts"]>, false>>,
  Expect<Equal<IsMutableArray<LanguageFamily["languages"]>, false>>,
  Expect<Equal<IsMutableArray<Catalog["languages"]>, false>>,
  Expect<Equal<Script["localNames"], ReadonlyMap<Language, string>>>,
];

export type Errors = [
  Expect<Equal<UnknownLanguageError extends PolyglossaError ? true : false, true>>,
  Expect<Equal<NotACatalogError["line"], number>>,
];

export function narrows(value: unknown): LanguageType | null {
  return isLanguageType(value) ? value : null;
}
