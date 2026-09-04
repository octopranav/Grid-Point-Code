-- Load the shared conformance vectors and run the checks. Run from the repo
-- root, because the paths below are relative to it:
--
--     psql -d yourdb -v ON_ERROR_STOP=1 -f sql/gpc.sql -f sql/load.sql
--
-- This is the only file here that knows about psql. `check.sql` is plain SQL
-- so it can be run through anything that speaks the protocol, which is what
-- lets the same assertions be exercised without a server.
--
-- `\copy ... FROM PROGRAM` rather than a plain path: the vector files carry
-- comment lines and blank lines that COPY has no way to skip, and filtering
-- them on the way in is better than keeping a stripped copy that could drift
-- from the original.

\set ON_ERROR_STOP on

\i sql/vectors.sql

\copy gpc_v_encoding FROM PROGRAM 'grep -vE "^#|^$" test_data/v2_encoding.csv' WITH (FORMAT csv)
\copy gpc_v_decoding FROM PROGRAM 'grep -vE "^#|^$" test_data/v2_decoding.csv' WITH (FORMAT csv)
\copy gpc_v_area FROM PROGRAM 'grep -vE "^#|^$" test_data/v2_area.csv' WITH (FORMAT csv)
\copy gpc_v_check FROM PROGRAM 'grep -vE "^#|^$" test_data/v2_check.csv' WITH (FORMAT csv)
\copy gpc_v_cells FROM PROGRAM 'grep -vE "^#|^$" test_data/v2_cells.csv' WITH (FORMAT csv)

-- One column, verbatim. A delimiter and a quote character that cannot occur in
-- the file, because this one must not be tidied: its own README says to split
-- on the first two commas only and to leave the input field alone, and two of
-- its cases are nothing but whitespace.
\copy gpc_v_classify_raw FROM PROGRAM 'grep -vE "^#|^$" test_data/v2_classify.csv' WITH (FORMAT csv, DELIMITER E'\x01', QUOTE E'\x02')

\i sql/check.sql
