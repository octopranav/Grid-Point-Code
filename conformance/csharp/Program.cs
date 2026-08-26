// The C# leg of the differential harness. See ../README.md.
using System.Globalization;
using System.Text;
using Ca.Pranavpatel.Algo.GridPointCode;

var outp = new List<string>();

static string F(double d) => d.ToString("R", CultureInfo.InvariantCulture);

void T(string label, Func<string> fn) {
    try { outp.Add(label + "|" + fn()); }
    catch (GPCException e) { outp.Add(label + "|ERR:" + e.Reason); }
    catch (Exception e) { outp.Add(label + "|EXC:" + e.GetType().Name); }
}

const string C = "G3RJM98NM9";
const string X = "XG3RJ98NM9";

foreach (int k in new[] { -1, 0, 1, 5, 10, 11 }) {
    int kk = k;
    T($"cell({k})", () => GPC.Cell(C, kk));
}
T("cell(reserved,3)", () => GPC.Cell(X, 3));
T("cell(lowercase,4)", () => GPC.Cell("g3rjm98nm9", 4));
T("cell(formatted,4)", () => GPC.Cell("#G3RJM-98NM9", 4));

T("contains(cell,code)", () => GPC.Contains("G3RJM", C) ? "true" : "false");
T("contains(self)", () => GPC.Contains(C, C) ? "true" : "false");
T("contains(other)", () => GPC.Contains("G3RJM", "G3RJT98NM9") ? "true" : "false");
T("contains(longer-cell)", () => GPC.Contains(C, "G3RJM") ? "true" : "false");
T("contains(reserved-cell)", () => GPC.Contains("XG3RJ", X) ? "true" : "false");
T("contains(empty-cell)", () => GPC.Contains("", C) ? "true" : "false");

foreach (string code in new[] { "G3RJM98NM9", "P4444PPPPP", "3PPPP00000", "F000000000", "G", "G3RJM" }) {
    string cc = code;
    T($"neighbours({code})", () => string.Join(" ", GPC.Neighbours(cc)));
}
T("neighbours(reserved)", () => string.Join(" ", GPC.Neighbours(X)));

T("distance(self)", () => F(GPC.Distance(C, C)));
T("distance(levels)", () => F(GPC.Distance("G3RJM", C)));
T("distance(antipodal)", () => F(GPC.Distance("P4444PPPPP", "3PPPP00000")));
T("distance(reserved)", () => F(GPC.Distance(X, C)));

foreach (int k in new[] { 0, 1, 10, 11 }) {
    int kk = k;
    T($"cellDimensions({k})", () => {
        var d = GPC.CellDimensions(kk);
        return F(d.LatitudeSpan) + "," + F(d.LongitudeSpan) + "," + F(d.NorthSouth) + "," + F(d.EastWest);
    });
}

T("toInteger", () => GPC.ToInteger(C).ToString(CultureInfo.InvariantCulture));
T("toInteger(reserved)", () => GPC.ToInteger(X).ToString(CultureInfo.InvariantCulture));
T("fromInteger(0)", () => GPC.FromInteger(0L));
T("fromInteger(-1)", () => GPC.FromInteger(-1L));
T("fromInteger(max)", () => GPC.FromInteger(95367431640624L));
T("fromInteger(over)", () => GPC.FromInteger(95367431640625L));

T("shorten", () => GPC.Shorten(C));
T("recoverShort(dash)", () => GPC.RecoverShort("-98NM9", 43.65, -79.38));
T("recoverShort(plain)", () => GPC.RecoverShort("98NM9", 43.65, -79.38));
T("recoverShort(lower)", () => GPC.RecoverShort("98nm9", 43.65, -79.38));
T("recoverShort(alias)", () => GPC.RecoverShort("98NMO", 43.65, -79.38));
T("recoverShort(short)", () => GPC.RecoverShort("98NM", 43.65, -79.38));
T("recoverShort(long)", () => GPC.RecoverShort("98NM99", 43.65, -79.38));
T("recoverShort(wrap-w)", () => GPC.RecoverShort("00000", 0.0, -179.999));
T("recoverShort(wrap-e)", () => GPC.RecoverShort("00000", 0.0, 179.999));
T("recoverShort(pole)", () => GPC.RecoverShort("PPPPP", 90.0, 0.0));

