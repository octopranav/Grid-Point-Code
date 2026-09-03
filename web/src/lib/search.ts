// Typing a place name, without a search engine behind it.
//
// The index is one sorted file. Every name beginning with what somebody typed
// is contiguous in it, so a prefix search is a single byte range -- and which
// range is answered by a sparse table of every 512th line, fetched once.
//
// Nothing here is a query to anybody's service. The file sits beside the site
// on the same static host, and a hit carries the code, which *is* the
// coordinate: no second lookup to put a pin on a map.

/** A place the index knows about. */
export interface Place {
    /** As it is written locally: `Trá Mhór`. */
    name: string;
    /** Ten characters. The coordinate, not a key to one. */
    code: string;
    /** Where it is, for telling two places of the same name apart. */
    region: string;
}

interface Marks {
    stride: number;
    lines: number;
    bytes: number;
    regions: string[];
    /** `[folded name, byte offset]`, one per stride. */
    marks: [string, number][];
    built: string;
}

/**
 * What a name is reduced to for searching.
 *
 * **This must agree exactly with `scripts/build-names.mjs`**, which sorted the
 * file by it. A difference here is not a wrong answer, it is a binary search
 * walking off into the wrong part of a quarter-gigabyte file. `test-search.mjs`
 * holds the two to the same answers.
 */
export function fold(name: string): string {
    return name
        .toLowerCase()
        .normalize('NFKD')
        .replace(/[̀-ͯ]/g, '')
        .replace(/[^a-z0-9]+/g, ' ')
        .trim();
}

const base = () => import.meta.env.BASE_URL.replace(/\/$/, '');

let table: Promise<Marks | null> | null = null;

/**
 * The sparse table, fetched once and kept.
 *
 * Missing is not an error: the index is built by a workflow of its own and
 * published as a release asset, so a checkout that has never run it has no
 * index and the search simply says so.
 */
export function marks(): Promise<Marks | null> {
    table ??= fetch(`${base()}/names/names.index.json`)
        .then((response) => (response.ok ? (response.json() as Promise<Marks>) : null))
        .catch(() => null);
    return table;
}

/** The last mark at or before `folded`, which is where its block begins. */
function blockFor(found: Marks, folded: string): number {
    let low = 0;
    let high = found.marks.length - 1;
    let at = 0;
    while (low <= high) {
        const middle = (low + high) >> 1;
        if (found.marks[middle][0] <= folded) {
            at = middle;
            low = middle + 1;
        } else {
            high = middle - 1;
        }
    }
    return at;
}

/**
 * One line of the file. Tabs separate, so no field can contain one.
 *
 * `folded, importance, name, code, region`. Importance is read past rather than
 * read: it did its work in the sort, where it put the Toronto somebody meant
 * above the four others of that name. Order here is the file's order.
 */
function parse(line: string, regions: string[]): Place | null {
    const parts = line.split('\t');
    if (parts.length < 5) return null;
    return {
        name: parts[2],
        code: parts[3],
        region: regions[Number(parts[4])] ?? '',
    };
}

/**
 * Places whose name begins with `query`.
 *
 * Reads from the block the query lands in and keeps going while the lines still
 * match, because a prefix can span a block boundary -- `london` does not stop
 * being an answer because the 512th line fell in the middle of it.
 */
export async function search(query: string, most = 20): Promise<Place[] | null> {
    const folded = fold(query);
    if (folded.length < 2) return [];

    const found = await marks();
    if (!found) return null;

    const hits: Place[] = [];
    let block = blockFor(found, folded);

    // A bounded walk: a query matching more than a few blocks is a prefix so
    // short that the first twenty answers are as good as any others.
    for (let step = 0; step < 8 && block < found.marks.length; step += 1, block += 1) {
        const from = found.marks[block][1];
        const to = block + 1 < found.marks.length ? found.marks[block + 1][1] : found.bytes;

        let body: string;
        try {
            const response = await fetch(`${base()}/names/names.txt`, {
                headers: { Range: `bytes=${from}-${to - 1}` },
            });
            if (!response.ok && response.status !== 206) return hits;
            body = await response.text();
        } catch {
            return hits.length > 0 ? hits : null;
        }

        let sawMatch = false;
        for (const line of body.split('\n')) {
            if (!line) continue;
            const key = line.slice(0, line.indexOf('\t'));
            if (key < folded) continue;              // still before the run
            if (!key.startsWith(folded)) {
                // Past the run entirely: nothing later in the file can match.
                if (key > folded) return hits;
                continue;
            }
            sawMatch = true;
            const place = parse(line, found.regions);
            if (place) hits.push(place);
            if (hits.length >= most) return hits;
        }

        // If this block held no match and we are past the query, stop.
        if (!sawMatch && hits.length > 0) return hits;
    }

    return hits;
}
