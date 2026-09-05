--  Copyright 2017 Pranavkumar Patel
--
--  Licensed under the Apache License, Version 2.0 (the "License");
--  you may not use this file except in compliance with the License.
--  You may obtain a copy of the License at
--
--      http://www.apache.org/licenses/LICENSE-2.0
--
--  Unless required by applicable law or agreed to in writing, software
--  distributed under the License is distributed on an "AS IS" BASIS,
--  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
--  See the License for the specific language governing permissions and
--  limitations under the License.

-- Grid Point Code, in the database.
--
--     psql -d yourdb -f sql/gpc.sql
--
-- Plain SQL and PL/pgSQL. No extension to compile, nothing to install on the
-- server, and no superuser: it is a file of functions, and it works on a
-- managed instance where `CREATE EXTENSION` does not.
--
-- **Why this is worth having in the database rather than in front of it.**
-- The alphabet ascends in ASCII and the earlier characters are the more
-- significant, so comparing two codes as text gives the same answer as
-- comparing their positions in the traversal of the grid. That means an
-- ordinary B-tree on a `text` column is a spatial index:
--
--     CREATE INDEX ON places (code);
--
--     -- everything inside a cell, as a prefix
--     SELECT * FROM places WHERE code LIKE 'G3RJM%';
--     SELECT * FROM places WHERE gpc_contains('G3RJM', code);
--
--     -- and in spatial order, with no decoding and no custom collation
--     SELECT * FROM places ORDER BY code;
--
-- A prefix search on a B-tree is a range scan, so the first query is an index
-- scan over one contiguous run of the index. Nothing here needs PostGIS, a
-- GiST index, or a second column to keep in step. `sql/gpc_postgis.sql` adds
-- geometry bindings for installations that do have it.
--
-- Every function is IMMUTABLE and PARALLEL SAFE, which is what lets the
-- planner use them in an index expression and in a parallel scan.
--
-- The rules implemented here are SPEC.md, and the section numbers in the
-- comments are its section numbers. `sql/check.sql` runs the same conformance
-- vectors the four library ports are held to.

-- ---------------------------------------------------------------------------
-- Grid to code, and back. Sections 5.2 and 6.1.
--
-- The alphabet of section 4 is written into each function rather than kept in
-- a table. A table would be a second thing to install, and a function that
-- read one could not be IMMUTABLE, which would cost the index expression this
-- whole file exists for.

CREATE OR REPLACE FUNCTION gpc_grid_to_code(p_row bigint, p_col bigint)
    RETURNS text
    LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE
    AS $$
DECLARE
    alphabet CONSTANT text := '0123456789CDFGHJKLMNPRTWX';
    -- 5^(10-level), for level 1 to 10. A lookup rather than power(), which
    -- returns double precision and would put floating point in the one part
    -- of the format that has none.
    spans CONSTANT bigint[] := ARRAY[1953125, 390625, 78125, 15625, 3125,
                                     625, 125, 25, 5, 1];
    r1 int; c1 int; k int;
    sr int := 0; sc int := 0;
    span bigint; r int; c int; rr int; cc int;
    code text;
BEGIN
    IF p_row < 0 OR p_row > 7812499 THEN
        RAISE EXCEPTION 'GPC_LATITUDE' USING ERRCODE = '22023';
    END IF;
    IF p_col < 0 OR p_col > 11718749 THEN
        RAISE EXCEPTION 'GPC_LONGITUDE' USING ERRCODE = '22023';
    END IF;

    -- Level 1: a serpentine over the 24 blocks, west to east, snaking north.
    r1 := (p_row / spans[1])::int;
    c1 := (p_col / spans[1])::int;
    IF r1 % 2 = 0 THEN k := c1; ELSE k := 5 - c1; END IF;
    code := substr(alphabet, r1 * 6 + k + 1, 1);

    sr := r1;
    sc := c1;

    FOR level IN 2..10 LOOP
        IF level = 6 THEN                       -- the parity reset, 5.3
            sr := 0;
            sc := 0;
        END IF;

        span := spans[level];
        r := ((p_row / span) % 5)::int;
        c := ((p_col / span) % 5)::int;

        -- The order of these four is normative: the row is decided from the
        -- column parity before this level's column is added to it, and the
        -- column from the row parity after this level's row has been.
        IF sc % 2 = 0 THEN rr := r; ELSE rr := 4 - r; END IF;
        sr := sr + r;
        IF sr % 2 = 0 THEN cc := c; ELSE cc := 4 - c; END IF;
        sc := sc + c;

        code := code || substr(alphabet, rr * 5 + cc + 1, 1);
    END LOOP;

    RETURN code;
