/**
 * A small seeded pseudo-random generator (mulberry32) returning numbers in [0, 1). The same
 * seed gives the same sequence, so UMAP draws the same picture for the same data every time.
 * Not suitable for anything security-related.
 */
export function seededRandom(seed: number): () => number {
  let state = seed | 0;
  return () => {
    state = (state + 0x6d2b79f5) | 0;
    let mixed = Math.imul(state ^ (state >>> 15), 1 | state);
    mixed = (mixed + Math.imul(mixed ^ (mixed >>> 7), 61 | mixed)) ^ mixed;
    return ((mixed ^ (mixed >>> 14)) >>> 0) / 4_294_967_296;
  };
}
