// The alphabet of section 4.
//
// Written out rather than read from the library, which keeps it private. It
// cannot change without changing the format, so this is a constant in the same
// sense the grid is.
//
// No vowels, so a code cannot spell a word. No `I`, `O`, `S`, `Z`, `B`, `A`,
// `E`, `U`, `Q`, `V` or `Y`, so the pairs every font guide warns about -- 0/O
// and 1/l -- cannot occur in a code at all.
export const ALPHABET = '0123456789CDFGHJKLMNPRTWX';

/** Every symbol that is not the one already there. */
export const insteadOf = (symbol: string) =>
    [...ALPHABET].filter((candidate) => candidate !== symbol);