END;
$$;

COMMENT ON FUNCTION gpc_grid_to_code(bigint, bigint) IS
    'A grid row and column to the bare ten-character code. Section 5.2.';


CREATE OR REPLACE FUNCTION gpc_code_to_grid(p_code text)
    RETURNS bigint[]
    LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE
    AS $$
DECLARE
    alphabet CONSTANT text := '0123456789CDFGHJKLMNPRTWX';
    i int; r1 int; c1 int; k int; j int;
    sr int; sc int; r int; c int;
    grid_row bigint; grid_col bigint;
BEGIN
    IF length(p_code) <> 10 THEN
        RAISE EXCEPTION 'GPC_LENGTH' USING ERRCODE = '22023';
    END IF;

    i := position(substr(p_code, 1, 1) IN alphabet) - 1;
    IF i < 0 THEN
        RAISE EXCEPTION 'GPC_CHAR' USING ERRCODE = '22023';
    END IF;
    IF i > 23 THEN
        -- Index 24 is unreachable at position one, which is what leaves the
        -- reserved namespace. Section 9.
        RAISE EXCEPTION 'GPC_RESERVED' USING ERRCODE = '22023';
    END IF;

    r1 := i / 6;
    k := i % 6;
    IF r1 % 2 = 0 THEN c1 := k; ELSE c1 := 5 - k; END IF;

    grid_row := r1;
    grid_col := c1;
    sr := r1;
    sc := c1;

    FOR level IN 2..10 LOOP
        IF level = 6 THEN
            sr := 0;
            sc := 0;
        END IF;

        j := position(substr(p_code, level, 1) IN alphabet) - 1;
        IF j < 0 THEN
            RAISE EXCEPTION 'GPC_CHAR' USING ERRCODE = '22023';
        END IF;

        IF sc % 2 = 0 THEN r := j / 5; ELSE r := 4 - (j / 5); END IF;
        sr := sr + r;
        IF sr % 2 = 0 THEN c := j % 5; ELSE c := 4 - (j % 5); END IF;
        sc := sc + c;

        grid_row := grid_row * 5 + r;
        grid_col := grid_col * 5 + c;
    END LOOP;

    RETURN ARRAY[grid_row, grid_col];
END;
$$;

COMMENT ON FUNCTION gpc_code_to_grid(text) IS
    'The bare ten-character code back to a grid row and column. Section 6.1.';


-- ---------------------------------------------------------------------------
-- Parsing and normalisation. Section 8.

CREATE OR REPLACE FUNCTION gpc_normalise(p_code text)
    RETURNS text[]
    LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE
    AS $$
DECLARE
    star int;
    payload text;
    checked text := NULL;
    offender text;
