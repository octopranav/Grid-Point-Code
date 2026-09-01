"""The Python leg of the differential harness. See README.md.

Prints one `label|result` line per case. Every port prints the same labels in
the same order; conformance/compare.py runs all four and diffs.
"""

import os
import sys
from pathlib import Path

# The source next door by default. `compare.py --released` points this at a
# directory it has installed the published wheel into, so the same driver can
# be asked about either without a second copy of it existing.
SOURCE = os.environ.get("GPC_PYTHON_PATH") or str(
    Path(__file__).resolve().parent.parent / "python" / "src"
)
sys.path.insert(0, SOURCE)

from gridpointcode_algo_pranavpatel_ca import GPC  # noqa: E402
from gridpointcode_algo_pranavpatel_ca.errors import GPCError

out = []


def t(label, fn):
    try:
        r = fn()
        if isinstance(r, tuple):
            r = ",".join(fmt(x) for x in r)
        elif isinstance(r, list):
            r = " ".join(str(x) for x in r)
        else:
            r = fmt(r)
        out.append(f"{label}|{r}")
    except GPCError as e:
        out.append(f"{label}|ERR:{e.reason}")
    except Exception as e:                      # noqa: BLE001
        out.append(f"{label}|EXC:{type(e).__name__}")


def fmt(x):
    if isinstance(x, bool):
        return "true" if x else "false"
    if isinstance(x, float):
        return repr(x)
    if isinstance(x, list):
        return " ".join(str(i) for i in x)
    return str(x)


C = "G3RJM98NM9"
X = "XG3RJ98NM9"

# --- cells and levels
for k in (-1, 0, 1, 5, 10, 11):
    t(f"cell({k})", lambda k=k: GPC.cell(C, k))
t("cell(reserved,3)", lambda: GPC.cell(X, 3))
t("cell(lowercase,4)", lambda: GPC.cell("g3rjm98nm9", 4))
t("cell(formatted,4)", lambda: GPC.cell("#G3RJM-98NM9", 4))

# --- contains
t("contains(cell,code)", lambda: GPC.contains("G3RJM", C))
t("contains(self)", lambda: GPC.contains(C, C))
t("contains(other)", lambda: GPC.contains("G3RJM", "G3RJT98NM9"))
t("contains(longer-cell)", lambda: GPC.contains(C, "G3RJM"))
t("contains(reserved-cell)", lambda: GPC.contains("XG3RJ", X))
t("contains(empty-cell)", lambda: GPC.contains("", C))

# --- neighbours
for code in ("G3RJM98NM9", "P4444PPPPP", "3PPPP00000", "F0000000000"[:10], "G", "G3RJM"):
    t(f"neighbours({code})", lambda code=code: GPC.neighbours(code))
t("neighbours(reserved)", lambda: GPC.neighbours(X))

# --- distance
t("distance(self)", lambda: GPC.distance(C, C))
t("distance(levels)", lambda: GPC.distance("G3RJM", C))
t("distance(antipodal)", lambda: GPC.distance("P4444PPPPP", "3PPPP00000"))
t("distance(reserved)", lambda: GPC.distance(X, C))

# --- cell dimensions
for k in (0, 1, 10, 11):
    t(f"cellDimensions({k})", lambda k=k: GPC.cell_dimensions(k))

# --- integer form
t("toInteger", lambda: GPC.to_integer(C))
t("toInteger(reserved)", lambda: GPC.to_integer(X))
t("fromInteger(0)", lambda: GPC.from_integer(0))
t("fromInteger(-1)", lambda: GPC.from_integer(-1))
t("fromInteger(max)", lambda: GPC.from_integer(25 ** 10 - 1))
t("fromInteger(over)", lambda: GPC.from_integer(25 ** 10))

