-- The conformance vectors, as tables. See test_data/README.md.
--
-- These are the same files the four library ports are held to. Loading them
-- rather than restating any of them here is the point: a fifth implementation
-- that agreed with itself would prove nothing.

DROP VIEW IF EXISTS gpc_v_classify;
DROP TABLE IF EXISTS gpc_v_encoding, gpc_v_decoding, gpc_v_area,
                     gpc_v_check, gpc_v_cells, gpc_v_classify_raw;

CREATE TABLE gpc_v_encoding (
    latitude double precision,
    longitude double precision,
    code text
);

CREATE TABLE gpc_v_decoding (
    code text,
    latitude double precision,
    longitude double precision
);

CREATE TABLE gpc_v_area (
    code text,
    south double precision,
    west double precision,
    north double precision,
    east double precision
);

CREATE TABLE gpc_v_check (
    code text,
    check_character text
);

CREATE TABLE gpc_v_cells (
    level int,
    code text,
    cell text,
    neighbours text
);

-- Classification is loaded a line at a time rather than as CSV. Its own
-- README says to split on the first two commas only and not to trim the input
-- field: one case is a bare tab, one is three spaces, and a CSV reader would
-- quietly tidy both away -- which are exactly the cases worth keeping.
CREATE TABLE gpc_v_classify_raw (line text);

CREATE OR REPLACE VIEW gpc_v_classify AS
SELECT substring(line FROM '^([^,]*)') AS class,
       substring(line FROM '^[^,]*,([^,]*)') AS reason,
       substring(line FROM '^[^,]*,[^,]*,(.*)$') AS input
  FROM gpc_v_classify_raw;