BEGIN
    -- 1. Anything to parse at all. The only rejection before normalising.
    IF p_code IS NULL OR btrim(p_code, E' \t\n\r\f\v') = '' THEN
        RAISE EXCEPTION 'GPC_NULL' USING ERRCODE = '22023';
    END IF;

    -- 2. Split off the check character at the first star. Section 14.
    star := position('*' IN p_code);
    IF star > 0 THEN
        payload := substr(p_code, 1, star - 1);
        checked := substr(p_code, star + 1);
    ELSE
        payload := p_code;
    END IF;

    -- 3. Case-fold with ASCII rules only. Deliberately not upper(), which
    --    follows the database collation: in a Turkish one it maps `i` to `İ`,
    --    and the same code would be valid on one server and not on another.
    payload := translate(payload, 'abcdefghijklmnopqrstuvwxyz',
                                  'ABCDEFGHIJKLMNOPQRSTUVWXYZ');
    IF checked IS NOT NULL THEN
        checked := translate(checked, 'abcdefghijklmnopqrstuvwxyz',
                                      'ABCDEFGHIJKLMNOPQRSTUVWXYZ');
    END IF;

    -- 4. Presentation characters, anywhere and in any number.
    payload := regexp_replace(payload, '[#-]|\s', '', 'g');
    IF checked IS NOT NULL THEN
        checked := regexp_replace(checked, '[#-]|\s', '', 'g');
    END IF;

    -- 5. The alias table. O I S Z A E B V only: U, Q and Y are not aliased,
    --    and L is a real symbol that must never be read as 1.
    payload := translate(payload, 'OISZAEBV', '0152438W');
    IF checked IS NOT NULL THEN
        checked := translate(checked, 'OISZAEBV', '0152438W');
    END IF;

    -- Every letter left is either a symbol or a mistake.
    offender := substring(payload FROM '[^0123456789CDFGHJKLMNPRTWX]');
    IF offender IS NOT NULL THEN
        RAISE EXCEPTION 'GPC_CHAR' USING ERRCODE = '22023',
            DETAIL = format('character %L is not in the alphabet', offender);
    END IF;
    -- The check part is deliberately not held to the alphabet. A `Q` after the
    -- star is a check that cannot be right rather than a character that has no
    -- business in a code, and section 9 classifies it as GPC_CHECK.
    --
    -- An empty payload is not GPC_NULL either: `#` alone survived step 1,
    -- because a hash is not whitespace, and comes out as a length problem.
    RETURN ARRAY[payload, checked];
END;
$$;

COMMENT ON FUNCTION gpc_normalise(text) IS
    'Case-folds, strips separators, applies the alias table. Returns '
    '[payload, check]; the check is NULL when the input carried none. '
    'Section 8.';


-- ---------------------------------------------------------------------------
-- The check character. Section 14.
--
-- GF(25) as GF(5) extended by a root of t^2 + t + 2. An element a + b*t is
-- represented by the symbol index b*5 + a, which is the same way section 5.2
-- builds a symbol out of a row and a column.

CREATE OR REPLACE FUNCTION gpc_gf_multiply(x int, y int)
    RETURNS int
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $$
    -- t^2 = 4t + 3, which is where the 3 and the 4 come from.
    SELECT (((x % 5) * (y / 5) + (x / 5) * (y % 5) + 4 * (x / 5) * (y / 5)) % 5) * 5
         + (((x % 5) * (y % 5) + 3 * (x / 5) * (y / 5)) % 5)
$$;


CREATE OR REPLACE FUNCTION gpc_check_character(p_payload text)
    RETURNS text
    LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE
    AS $$
DECLARE
    alphabet CONSTANT text := '0123456789CDFGHJKLMNPRTWX';
    -- t^i for i = 1..10, as symbol indices. Fixed by the field.
    weights CONSTANT int[] := ARRAY[5, 23, 22, 17, 24, 2, 10, 16, 19, 9];
    syndrome int := 0;
    term int;
    v int;
BEGIN
    IF length(p_payload) <> 10 THEN
        RAISE EXCEPTION 'GPC_LENGTH' USING ERRCODE = '22023';
    END IF;

    FOR i IN 1..10 LOOP
        v := position(substr(p_payload, i, 1) IN alphabet) - 1;
        IF v < 0 THEN
            RAISE EXCEPTION 'GPC_CHAR' USING ERRCODE = '22023';
        END IF;
        term := gpc_gf_multiply(weights[i], v);
        -- Addition is componentwise modulo 5.
        syndrome := ((syndrome / 5 + term / 5) % 5) * 5
                  + ((syndrome % 5 + term % 5) % 5);
    END LOOP;

    -- The check character is the syndrome multiplied by t, index 5.
    RETURN substr(alphabet, gpc_gf_multiply(5, syndrome) + 1, 1);
END;
$$;

COMMENT ON FUNCTION gpc_check_character(text) IS
    'The optional GF(25) check character for a bare ten-character payload. '
    'Section 14.3.';


-- ---------------------------------------------------------------------------
-- Encoding and decoding. Sections 5.1, 5.4, 6.1 and 6.2.