# --- short form
t("shorten", lambda: GPC.shorten(C))
t("recoverShort(dash)", lambda: GPC.recover_short("-98NM9", 43.65, -79.38))
t("recoverShort(plain)", lambda: GPC.recover_short("98NM9", 43.65, -79.38))
t("recoverShort(lower)", lambda: GPC.recover_short("98nm9", 43.65, -79.38))
t("recoverShort(alias)", lambda: GPC.recover_short("98NMO", 43.65, -79.38))
t("recoverShort(short)", lambda: GPC.recover_short("98NM", 43.65, -79.38))
t("recoverShort(long)", lambda: GPC.recover_short("98NM99", 43.65, -79.38))
t("recoverShort(wrap-w)", lambda: GPC.recover_short("00000", 0.0, -179.999))
t("recoverShort(wrap-e)", lambda: GPC.recover_short("00000", 0.0, 179.999))
t("recoverShort(pole)", lambda: GPC.recover_short("PPPPP", 90.0, 0.0))

# --- corrections
t("suggest(level0)", lambda: len(GPC.suggest_corrections(C, 43.65, -79.38, 0)))
t("suggest(level11)", lambda: len(GPC.suggest_corrections(C, 43.65, -79.38, 11)))
t("suggest(reserved-in)", lambda: len(GPC.suggest_corrections(X, 43.65, -79.38)))
t("suggest(bad-char)", lambda: len(GPC.suggest_corrections("G3RJM98NMQ", 43.65, -79.38)))
t("suggest(count6)", lambda: len(GPC.suggest_corrections("G3RJM98N09", 43.65, -79.38, 6)))

# --- screen
t("screen(code)", lambda: GPC.screen(C))
t("screen(reserved)", lambda: GPC.screen(X))
t("screen(bad-char)", lambda: GPC.screen("G3RJM98NMQ"))
t("screen(short)", lambda: GPC.screen("G3RJM"))

# --- conversions
t("toDMS", lambda: GPC.to_dms(43.650006, -79.380004))
t("toDMS(zero)", lambda: GPC.to_dms(0.0, 0.0))
t("toDMS(negzero)", lambda: GPC.to_dms(-0.0, -0.0))
t("toDMS(pole)", lambda: GPC.to_dms(90.0, 180.0))
t("fromDMS", lambda: GPC.from_dms("43\u00b039'00.02\"N, 79\u00b022'48.01\"W"))
t("fromDMS(min60)", lambda: GPC.from_dms("43\u00b060'00.00\"N, 79\u00b022'48.01\"W"))
t("fromDMS(sec60)", lambda: GPC.from_dms("43\u00b039'60.00\"N, 79\u00b022'48.01\"W"))
t("fromDMS(junk)", lambda: GPC.from_dms("hello"))
t("fromDMS(empty)", lambda: GPC.from_dms(""))
t("toGeoURI", lambda: GPC.to_geo_uri(43.650006, -79.380004))
t("toGeoURI(zero)", lambda: GPC.to_geo_uri(0.0, 0.0))
t("fromGeoURI", lambda: GPC.from_geo_uri("geo:43.650006,-79.380004"))
t("fromGeoURI(crs)", lambda: GPC.from_geo_uri("geo:43.65,-79.38;crs=WGS84"))
t("fromGeoURI(badcrs)", lambda: GPC.from_geo_uri("geo:43.65,-79.38;crs=nad27"))
t("fromGeoURI(u)", lambda: GPC.from_geo_uri("geo:43.65,-79.38;u=35"))
t("fromGeoURI(alt)", lambda: GPC.from_geo_uri("geo:43.65,-79.38,120"))
t("fromGeoURI(junk)", lambda: GPC.from_geo_uri("http://x"))
t("fromGeoURI(empty)", lambda: GPC.from_geo_uri(""))

# --- bulk
t("encodeAll(empty)", lambda: len(GPC.encode_all([])))
t("decodeAll(empty)", lambda: len(GPC.decode_all([])))
t("encodeAll(two)", lambda: GPC.encode_all([(43.65, -79.38), (0.0, 0.0)]))

# --- check form
t("withCheck", lambda: GPC.with_check(C))
t("withCheck(reserved)", lambda: GPC.with_check(X))

print("\n".join(out))
