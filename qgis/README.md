# The QGIS plugin

Three Processing algorithms, and nothing else — no toolbar, no dock, no menu
item. Everything here belongs in the toolbox, where it can be run in a model, in
a batch over a hundred layers, or from the command line with `qgis_process`. A
button can do none of that.

| algorithm | what it does |
| --- | --- |
| **Points to codes** | adds a text column holding each point's code |
| **Codes to points** | turns a column of codes into a point layer |
| **Codes to cells** | draws the cell each code names, at any level |

## Installing

Copy `gridpointcode/` into your QGIS plugin directory and enable it in
*Plugins → Manage and Install Plugins*:

| | |
| --- | --- |
| Linux | `~/.local/share/QGIS/QGIS3/profiles/default/python/plugins/` |
| macOS | `~/Library/Application Support/QGIS/QGIS3/profiles/default/python/plugins/` |
| Windows | `%APPDATA%\QGIS\QGIS3\profiles\default\python\plugins\` |

It needs the Python port, which QGIS does not ship. From the QGIS Python
console:

```
import pip; pip.main(['install', 'gridpointcode-algo-pranavpatel-ca'])
```

then restart QGIS. If you skip this the algorithms still appear, and running one
tells you exactly this rather than failing with an import error somewhere deep.

## Two things the algorithms do without being asked

**They reproject.** A layer in a national grid or in web mercator holds numbers
that are not degrees. Encoding them anyway does not raise — it produces a
well-formed code for the wrong hemisphere, which is a failure with no symptom.
The transform is not optional and not yours to remember. A layer with no CRS at
all is refused rather than guessed at.

**They keep rows they cannot use.** A code that will not parse leaves its row in
place without geometry, and says so in the log. Dropping it would lose the only
copy of everything else on that row, and a run that quietly returns fewer
features than it was given is the kind of thing found six months later.

## Levels

*Points to codes* takes a level. At 10 you get the whole code, naming a cell
about 2.5 m across. Below that you get the cell containing the point, which is
how you group points by area without a spatial join:

```
level 6  →  G3RJM9      1.6 km cells
level 7  →  G3RJM98     320 m cells
level 10 →  G3RJM98NM9  2.5 m
```

Every point in the same cell gets the same value, so *Group by* on that column
is a spatial aggregation, and sorting on it sorts geographically. That is the
same property `sql/` uses to turn a B-tree into a spatial index.

*Codes to cells* is the one that makes the format visible. A code is not a point
but a box, and drawing the boxes is how somebody sees what a code actually
claims.

## Testing

```
python -m unittest discover --start-directory qgis --top-level-directory qgis
```

Without QGIS this runs `test_geometry.py` and skips the rest. `geometry.py` is
deliberately free of any QGIS import: it holds the one piece of arithmetic the
plugin does for itself — the box of a cell coarser than level 10, which
`decodeToArea` will not give you because it takes a whole code rather than a
prefix. A second copy of a formula drifts from the first, so at level 10 it is
held to being bit-identical to the library's.

Everything else needs the framework it plugs into, and CI runs it inside the
`qgis/qgis:ltr` container. Those tests go through `processing.run` and the
algorithm ids rather than calling the classes, because half of what can be wrong
with a plugin is the registration: an algorithm nobody can reach is not working,
however correct its arithmetic.
