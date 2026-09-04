-- The claim the SQL port exists for, checked against the planner.
--
-- "A plain B-tree on a text column is a spatial index" is easy to say and easy
-- to be wrong about, because whether it is true depends on a thing nobody
-- thinks about until it bites: the collation.
--
-- A code's ordering property is a property of *bytes*. The alphabet ascends in
-- ASCII, so byte order is traversal order. A database whose default collation
-- is `en_US.UTF-8` does not sort bytes -- it sorts the way a person would
-- alphabetise, and it will not use a plain B-tree for `LIKE 'G3RJM%'` at all.
-- Both problems disappear under `COLLATE "C"`, which is what the README tells
-- callers to declare and what this file proves is necessary.
--
-- So: build a table the size of a real one, and ask the planner what it would
-- do. An assertion about a plan is worth more than an assertion about a result,
-- because the result is right either way and only the plan says whether the
-- index was any use.

DO $$
DECLARE
    -- json rather than text: EXPLAIN in text form is one row per plan line and
    -- `INTO` keeps only the first, which is the node on top and says nothing
    -- about how the rows underneath it were found. json is a single row.
    plan text;
    rows_made int;
BEGIN
    CREATE TEMP TABLE gpc_places (
        id int,
        -- Byte order, explicitly. This is the whole trick, and leaving it to
        -- the database's default is the mistake this file exists to catch.
        code text COLLATE "C"
    );

    -- Codes spread over the world, generated rather than random so a failure
    -- here reproduces. Two hundred thousand is enough that a sequential scan
    -- is a real choice the planner could make and is rejecting on merit.
    INSERT INTO gpc_places
    SELECT i,
           -- bigint before multiplying: 200000 * 104729 leaves int4 behind.
           gpc_grid_to_code((i::bigint * 7919) % 7812500,
                            (i::bigint * 104729) % 11718750)
      FROM generate_series(1, 200000) AS i;

    GET DIAGNOSTICS rows_made = ROW_COUNT;

    CREATE INDEX gpc_places_code ON gpc_places (code);
    ANALYZE gpc_places;

    -- 1. A cell is a prefix, and a prefix must be a range scan.
    EXECUTE 'EXPLAIN (FORMAT json) SELECT count(*) FROM gpc_places '
            'WHERE code LIKE ''G3RJM%''' INTO plan;

    IF plan NOT LIKE '%Index%' THEN
        RAISE EXCEPTION
            'a prefix query on % rows did not use the index. The plan was: %',
            rows_made, plan;
    END IF;
    RAISE NOTICE 'a cell prefix is an index scan';

    -- 2. The same question asked through the function, which is the form a
    --    caller is more likely to write.
    EXECUTE 'EXPLAIN (FORMAT json) SELECT count(*) FROM gpc_places '
            'WHERE code >= ''G3RJM'' AND code < ''G3RJN''' INTO plan;

    IF plan NOT LIKE '%Index%' THEN
        RAISE EXCEPTION 'a bounded range did not use the index: %', plan;
    END IF;
    RAISE NOTICE 'a bounded range is an index scan';

    -- 3. Spatial order, with nothing sorted at read time. If a Sort appears
    --    here the index is not in traversal order and the ordering property
    --    has stopped paying for itself.
    EXECUTE 'EXPLAIN (FORMAT json) SELECT code FROM gpc_places '
            'ORDER BY code LIMIT 100' INTO plan;

    IF plan LIKE '%Sort%' THEN
        RAISE EXCEPTION 'reading in spatial order needed a sort: %', plan;
    END IF;
    RAISE NOTICE 'spatial order costs no sort';
END;
$$;

-- And the property itself: byte order over codes is traversal order over the
-- grid. Checked by walking the table in code order and confirming that
-- neighbours in that order are close on the grid, which is the only thing
-- "a string sort is a spatial sort" can be made to mean.
DO $$
DECLARE
    near double precision;
    far double precision;
