/** What every error polyglossa throws is. */
export class PolyglossaError extends Error {
  override readonly name: string = "PolyglossaError";
}

/** Wiktionary has no language with this code. */
export class UnknownLanguageError extends PolyglossaError {
  override readonly name = "UnknownLanguageError";

  /** @param code The code that was looked up. */
  constructor(readonly code: string) {
    super(`polyglossa: unknown language ${JSON.stringify(code)}`);
  }
}

/** Wiktionary has no family with this code. */
export class UnknownFamilyError extends PolyglossaError {
  override readonly name = "UnknownFamilyError";

  /** @param code The code that was looked up. */
  constructor(readonly code: string) {
    super(`polyglossa: unknown family ${JSON.stringify(code)}`);
  }
}

/** Wiktionary has no script with this code. */
export class UnknownScriptError extends PolyglossaError {
  override readonly name = "UnknownScriptError";

  /** @param code The code that was looked up. */
  constructor(readonly code: string) {
    super(`polyglossa: unknown script ${JSON.stringify(code)}`);
  }
}

/** The text is not a catalog polyglossa wrote. */
export class NotACatalogError extends PolyglossaError {
  override readonly name = "NotACatalogError";

  /**
   * @param line The line, counted from 1, that could not be read.
   * @param reason What is wrong with it.
   */
  constructor(
    readonly line: number,
    readonly reason: string,
  ) {
    super(`polyglossa: not a catalog polyglossa wrote: line ${line}: ${reason}`);
  }
}
