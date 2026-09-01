// The TypeScript leg of the differential harness. See README.md.
// Reads the built package, so `npm run build` in typescript/ has to have run.
//
// `compare.py --released` points GPC_TYPESCRIPT_MAIN at a directory it has
// installed the published package into, so the same driver can be asked about
// either without a second copy of it existing.
const path = require('path');
const main = process.env.GPC_TYPESCRIPT_MAIN
    || path.join(__dirname, '..', 'typescript', 'dist', 'index.js');
const { GPC } = require(main);

const out = [];
function fmt(x) {
    if (typeof x === 'boolean') return x ? 'true' : 'false';
    if (Array.isArray(x)) return x.map(fmt).join(' ');
    if (typeof x === 'number') return Number.isInteger(x) && Math.abs(x) < 1e15 ? String(x) : String(x);
    return String(x);
}
function t(label, fn) {
    try {
        const r = fn();
        let s;
        if (Array.isArray(r)) {
            // tuple-ish results are comma joined, list results space joined
            s = r.every((e) => typeof e === 'string') && label.startsWith('neighbours')
                ? r.join(' ')
                : r.map(fmt).join(',');
        } else s = fmt(r);
        out.push(`${label}|${s}`);
    } catch (e) {
        if (e && e.reason) out.push(`${label}|ERR:${e.reason}`);
        else out.push(`${label}|EXC:${e && e.constructor ? e.constructor.name : 'x'}`);
    }
}

const C = 'G3RJM98NM9';
const X = 'XG3RJ98NM9';

for (const k of [-1, 0, 1, 5, 10, 11]) t(`cell(${k})`, () => GPC.cell(C, k));
t('cell(reserved,3)', () => GPC.cell(X, 3));
t('cell(lowercase,4)', () => GPC.cell('g3rjm98nm9', 4));
t('cell(formatted,4)', () => GPC.cell('#G3RJM-98NM9', 4));

t('contains(cell,code)', () => GPC.contains('G3RJM', C));
t('contains(self)', () => GPC.contains(C, C));
t('contains(other)', () => GPC.contains('G3RJM', 'G3RJT98NM9'));
t('contains(longer-cell)', () => GPC.contains(C, 'G3RJM'));
t('contains(reserved-cell)', () => GPC.contains('XG3RJ', X));
t('contains(empty-cell)', () => GPC.contains('', C));

for (const code of ['G3RJM98NM9', 'P4444PPPPP', '3PPPP00000', 'F000000000', 'G', 'G3RJM'])
    t(`neighbours(${code})`, () => GPC.neighbours(code));
t('neighbours(reserved)', () => GPC.neighbours(X));

t('distance(self)', () => GPC.distance(C, C));
t('distance(levels)', () => GPC.distance('G3RJM', C));
t('distance(antipodal)', () => GPC.distance('P4444PPPPP', '3PPPP00000'));
t('distance(reserved)', () => GPC.distance(X, C));

for (const k of [0, 1, 10, 11]) t(`cellDimensions(${k})`, () => GPC.cellDimensions(k));

t('toInteger', () => GPC.toInteger(C));
t('toInteger(reserved)', () => GPC.toInteger(X));
t('fromInteger(0)', () => GPC.fromInteger(0));
t('fromInteger(-1)', () => GPC.fromInteger(-1));
t('fromInteger(max)', () => GPC.fromInteger(25 ** 10 - 1));
t('fromInteger(over)', () => GPC.fromInteger(25 ** 10));

t('shorten', () => GPC.shorten(C));
t('recoverShort(dash)', () => GPC.recoverShort('-98NM9', 43.65, -79.38));
t('recoverShort(plain)', () => GPC.recoverShort('98NM9', 43.65, -79.38));
t('recoverShort(lower)', () => GPC.recoverShort('98nm9', 43.65, -79.38));
t('recoverShort(alias)', () => GPC.recoverShort('98NMO', 43.65, -79.38));
t('recoverShort(short)', () => GPC.recoverShort('98NM', 43.65, -79.38));
t('recoverShort(long)', () => GPC.recoverShort('98NM99', 43.65, -79.38));
t('recoverShort(wrap-w)', () => GPC.recoverShort('00000', 0.0, -179.999));
t('recoverShort(wrap-e)', () => GPC.recoverShort('00000', 0.0, 179.999));
t('recoverShort(pole)', () => GPC.recoverShort('PPPPP', 90.0, 0.0));

t('suggest(level0)', () => GPC.suggestCorrections(C, 43.65, -79.38, 0).length);
t('suggest(level11)', () => GPC.suggestCorrections(C, 43.65, -79.38, 11).length);
t('suggest(reserved-in)', () => GPC.suggestCorrections(X, 43.65, -79.38).length);
t('suggest(bad-char)', () => GPC.suggestCorrections('G3RJM98NMQ', 43.65, -79.38).length);
t('suggest(count6)', () => GPC.suggestCorrections('G3RJM98N09', 43.65, -79.38, 6).length);

t('screen(code)', () => GPC.screen(C));
t('screen(reserved)', () => GPC.screen(X));
t('screen(bad-char)', () => GPC.screen('G3RJM98NMQ'));
t('screen(short)', () => GPC.screen('G3RJM'));

t('toDMS', () => GPC.toDMS(43.650006, -79.380004));
t('toDMS(zero)', () => GPC.toDMS(0.0, 0.0));
t('toDMS(negzero)', () => GPC.toDMS(-0.0, -0.0));
t('toDMS(pole)', () => GPC.toDMS(90.0, 180.0));
t('fromDMS', () => GPC.fromDMS('43\u00b039\'00.02"N, 79\u00b022\'48.01"W'));
t('fromDMS(min60)', () => GPC.fromDMS('43\u00b060\'00.00"N, 79\u00b022\'48.01"W'));
t('fromDMS(sec60)', () => GPC.fromDMS('43\u00b039\'60.00"N, 79\u00b022\'48.01"W'));
t('fromDMS(junk)', () => GPC.fromDMS('hello'));
t('fromDMS(empty)', () => GPC.fromDMS(''));
t('toGeoURI', () => GPC.toGeoURI(43.650006, -79.380004));
t('toGeoURI(zero)', () => GPC.toGeoURI(0.0, 0.0));
t('fromGeoURI', () => GPC.fromGeoURI('geo:43.650006,-79.380004'));
t('fromGeoURI(crs)', () => GPC.fromGeoURI('geo:43.65,-79.38;crs=WGS84'));
t('fromGeoURI(badcrs)', () => GPC.fromGeoURI('geo:43.65,-79.38;crs=nad27'));
t('fromGeoURI(u)', () => GPC.fromGeoURI('geo:43.65,-79.38;u=35'));
t('fromGeoURI(alt)', () => GPC.fromGeoURI('geo:43.65,-79.38,120'));
t('fromGeoURI(junk)', () => GPC.fromGeoURI('http://x'));
t('fromGeoURI(empty)', () => GPC.fromGeoURI(''));

t('encodeAll(empty)', () => GPC.encodeAll([]).length);
t('decodeAll(empty)', () => GPC.decodeAll([]).length);
t('encodeAll(two)', () => GPC.encodeAll([[43.65, -79.38], [0.0, 0.0]]).join(' '));

t('withCheck', () => GPC.withCheck(C));
t('withCheck(reserved)', () => GPC.withCheck(X));

process.stdout.write(out.join('\n') + '\n');