CREATE OR REPLACE FUNCTION gpc_encode(
    p_latitude double precision,
    p_longitude double precision,
    p_formatted boolean DEFAULT true)
    RETURNS text
    LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE
    AS $$
DECLARE
    latitude double precision := p_latitude;
    longitude double precision := p_longitude;
    grid_row bigint;
    grid_col bigint;
    code text;
BEGIN
    IF latitude < -90.0 OR latitude > 90.0 OR latitude <> latitude THEN
        RAISE EXCEPTION 'GPC_LATITUDE' USING ERRCODE = '22023';
    END IF;
    IF longitude < -180.0 OR longitude > 180.0 OR longitude <> longitude THEN
        RAISE EXCEPTION 'GPC_LONGITUDE' USING ERRCODE = '22023';
    END IF;

    -- The antimeridian is one line, named from the west. Section 2.
    IF longitude = 180.0 THEN
        longitude := -180.0;
    END IF;

    -- The only floating point in the format, evaluated as section 7 pins it.
    grid_row := floor((latitude + 90.0::double precision)
                      * 7812500.0::double precision / 180.0::double precision);
    grid_col := floor((longitude + 180.0::double precision)
                      * 11718750.0::double precision / 360.0::double precision);

    -- Reached by exactly one input per axis: latitude +90, and longitude +180
    -- had it not already been normalised. It is what makes the poles encode
    -- rather than index past the end of the grid.
    grid_row := least(greatest(grid_row, 0), 7812499);
    grid_col := least(greatest(grid_col, 0), 11718749);

    code := gpc_grid_to_code(grid_row, grid_col);
    IF p_formatted THEN
        RETURN gpc_format(code);
    END IF;
    RETURN code;
END;
$$;

COMMENT ON FUNCTION gpc_encode(double precision, double precision, boolean) IS
    'A coordinate to a code. Formatted by default. Sections 5.1 and 5.2.';


CREATE OR REPLACE FUNCTION gpc_format(p_payload text)
    RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $$ SELECT '#' || substr(p_payload, 1, 5) || '-' || substr(p_payload, 6, 5) $$;

COMMENT ON FUNCTION gpc_format(text) IS
    'The presentation form, #XXXXX-XXXXX. Section 5.4.';


CREATE OR REPLACE FUNCTION gpc_decode(
    p_code text,
    OUT latitude double precision,
    OUT longitude double precision)
    LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE
    AS $$
DECLARE
    parts text[];
    grid bigint[];
    lat_e8 bigint;
    lng_e8 bigint;
BEGIN
    parts := gpc_normalise(p_code);
    IF parts[2] IS NOT NULL
       AND (length(parts[2]) <> 1 OR parts[2] <> gpc_check_character(parts[1])) THEN
        -- A code that fails the check fails everywhere: it must not be
        -- accepted by discarding the check. Section 14.3.
        RAISE EXCEPTION 'GPC_CHECK' USING ERRCODE = '22023';
    END IF;

    grid := gpc_code_to_grid(parts[1]);

    -- The centre of the cell, as an exact count of 1e-8 degrees, rounded to
    -- six places. Ties are unreachable, so the rounding mode cannot matter.
    lat_e8 := (2 * grid[1] + 1) * 1152 - 9000000000;
    lng_e8 := (2 * grid[2] + 1) * 1536 - 18000000000;

    latitude := (round(lat_e8::numeric / 100) / 1000000)::double precision;
    longitude := (round(lng_e8::numeric / 100) / 1000000)::double precision;
END;
$$;

COMMENT ON FUNCTION gpc_decode(text) IS
    'A code to the centre of the cell it names. Sections 6.1 and 6.2.';


CREATE OR REPLACE FUNCTION gpc_decode_area(
    p_code text,
    OUT south double precision,
    OUT west double precision,
    OUT north double precision,
    OUT east double precision)
    LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE
    AS $$
DECLARE
    grid bigint[];
