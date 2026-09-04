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

-- Geometry bindings. Needs PostGIS and sql/gpc.sql.
--
--     psql -d yourdb -f sql/gpc.sql -f sql/gpc_postgis.sql
--
-- Optional, and separate on purpose. Everything the format is actually for
-- works without PostGIS: a text column, a B-tree, and a prefix. This file is
-- for installations that already have geometry and want to move between the
-- two representations.
--
-- What it is good for is the join nobody wants to write twice: a table of
-- points and a table of codes, matched without a spatial index on either.
--
--     SELECT p.name, c.label
--       FROM points p
--       JOIN codes c ON gpc_contains(c.cell, gpc_encode(p.geom))
--
-- Longitude is X and latitude is Y, which is the PostGIS convention and the
-- reverse of the order the format writes them in. Getting that backwards is
-- the classic error, so these functions take and return geometry rather than
-- pairs of numbers, and the axis order stops being the caller's problem.

-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION gpc_encode(p_point geometry, p_formatted boolean DEFAULT true)
    RETURNS text
    LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE
    AS $$
DECLARE
    srid int;
BEGIN
    IF GeometryType(p_point) <> 'POINT' THEN
        RAISE EXCEPTION 'a code names a point, not a %', GeometryType(p_point)
            USING ERRCODE = '22023';
    END IF;

    -- A code is degrees on WGS 84. Anything else would encode a number that
    -- happens to be in range and mean somewhere entirely different, so it is
    -- refused rather than guessed at. 0 is allowed as "unspecified", which is
    -- what a geometry built by ST_MakePoint carries until told otherwise.
    srid := ST_SRID(p_point);
    IF srid <> 4326 AND srid <> 0 THEN
        RAISE EXCEPTION 'SRID % is not 4326; reproject before encoding', srid
            USING ERRCODE = '22023';
    END IF;

    RETURN gpc_encode(ST_Y(p_point), ST_X(p_point), p_formatted);
END;
$$;

COMMENT ON FUNCTION gpc_encode(geometry, boolean) IS
    'A WGS 84 point to a code. Refuses any other SRID rather than guessing.';


CREATE OR REPLACE FUNCTION gpc_decode_point(p_code text)
    RETURNS geometry
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $$
    -- The centre of the cell, which is what decoding a code gives back.
    SELECT ST_SetSRID(ST_MakePoint(d.longitude, d.latitude), 4326)
      FROM gpc_decode(p_code) d
$$;

COMMENT ON FUNCTION gpc_decode_point(text) IS
    'The centre of the cell a code names, as a WGS 84 point.';


CREATE OR REPLACE FUNCTION gpc_decode_box(p_code text)
    RETURNS geometry
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $$
    -- The cell itself. A code names a box; the point above is its centre, and
    -- anything measuring area or overlap wants this instead.
    SELECT ST_MakeEnvelope(a.west, a.south, a.east, a.north, 4326)
      FROM gpc_decode_area(p_code) a
$$;

COMMENT ON FUNCTION gpc_decode_box(text) IS
    'The cell a code names, as a WGS 84 polygon. Section 6.3.';
