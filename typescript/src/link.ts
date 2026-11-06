import type { CodePointRange } from "./kinds.js";

/**
 * A code one entry gives for another, resolved when the catalog is read: to the entry when the catalog has it,
 * or kept as written when it does not.
 *
 * @internal
 */
export type Link<T> =
  | { readonly kind: "known"; readonly entry: T }
  | { readonly kind: "unknown"; readonly code: string };

/** @internal The entry `link` leads to, or `null` when the catalog does not have it. */
export function known<T>(link: Link<T> | null): T | null {
  return link?.kind === "known" ? link.entry : null;
}

/** @internal Compares two codes as the other readers do, by UTF-16 code unit. */
export function compareCodes(a: string, b: string): number {
  if (a === b) return 0;
  return a < b ? -1 : 1;
}

/** @internal Whether one of `ranges`, sorted and apart, holds `codePoint`. */
export function holds(ranges: readonly CodePointRange[], codePoint: number): boolean {
  let low = 0;
  let high = ranges.length - 1;
  while (low <= high) {
    const middle = (low + high) >>> 1;
    const range = ranges[middle];
    if (range === undefined) return false;
    if (codePoint < range.first) high = middle - 1;
    else if (codePoint > range.last) low = middle + 1;
    else return true;
  }
  return false;
}