BEGIN
    grid := gpc_code_to_grid((gpc_normalise(p_code))[1]);
    south := grid[1] * 180.0::double precision / 7812500.0::double precision - 90.0;
    north := (grid[1] + 1) * 180.0::double precision / 7812500.0::double precision - 90.0;
    west := grid[2] * 360.0::double precision / 11718750.0::double precision - 180.0;
    east := (grid[2] + 1) * 360.0::double precision / 11718750.0::double precision - 180.0;
END;
$$;

COMMENT ON FUNCTION gpc_decode_area(text) IS
    'The boundaries of the cell a code names. Section 6.3.';


-- ---------------------------------------------------------------------------
-- Cells and containment. This is the part that makes an ordinary index
-- spatial, so it is the part worth having in the database.

CREATE OR REPLACE FUNCTION gpc_cell(p_code text, p_level int)
    RETURNS text
    LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE
    AS $$
DECLARE
    payload text;
BEGIN
    IF p_level < 1 OR p_level > 10 THEN
        RAISE EXCEPTION 'GPC_LEVEL' USING ERRCODE = '22023';
    END IF;
    payload := (gpc_normalise(p_code))[1];
    IF length(payload) < p_level THEN
        RAISE EXCEPTION 'GPC_LENGTH' USING ERRCODE = '22023';
    END IF;
    IF substr(payload, 1, 1) = 'X' THEN
        RAISE EXCEPTION 'GPC_RESERVED' USING ERRCODE = '22023';
    END IF;
    RETURN substr(payload, 1, p_level);
END;
$$;

COMMENT ON FUNCTION gpc_cell(text, int) IS
    'The first `level` characters: the cell containing the code, bare. '
    'Section 18.1.';


CREATE OR REPLACE FUNCTION gpc_contains(p_cell text, p_code text)
    RETURNS boolean
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $$
    -- The prefix test and nothing more. What the ordering buys is that this
    -- is the whole of containment: no decoding, no geometry.
    SELECT p_cell <> ''
       AND length(p_cell) <= 10
       AND left((gpc_normalise(p_code))[1], length((gpc_normalise(p_cell))[1]))
           = (gpc_normalise(p_cell))[1]
$$;

COMMENT ON FUNCTION gpc_contains(text, text) IS
    'Whether a code lies inside a cell, as a prefix test. Section 18.2.';


-- ---------------------------------------------------------------------------
-- Classification. Section 9.

CREATE OR REPLACE FUNCTION gpc_classify(p_code text)
    RETURNS text[]
    LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE
    AS $$
DECLARE
    parts text[];
BEGIN
    BEGIN
        parts := gpc_normalise(p_code);
    EXCEPTION
        WHEN SQLSTATE '22023' THEN
            RETURN ARRAY['INVALID', SQLERRM];
    END;

    IF length(parts[1]) <> 10 THEN
        RETURN ARRAY['INVALID', 'GPC_LENGTH'];
    END IF;

    -- A supplied check is verified before anything is said about what the code
    -- names: a reserved code with a wrong check is a wrong check. NULL here
    -- means no star was typed, which is not the same as a star with nothing
    -- after it -- that is a check, and an empty one cannot be right.
    IF parts[2] IS NOT NULL THEN
        IF length(parts[2]) <> 1
           OR parts[2] <> gpc_check_character(parts[1]) THEN
            RETURN ARRAY['INVALID', 'GPC_CHECK'];
        END IF;
    END IF;

    IF substr(parts[1], 1, 1) = 'X' THEN
        RETURN ARRAY['RESERVED', ''];
    END IF;

    RETURN ARRAY['GEOMETRIC', ''];
END;
$$;

COMMENT ON FUNCTION gpc_classify(text) IS
    'What an input is: [GEOMETRIC|RESERVED|INVALID, reason]. The reason is '
    'empty unless the class is INVALID. Section 9.';


-- ---------------------------------------------------------------------------
-- Validity, for callers that would rather have a boolean than an exception.

CREATE OR REPLACE FUNCTION gpc_is_valid(p_code text)
    RETURNS boolean
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $$ SELECT (gpc_classify(p_code))[1] = 'GEOMETRIC' $$;

COMMENT ON FUNCTION gpc_is_valid(text) IS
    'Whether a code is a usable geometric code. Reserved is not valid here: '
    'it parses, but it names nothing. Section 9.';