T("suggest(level0)", () => GPC.SuggestCorrections(C, 43.65, -79.38, 0).Count.ToString());
T("suggest(level11)", () => GPC.SuggestCorrections(C, 43.65, -79.38, 11).Count.ToString());
T("suggest(reserved-in)", () => GPC.SuggestCorrections(X, 43.65, -79.38).Count.ToString());
T("suggest(bad-char)", () => GPC.SuggestCorrections("G3RJM98NMQ", 43.65, -79.38).Count.ToString());
T("suggest(count6)", () => GPC.SuggestCorrections("G3RJM98N09", 43.65, -79.38, 6).Count.ToString());

static string ScreenStr(string code) {
    var r = GPC.Screen(code);
    var sb = new StringBuilder(r.Version).Append(',');
    bool first = true;
    foreach (var s in r.Spans) {
        if (!first) sb.Append(' ');
        sb.Append(s.Position).Append(':').Append(s.Length);
        first = false;
    }
    return sb.ToString();
}
T("screen(code)", () => ScreenStr(C));
T("screen(reserved)", () => ScreenStr(X));
T("screen(bad-char)", () => ScreenStr("G3RJM98NMQ"));
T("screen(short)", () => ScreenStr("G3RJM"));

T("toDMS", () => GPC.ToDMS(43.650006, -79.380004));
T("toDMS(zero)", () => GPC.ToDMS(0.0, 0.0));
T("toDMS(negzero)", () => GPC.ToDMS(-0.0, -0.0));
T("toDMS(pole)", () => GPC.ToDMS(90.0, 180.0));
static string Pair((double Latitude, double Longitude) c) => F(c.Latitude) + "," + F(c.Longitude);
T("fromDMS", () => Pair(GPC.FromDMS("43°39'00.02\"N, 79°22'48.01\"W")));
T("fromDMS(min60)", () => Pair(GPC.FromDMS("43°60'00.00\"N, 79°22'48.01\"W")));
T("fromDMS(sec60)", () => Pair(GPC.FromDMS("43°39'60.00\"N, 79°22'48.01\"W")));
T("fromDMS(junk)", () => Pair(GPC.FromDMS("hello")));
T("fromDMS(empty)", () => Pair(GPC.FromDMS("")));
T("toGeoURI", () => GPC.ToGeoURI(43.650006, -79.380004));
T("toGeoURI(zero)", () => GPC.ToGeoURI(0.0, 0.0));
T("fromGeoURI", () => Pair(GPC.FromGeoURI("geo:43.650006,-79.380004")));
T("fromGeoURI(crs)", () => Pair(GPC.FromGeoURI("geo:43.65,-79.38;crs=WGS84")));
T("fromGeoURI(badcrs)", () => Pair(GPC.FromGeoURI("geo:43.65,-79.38;crs=nad27")));
T("fromGeoURI(u)", () => Pair(GPC.FromGeoURI("geo:43.65,-79.38;u=35")));
T("fromGeoURI(alt)", () => Pair(GPC.FromGeoURI("geo:43.65,-79.38,120")));
T("fromGeoURI(junk)", () => Pair(GPC.FromGeoURI("http://x")));
T("fromGeoURI(empty)", () => Pair(GPC.FromGeoURI("")));

T("encodeAll(empty)", () => GPC.EncodeAll(new List<(double, double)>()).Count.ToString());
T("decodeAll(empty)", () => GPC.DecodeAll(new List<string>()).Count.ToString());
T("encodeAll(two)", () => string.Join(" ",
    GPC.EncodeAll(new List<(double, double)> { (43.65, -79.38), (0.0, 0.0) })));

T("withCheck", () => GPC.WithCheck(C));
T("withCheck(reserved)", () => GPC.WithCheck(X));

Console.OutputEncoding = new UTF8Encoding(false);
Console.Out.Write(string.Join("\n", outp) + "\n");
