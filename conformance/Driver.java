// The Java leg of the differential harness. See README.md.
import ca.pranavpatel.algo.gridpointcode.*;
import java.util.*;
import java.util.function.Supplier;

public class Driver {
    static List<String> out = new ArrayList<>();

    static void t(String label, Supplier<Object> fn) {
        try {
            out.add(label + "|" + fmt(fn.get()));
        } catch (GPCException e) {
            out.add(label + "|ERR:" + e.getReason());
        } catch (Exception e) {
            out.add(label + "|EXC:" + e.getClass().getSimpleName());
        }
    }

    static String fmt(Object o) {
        if (o instanceof String[]) return String.join(" ", (String[]) o);
        if (o instanceof List) {
            StringBuilder sb = new StringBuilder();
            for (Object e : (List<?>) o) { if (sb.length() > 0) sb.append(" "); sb.append(fmt(e)); }
            return sb.toString();
        }
        if (o instanceof Coordinates) {
            Coordinates c = (Coordinates) o;
            return fmt(c.Latitude) + "," + fmt(c.Longitude);
        }
        if (o instanceof Double) return String.valueOf((double) (Double) o);
        return String.valueOf(o);
    }

    public static void main(String[] a) {
        String C = "G3RJM98NM9", X = "XG3RJ98NM9";

        for (int k : new int[]{-1, 0, 1, 5, 10, 11}) {
            final int kk = k;
            t("cell(" + k + ")", () -> GPC.Cell(C, kk));
        }
        t("cell(reserved,3)", () -> GPC.Cell(X, 3));
        t("cell(lowercase,4)", () -> GPC.Cell("g3rjm98nm9", 4));
        t("cell(formatted,4)", () -> GPC.Cell("#G3RJM-98NM9", 4));

        t("contains(cell,code)", () -> GPC.Contains("G3RJM", C));
        t("contains(self)", () -> GPC.Contains(C, C));
        t("contains(other)", () -> GPC.Contains("G3RJM", "G3RJT98NM9"));
        t("contains(longer-cell)", () -> GPC.Contains(C, "G3RJM"));
        t("contains(reserved-cell)", () -> GPC.Contains("XG3RJ", X));
        t("contains(empty-cell)", () -> GPC.Contains("", C));

        for (String code : new String[]{"G3RJM98NM9", "P4444PPPPP", "3PPPP00000", "F000000000", "G", "G3RJM"}) {
            final String cc = code;
            t("neighbours(" + code + ")", () -> GPC.Neighbours(cc));
        }
        t("neighbours(reserved)", () -> GPC.Neighbours(X));

        t("distance(self)", () -> GPC.Distance(C, C));
        t("distance(levels)", () -> GPC.Distance("G3RJM", C));
        t("distance(antipodal)", () -> GPC.Distance("P4444PPPPP", "3PPPP00000"));
        t("distance(reserved)", () -> GPC.Distance(X, C));

        for (int k : new int[]{0, 1, 10, 11}) {
            final int kk = k;
            t("cellDimensions(" + k + ")", () -> {
                Dimensions d = GPC.CellDimensions(kk);
                return fmt(d.LatitudeSpan) + "," + fmt(d.LongitudeSpan) + ","
                     + fmt(d.NorthSouth) + "," + fmt(d.EastWest);
            });
        }

        t("toInteger", () -> GPC.ToInteger(C));
        t("toInteger(reserved)", () -> GPC.ToInteger(X));
        t("fromInteger(0)", () -> GPC.FromInteger(0L));
        t("fromInteger(-1)", () -> GPC.FromInteger(-1L));
        t("fromInteger(max)", () -> GPC.FromInteger(95367431640624L));
        t("fromInteger(over)", () -> GPC.FromInteger(95367431640625L));

        t("shorten", () -> GPC.Shorten(C));
        t("recoverShort(dash)", () -> GPC.RecoverShort("-98NM9", 43.65, -79.38));
        t("recoverShort(plain)", () -> GPC.RecoverShort("98NM9", 43.65, -79.38));
        t("recoverShort(lower)", () -> GPC.RecoverShort("98nm9", 43.65, -79.38));
        t("recoverShort(alias)", () -> GPC.RecoverShort("98NMO", 43.65, -79.38));
        t("recoverShort(short)", () -> GPC.RecoverShort("98NM", 43.65, -79.38));
        t("recoverShort(long)", () -> GPC.RecoverShort("98NM99", 43.65, -79.38));
        t("recoverShort(wrap-w)", () -> GPC.RecoverShort("00000", 0.0, -179.999));
        t("recoverShort(wrap-e)", () -> GPC.RecoverShort("00000", 0.0, 179.999));
        t("recoverShort(pole)", () -> GPC.RecoverShort("PPPPP", 90.0, 0.0));

        t("suggest(level0)", () -> GPC.SuggestCorrections(C, 43.65, -79.38, 0).size());
        t("suggest(level11)", () -> GPC.SuggestCorrections(C, 43.65, -79.38, 11).size());
        t("suggest(reserved-in)", () -> GPC.SuggestCorrections(X, 43.65, -79.38).size());
        t("suggest(bad-char)", () -> GPC.SuggestCorrections("G3RJM98NMQ", 43.65, -79.38).size());
        t("suggest(count6)", () -> GPC.SuggestCorrections("G3RJM98N09", 43.65, -79.38, 6).size());

        t("screen(code)", () -> screenStr(C));
        t("screen(reserved)", () -> screenStr(X));
        t("screen(bad-char)", () -> screenStr("G3RJM98NMQ"));
        t("screen(short)", () -> screenStr("G3RJM"));

        t("toDMS", () -> GPC.ToDMS(43.650006, -79.380004));
        t("toDMS(zero)", () -> GPC.ToDMS(0.0, 0.0));
        t("toDMS(negzero)", () -> GPC.ToDMS(-0.0, -0.0));
        t("toDMS(pole)", () -> GPC.ToDMS(90.0, 180.0));
        t("fromDMS", () -> GPC.FromDMS("43°39'00.02\"N, 79°22'48.01\"W"));
        t("fromDMS(min60)", () -> GPC.FromDMS("43°60'00.00\"N, 79°22'48.01\"W"));
        t("fromDMS(sec60)", () -> GPC.FromDMS("43°39'60.00\"N, 79°22'48.01\"W"));
        t("fromDMS(junk)", () -> GPC.FromDMS("hello"));
        t("fromDMS(empty)", () -> GPC.FromDMS(""));
        t("toGeoURI", () -> GPC.ToGeoURI(43.650006, -79.380004));
        t("toGeoURI(zero)", () -> GPC.ToGeoURI(0.0, 0.0));
        t("fromGeoURI", () -> GPC.FromGeoURI("geo:43.650006,-79.380004"));
        t("fromGeoURI(crs)", () -> GPC.FromGeoURI("geo:43.65,-79.38;crs=WGS84"));
        t("fromGeoURI(badcrs)", () -> GPC.FromGeoURI("geo:43.65,-79.38;crs=nad27"));
        t("fromGeoURI(u)", () -> GPC.FromGeoURI("geo:43.65,-79.38;u=35"));
        t("fromGeoURI(alt)", () -> GPC.FromGeoURI("geo:43.65,-79.38,120"));
        t("fromGeoURI(junk)", () -> GPC.FromGeoURI("http://x"));
        t("fromGeoURI(empty)", () -> GPC.FromGeoURI(""));

        t("encodeAll(empty)", () -> GPC.EncodeAll(new ArrayList<Coordinates>()).size());
        t("decodeAll(empty)", () -> GPC.DecodeAll(new ArrayList<String>()).size());
        t("encodeAll(two)", () -> {
            List<Coordinates> pts = new ArrayList<>();
            pts.add(new Coordinates(43.65, -79.38));
            pts.add(new Coordinates(0.0, 0.0));
            return String.join(" ", GPC.EncodeAll(pts));
        });

        t("withCheck", () -> GPC.WithCheck(C));
        t("withCheck(reserved)", () -> GPC.WithCheck(X));

        // --- generated cases, when the harness hands over a file of them
        //
        // The battery above is fixed and written by hand. These arrive from
        // fuzz.py, which produces far more of them than anyone would sit and
        // type. Same formatting and the same error discipline, so one diff
        // covers both.
        String casefile = System.getenv("GPC_FUZZ_CASES");
        if (casefile != null && !casefile.isEmpty()) {
            List<String> cases;
            try {
                cases = java.nio.file.Files.readAllLines(
                    java.nio.file.Paths.get(casefile),
                    java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                System.err.println("cannot read " + casefile + ": " + e);
                System.exit(2);
                return;
            }
            for (String one : cases) {
                if (one.isEmpty()) continue;
                String[] part = one.split("[|]", -1);
                final String label = part[0];
                switch (part[1]) {
                    case "encode": {
                        final double lat = Double.parseDouble(part[2]);
                        final double lng = Double.parseDouble(part[3]);
                        t(label, () -> GPC.Encode(lat, lng));
                        break;
                    }
                    case "decode": {
                        final String code = part[2];
                        t(label, () -> GPC.Decode(code));
                        break;
                    }
                    case "isvalid": {
                        final String code = part[2];
                        t(label, () -> GPC.IsValid(code));
                        break;
                    }
                    default:
                        out.add(label + "|EXC:UnknownOperation");
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String s : out) sb.append(s).append("\n");
        System.out.print(sb);
    }

    static String screenStr(String code) {
        Screening r = GPC.Screen(code);
        StringBuilder sb = new StringBuilder(r.Version).append(",");
        boolean first = true;
        for (Span s : r.Spans) {
            if (!first) sb.append(" ");
            sb.append(s.Position).append(":").append(s.Length);
            first = false;
        }
        return sb.toString();
    }
}
