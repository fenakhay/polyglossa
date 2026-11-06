/**
 * English Wiktionary's languages, language families and scripts.
 *
 * ```ts
 * import { Language } from "polyglossa";
 *
 * const english = Language.of("en");
 * english.name           // "English"
 * english.family?.name   // "Anglic"
 * Language.get("xx")     // null
 * ```
 *
 * @module
 */

export { Catalog } from "./catalog.js";
export { Language, LanguageFamily, Script } from "./entries.js";
export {
  NotACatalogError,
  PolyglossaError,
  UnknownFamilyError,
  UnknownLanguageError,
  UnknownScriptError,
} from "./errors.js";
export {
  LANGUAGE_TYPES,
  SCRIPT_DIRECTIONS,
  isLanguageType,
  isScriptDirection,
  type CodePointRange,
  type LanguageType,
  type ScriptDirection,
  type Variety,
  type WikidataItem,
} from "./kinds.js";