BEGIN
    CREATE TEMP TABLE gpc_walk AS
    SELECT row_number() OVER (ORDER BY code) AS at,
           (gpc_code_to_grid(code))[1] AS grid_row,
           (gpc_code_to_grid(code))[2] AS grid_col
      FROM (SELECT code FROM gpc_places ORDER BY code LIMIT 20000) c;

    SELECT percentile_cont(0.5) WITHIN GROUP (
             ORDER BY abs(b.grid_row - a.grid_row) + abs(b.grid_col - a.grid_col))
      INTO near
      FROM gpc_walk a JOIN gpc_walk b ON b.at = a.at + 1;

    SELECT percentile_cont(0.5) WITHIN GROUP (
             ORDER BY abs(b.grid_row - a.grid_row) + abs(b.grid_col - a.grid_col))
      INTO far
      FROM gpc_walk a JOIN gpc_walk b ON b.at = a.at + 500;

    IF near >= far THEN
        RAISE EXCEPTION
            'neighbours in code order are % apart on the grid and distant ones '
            '%. Sorting the text is not sorting the places.', near, far;
    END IF;

    RAISE NOTICE 'byte order is traversal order: neighbours % apart, distant rows %',
        round(near), round(far);
END;
$$;

-- The collation advice, demonstrated rather than repeated.
--
-- The check that used to be here compared the database's own default with byte
-- order and reported which it was. On a runner whose database is already byte
-- ordered that passes without touching the thing it is about, so it said
-- nothing on the only machine that runs it.
--
-- This builds a column under a collation that is definitely not byte order and
-- asks the two questions that matter. The answers were a surprise, and the
-- README now says what they are rather than what seemed likely.
DO $$
DECLARE
    plan text;
    differ int;
    total int;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_collation WHERE collname = 'und-x-icu') THEN
        RAISE NOTICE 'no ICU collation on this build; the collation check is skipped';
        RETURN;
    END IF;

    CREATE TEMP TABLE gpc_icu (code text COLLATE "und-x-icu");
    INSERT INTO gpc_icu SELECT code FROM gpc_places;
    CREATE INDEX gpc_icu_code ON gpc_icu (code);
    ANALYZE gpc_icu;
    SELECT count(*) INTO total FROM gpc_icu;

    -- 1. The order itself. For this alphabet -- digits and consonants, no
    --    vowels and no lower case -- a human-language collation turns out to
    --    agree with byte order at every position. Worth knowing, and worth
    --    knowing it was checked rather than assumed.
    SELECT count(*) FILTER (WHERE a <> b) INTO differ
      FROM (SELECT row_number() OVER (ORDER BY code COLLATE "C") AS n, code AS a
              FROM gpc_icu) one
      JOIN (SELECT row_number() OVER (ORDER BY code COLLATE "und-x-icu") AS n, code AS b
              FROM gpc_icu) other USING (n);

    IF differ > 0 THEN
        RAISE NOTICE 'ICU and byte order disagree at % of % positions: ORDER BY '
                     'needs COLLATE "C" as well as the index', differ, total;
    ELSE
        RAISE NOTICE 'ICU and byte order agree at all % positions', total;
    END IF;

    -- 2. The index, which is where it actually costs something. A prefix LIKE
    --    cannot use a B-tree under a non-byte collation and falls back to
    --    reading the table.
    EXECUTE 'EXPLAIN (FORMAT json) SELECT count(*) FROM gpc_icu '
            'WHERE code LIKE ' || quote_literal('G3RJM%') INTO plan;

    IF plan LIKE '%Index%' THEN
        RAISE NOTICE 'a prefix used the index even under ICU; the README is '
                     'stricter than this database requires';
    ELSE
        RAISE NOTICE 'under ICU a prefix falls back to a sequential scan: that '
                     'is what COLLATE "C" buys';
    END IF;

    -- 3. A bounded range does not care, because it compares whole values
    --    rather than asking for a prefix. Callers who cannot change the
    --    collation have this to fall back on, so it must keep working.
    EXECUTE 'EXPLAIN (FORMAT json) SELECT count(*) FROM gpc_icu '
            'WHERE code >= ' || quote_literal('G3RJM')
         || ' AND code < ' || quote_literal('G3RJN') INTO plan;

    IF plan NOT LIKE '%Index%' THEN
        RAISE EXCEPTION
            'a bounded range stopped using the index under ICU as well. The '
            'README offers it as the way out for callers who cannot set the '
            'collation, and it would now be wrong. The plan was: %', plan;
    END IF;
    RAISE NOTICE 'a bounded range still uses the index under ICU';
END;
$$;
