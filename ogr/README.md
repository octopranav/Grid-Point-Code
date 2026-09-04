# Codes through GDAL

```
python ogr/gpc_ogr.py encode points.shp coded.gpkg
python ogr/gpc_ogr.py encode survey.gpkg cells.gpkg --level 6 --field cell
python ogr/gpc_ogr.py decode manifest.csv points.gpkg --field gpc
```

GDAL reads a hundred vector formats. This adds one column to whatever it hands
back, or turns a column of codes into points, so a code can enter and leave a
pipeline that has never heard of it.

`encode` copies the source and appends a text column. `decode` goes the other
way and needs no geometry at all in the input — a CSV of codes becomes a point
layer.

| option | |
| --- | --- |
| `--field NAME` | the column to write or read. Default `gpc` |
| `--level 1-10` | write the cell at that level instead of the whole code |
| `--format DRIVER` | output driver. Default `GPKG` |
| `--layer NAME` | which layer of the source, if it has several |

## The trap this is written around

GDAL 3 changed the axis order of EPSG:4326 to what the authority says it is —
**latitude first**. Code written against GDAL 2 that reads `GetX()` as a
longitude now gets the two the wrong way round, and in most of the world a
transposed coordinate is still a valid coordinate. It encodes. It gives a
perfectly well-formed code for a real place several thousand kilometres from the
right one, and nothing raises.

Every transformation here sets `OAMS_TRADITIONAL_GIS_ORDER`, and the tests use a
point whose latitude and longitude cannot be confused for each other — 43.6426
is a valid longitude and −79.3871 is a valid latitude, so a swap produces an
answer rather than an error, and only the right answer passes.

## What it refuses

**A layer with no CRS.** Unlabelled numbers could be anything, and assuming they
are degrees is how a point ends up in the Gulf of Guinea. Set one and run it
again.

Reprojection itself is done rather than refused, unlike the database functions
in `sql/`. A file usually knows its own CRS and converting it is what a tool is
for; a database column has no such context and guessing there would be worse
than stopping.

## Column names

Shapefile truncates a column name at ten characters, so `gridpointcode` and
`gridpointcodes` become the same column and the second write silently replaces
the first. The name is truncated and made unique here, before the driver gets a
chance to resolve the collision badly.

## Non-point geometry

A code names a point. Given a line or a polygon this encodes the centroid, which
is the only defensible reading, and the count is reported so it is visible that
it happened. If a centroid is not what you meant, extract the points you did
mean first.

## Testing

```
python -m unittest discover --start-directory ogr --top-level-directory ogr
```

The column-naming tests run anywhere. The rest need the GDAL Python bindings and
are skipped without them; CI runs the whole file inside the `qgis/qgis:ltr`
container, which carries GDAL as well as QGIS.
