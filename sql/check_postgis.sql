-- The geometry bindings, against the same vectors. Needs gpc_postgis.sql.
--
-- Nothing here re-derives a coordinate: every expected value comes from the
-- functions in gpc.sql, which check.sql has already held to test_data/. What
-- is being asked is narrower and is the thing that actually goes wrong --
-- whether the axes came out in the right order.

DO $$
DECLARE
    wrong record;
    total int;
BEGIN
    SELECT count(*) INTO total FROM gpc_v_encoding;

    -- Longitude is X and latitude is Y. Reversed, this still returns a code
    -- for most inputs, which is why it is worth a vector rather than a glance.
    SELECT * INTO wrong FROM gpc_v_encoding v
     WHERE gpc_encode(ST_SetSRID(ST_MakePoint(v.longitude, v.latitude), 4326), false)
           IS DISTINCT FROM v.code
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'encode(POINT(% %)) gave %, the vectors say %',
            wrong.longitude, wrong.latitude,
            gpc_encode(ST_SetSRID(ST_MakePoint(wrong.longitude, wrong.latitude), 4326), false),
            wrong.code;
    END IF;

    RAISE NOTICE 'encode(geometry): % vectors', total;
END;
$$;

DO $$
DECLARE
    wrong record;
BEGIN
    SELECT v.code, ST_X(gpc_decode_point(v.code)) AS x,
           ST_Y(gpc_decode_point(v.code)) AS y
      INTO wrong
      FROM gpc_v_decoding v
     WHERE ST_Y(gpc_decode_point(v.code)) IS DISTINCT FROM v.latitude
        OR ST_X(gpc_decode_point(v.code)) IS DISTINCT FROM v.longitude
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'decode_point(%) is at x=% y=%, the vectors want the other way round?',
            wrong.code, wrong.x, wrong.y;
    END IF;

    RAISE NOTICE 'decode_point: axes are the right way round';
END;
$$;

DO $$
DECLARE
    wrong record;
BEGIN
    -- The box must be the cell, and the point must be inside it.
    SELECT v.code INTO wrong
      FROM gpc_v_area v
     WHERE NOT ST_Contains(gpc_decode_box(v.code), gpc_decode_point(v.code))
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'the centre of % is not inside its own cell', wrong.code;
    END IF;

    SELECT v.code INTO wrong
      FROM gpc_v_area v
     WHERE ST_XMin(gpc_decode_box(v.code)) IS DISTINCT FROM v.west
        OR ST_YMin(gpc_decode_box(v.code)) IS DISTINCT FROM v.south
        OR ST_XMax(gpc_decode_box(v.code)) IS DISTINCT FROM v.east
        OR ST_YMax(gpc_decode_box(v.code)) IS DISTINCT FROM v.north
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'the box for % is not the cell the vectors describe', wrong.code;
    END IF;

    RAISE NOTICE 'decode_box: every cell is its own box, and holds its own centre';
END;
$$;

-- A projected point is refused rather than encoded as though its metres were
-- degrees. This is the failure that would otherwise be silent and wrong.
DO $$
DECLARE
    got text;
BEGIN
    BEGIN
        got := gpc_encode(ST_SetSRID(ST_MakePoint(-8838000, 5413000), 3857));
        RAISE EXCEPTION 'a web-mercator point encoded to %, instead of being refused', got;
    EXCEPTION
        WHEN SQLSTATE '22023' THEN
            RAISE NOTICE 'a projected point is refused, not guessed at';
    END;
END;
$$;
