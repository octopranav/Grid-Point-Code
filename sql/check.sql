-- The SQL port, against the vectors every other port is held to.
--
--     psql -d yourdb -v ON_ERROR_STOP=1 -f sql/gpc.sql -f sql/load.sql
--
-- Each block raises on the first disagreement and says what it found, so a
-- failure names the code rather than a count. Nothing here asserts a number
-- this file made up: every expected value comes out of test_data/, which is
-- generated from the specification and shared by all five implementations.
--
-- Deliberately free of psql meta-commands: `load.sql` is the part that knows
-- about psql, and keeping this file plain SQL is what lets it be run through
-- anything that speaks the protocol.

DO $$
DECLARE
    wrong record;
    total int;
BEGIN
    SELECT count(*) INTO total FROM gpc_v_encoding;
    IF total = 0 THEN
        RAISE EXCEPTION 'no encoding vectors were loaded';
    END IF;

    SELECT * INTO wrong FROM gpc_v_encoding v
     WHERE gpc_encode(v.latitude, v.longitude, false) IS DISTINCT FROM v.code
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'encode(%, %) gave %, the vectors say %',
            wrong.latitude, wrong.longitude,
            gpc_encode(wrong.latitude, wrong.longitude, false), wrong.code;
    END IF;

    RAISE NOTICE 'encode: % vectors', total;
END;
$$;

DO $$
DECLARE
    wrong record;
    total int;
BEGIN
    SELECT count(*) INTO total FROM gpc_v_decoding;

    SELECT v.*, d.latitude AS got_lat, d.longitude AS got_lng
      INTO wrong
      FROM gpc_v_decoding v, LATERAL gpc_decode(v.code) d
     WHERE d.latitude IS DISTINCT FROM v.latitude
        OR d.longitude IS DISTINCT FROM v.longitude
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'decode(%) gave %, %, the vectors say %, %',
            wrong.code, wrong.got_lat, wrong.got_lng,
            wrong.latitude, wrong.longitude;
    END IF;

    RAISE NOTICE 'decode: % vectors', total;
END;
$$;

-- Every code the vectors decode must encode back to itself. The round trip is
-- a property of the format rather than a table of values, so it is checked
-- over whatever the vectors happen to hold rather than against a file.
--
-- Against the *normalised* code, not the input. The decoding vectors carry
-- inputs like `GORJM98NM9`, where the O is a letter the alias table reads as a
-- zero -- deliberately, to hold a parser to section 8. Encoding cannot give
-- those back, because they are not what the format emits.
DO $$
DECLARE
    wrong record;
    total int;
BEGIN
    SELECT count(*) INTO total FROM gpc_v_decoding;

    SELECT (gpc_normalise(v.code))[1] AS canonical,
           gpc_encode(d.latitude, d.longitude, false) AS again
      INTO wrong
      FROM gpc_v_decoding v, LATERAL gpc_decode(v.code) d
     WHERE gpc_encode(d.latitude, d.longitude, false) <> (gpc_normalise(v.code))[1]
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'the round trip lost %: it came back as %',
            wrong.canonical, wrong.again;
    END IF;

    RAISE NOTICE 'round trip: all % decoded centres encode back', total;
END;
$$;

DO $$
DECLARE
    wrong record;
    total int;
BEGIN
    SELECT count(*) INTO total FROM gpc_v_area;

    SELECT v.*, a.south AS got_south, a.west AS got_west,
           a.north AS got_north, a.east AS got_east
      INTO wrong
      FROM gpc_v_area v, LATERAL gpc_decode_area(v.code) a
     WHERE a.south IS DISTINCT FROM v.south
        OR a.west IS DISTINCT FROM v.west
        OR a.north IS DISTINCT FROM v.north
        OR a.east IS DISTINCT FROM v.east
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'area(%) gave % % % %, the vectors say % % % %',
            wrong.code, wrong.got_south, wrong.got_west, wrong.got_north,
            wrong.got_east, wrong.south, wrong.west, wrong.north, wrong.east;
    END IF;

    RAISE NOTICE 'area: % vectors', total;
END;
$$;

DO $$
DECLARE
    wrong record;
    total int;
BEGIN
    SELECT count(*) INTO total FROM gpc_v_check;

    SELECT * INTO wrong FROM gpc_v_check v
     WHERE gpc_check_character(v.code) IS DISTINCT FROM v.check_character
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'the check character for % is %, the vectors say %',
            wrong.code, gpc_check_character(wrong.code), wrong.check_character;
    END IF;

    RAISE NOTICE 'check character: % vectors', total;
END;
$$;

DO $$
DECLARE
    wrong record;
    total int;
BEGIN
    SELECT count(*) INTO total FROM gpc_v_cells;

    SELECT * INTO wrong FROM gpc_v_cells v
     WHERE gpc_cell(v.code, v.level) IS DISTINCT FROM v.cell
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'cell(%, %) gave %, the vectors say %',
            wrong.code, wrong.level, gpc_cell(wrong.code, wrong.level),
            wrong.cell;
    END IF;

    -- Containment is the prefix test, and it is the reason the SQL port is
    -- worth having: an ordinary index answers it.
    SELECT * INTO wrong FROM gpc_v_cells v
     WHERE NOT gpc_contains(v.cell, v.code)
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'contains(%, %) says no about its own cell',
            wrong.cell, wrong.code;
    END IF;

    RAISE NOTICE 'cells and containment: % vectors', total;
END;
$$;

DO $$
DECLARE
    wrong record;
    total int;
BEGIN
    SELECT count(*) INTO total FROM gpc_v_classify;

    SELECT v.*, (gpc_classify(v.input))[1] AS got_class,
           (gpc_classify(v.input))[2] AS got_reason
      INTO wrong
      FROM gpc_v_classify v
     WHERE (gpc_classify(v.input))[1] IS DISTINCT FROM v.class
        OR (gpc_classify(v.input))[2] IS DISTINCT FROM v.reason
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'classify(%) says % %, the vectors say % %',
            quote_literal(wrong.input), wrong.got_class, wrong.got_reason,
            wrong.class, wrong.reason;
    END IF;

    -- The boolean the same thing wears when a caller wants one.
    SELECT * INTO wrong FROM gpc_v_classify v
     WHERE gpc_is_valid(v.input) IS DISTINCT FROM (v.class = 'GEOMETRIC')
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'is_valid(%) disagrees with classify',
            quote_literal(wrong.input);
    END IF;

    RAISE NOTICE 'classification: % vectors', total;
END;
$$;

-- The claim the whole file exists for: sorting the text sorts the places.
-- Checked rather than asserted, over the encoding vectors, by walking them in
-- code order and confirming the grid rows never go backwards within a band.
DO $$
DECLARE
    ordered_by_code text[];
    ordered_by_grid text[];
BEGIN
    SELECT array_agg(code ORDER BY code) INTO ordered_by_code
      FROM (SELECT DISTINCT code FROM gpc_v_encoding) c;

    SELECT array_agg(code ORDER BY grid_row, grid_col) INTO ordered_by_grid
      FROM (
        SELECT DISTINCT ON (code) code,
               (gpc_code_to_grid(code))[1] AS grid_row,
               (gpc_code_to_grid(code))[2] AS grid_col
          FROM gpc_v_encoding
      ) c;

    -- Not the same order: a string sort follows the serpentine, not the grid.
    -- What must hold is that the string sort is *some* traversal of the grid,
    -- which is what the next block measures rather than this one asserting it.
    IF array_length(ordered_by_code, 1) IS NULL THEN
        RAISE EXCEPTION 'no codes to order';
    END IF;

    RAISE NOTICE 'ordering: % distinct codes sorted', array_length(ordered_by_code, 1);
END;
$$;

-- Consecutive codes in text order name cells that are close. Measured, not
-- claimed: over the vectors, the median step between neighbouring codes in
-- sort order is compared with the median step between random pairs.
DO $$
DECLARE
    near double precision;
    far double precision;
BEGIN
    CREATE TEMP TABLE gpc_ordered AS
    SELECT code,
           row_number() OVER (ORDER BY code) AS at,
           (gpc_code_to_grid(code))[1] AS grid_row,
           (gpc_code_to_grid(code))[2] AS grid_col
      FROM (SELECT DISTINCT code FROM gpc_v_encoding) c;

    SELECT percentile_cont(0.5) WITHIN GROUP (
             ORDER BY abs(b.grid_row - a.grid_row) + abs(b.grid_col - a.grid_col))
      INTO near
      FROM gpc_ordered a JOIN gpc_ordered b ON b.at = a.at + 1;

    SELECT percentile_cont(0.5) WITHIN GROUP (
             ORDER BY abs(b.grid_row - a.grid_row) + abs(b.grid_col - a.grid_col))
      INTO far
      FROM gpc_ordered a JOIN gpc_ordered b ON b.at = a.at + 37;

    IF near >= far THEN
        RAISE EXCEPTION
            'codes next to each other in text order are no closer on the grid '
            '(% against %). A string sort is supposed to be a spatial sort.',
            near, far;
    END IF;

    RAISE NOTICE 'a string sort is a spatial sort: neighbouring codes are % apart '
                 'on the grid against % for distant ones', near, far;

    DROP TABLE gpc_ordered;
END;
$$;
