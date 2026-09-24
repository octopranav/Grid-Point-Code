# Grid Point Code for Android

The application for phones, tablets, foldables and Android on the desktop, and
later for Wear OS, cars and headsets. One APK for the large screens, laid out by
the size of the window rather than the kind of device.

What is built so far is the place: a map with the cell drawn on it, the panel
that says everything about it, and a button that finds where the device is. The
map is the only thing that uses the network, and nothing waits for it; every
code, check and correction is arithmetic on the device.

## What is here

| Module | What it holds |
| --- | --- |
| [`core`](core) | Plain Kotlin on the JVM, no Android. Wraps the published package and adds only what the website's own `lib/` adds: the written forms, the read-aloud line in the words of seven languages, links with directions, one reader for every kind of input, the nudge pad, cell sizes, the accuracy rule, the rules for what survives a move, reading the website's name index, which landmarks can anchor a short form, saved places, reading locations written in other systems, the emergency card, and areas |
| [`designsystem`](designsystem) | The theme, the type scale, the shapes and the ten-cell mark, built from the files [`design/build-tokens.mjs`](../design/build-tokens.mjs) generates |
| [`map`](map) | MapLibre Native on the website's tile provider, its five styles, the cell drawn in brass with its eight neighbours faint around it, and a tap to place a point |
| [`app`](app) | The place screen, the view model that holds the place, read aloud, fetching the name index and the landmark archive, and the ways a place arrives from another app |

## What it does today

- Finds where the device is when the locate button is pressed, asking for the
  permission only then, and only while the app is open. It keeps listening
  while the reader holds still, keeps the tightest fix the device gives, and
  stops once a fix is inside one cell, after thirty seconds, or the moment the
  reader chooses somewhere else. The device's own estimate is drawn on the map
  as a disc under the cell and stated beside the code, as the site states it.
- Says what went wrong when it cannot: the permission refused, with a way to
  the app's settings; location switched off; or no fix in thirty seconds.
- Draws the place on a map as the cell it names, not a pin, with the eight
  cells around it. Tap the map to move there. North stays up.
- Offers the website's basemaps from a button on the map: match the theme
  (positron by day, fiord by night), positron, bright, liberty, dark or fiord.
  The choice is remembered, and the cell is drawn in inks that suit the map
  chosen rather than the app's theme.
- On a phone the panel is a sheet over the map; in a window wider than 840 dp it
  sits beside it.
- Says so, after a few seconds, when the map has not arrived, and lets it
  appear by itself when the network returns. With no connection the renderer
  never reports a failure; it only waits, so the notice cannot wait for one.
- Shows a place as the ten-cell mark, with its cell size at that latitude.
- Nudges one cell in any of eight directions.
- Writes the code every other way: the short form, the check form, the 48-bit
  integer, degrees and minutes, and a geo URI.
- Carries directions in the link it shares, one line of at most eighty
  characters, exactly as the site does.
- Reads the code aloud with its check word, through the device's own speech
  engine, offline, in the words of the listener's language: German, English,
  Spanish, French, Italian, Dutch or Swedish, each its own speakers' spelling
  table, in that language's voice. The choice is kept, and it carries to an
  area and to the emergency card, which then reads the coordinates too in that
  language. With no voice for it on the device, the words stay on the screen to
  be read out, and the device's speech engine is one tap away.
- Takes a code however it is written, a pair of coordinates, degrees and
  minutes, a geo URI, a gridpointcode.com link or a short form, from the search
  field, from a link, from another app's "open location", or from text selected
  anywhere on the phone.
- Finds a place by name from the same field, listing the places a name could
  mean as it is typed, largest first within one name, from the website's own
  index of 7.3 million names. Go takes the first one. With no connection it
  says names need one, and codes and coordinates carry on working.
- Anchors the short form to a landmark, from the website's archive of 6.5
  million places each named uniquely within its region: every one near enough
  for recovery to be guaranteed, nearest first, with its distance and bearing,
  and the line to share, `-98NM9 near Old Toronto, Ontario, Canada`. A choice is
  kept through a nudge while it stays in reach. Open country says there is
  nothing near enough, and an archive that cannot be reached says so rather
  than passing for open country.
- Reads such a line back, typed, pasted, or selected in another app: the place
  is found by name in the index and then in the archive, and the five
  characters are recovered against the archive's own coordinates for it, which
  are the ones the sender's list was drawn from. The anchor list then offers the
  same landmark first. A place that cannot be found, a name several places
  answer to, one that is not unique in its own region, and no connection are
  each refused with the reason, and the place stays where it was.
- Keeps an area offline on request: 25 shards of the archive centred on the
  place, 200 km north to south, a few hundred kilobytes. There the anchor list,
  reading an anchored line and finding a place by name all work with no
  connection. An area is kept whole or not at all, only what was asked for is
  ever counted as kept, and Forget gives it all back.
- Works offline wherever it has already been used: the map draws from the tiles
  it has already drawn, up to 200 MB, and the landmarks of places already looked
  at are cached apart from the kept areas.
- Reads a location written in several other systems, and the links the common
  map services share, from the same field, from a link or from text selected in
  another app, and says on the card which it was. A short code of another
  system written with its town is read against the town. What only its owner's
  service can read, and a short link that says where it goes only when opened,
  are named and refused rather than guessed.
- Shows an emergency card, from a button on the map or by holding the app's
  icon: where the device is, in large type, with the plain latitude and
  longitude first, then the code, how to say it, the landmark it is near and
  how far to trust the fix, and a button to read it all aloud. It asks the
  device afresh when it opens, keeps the screen awake, needs no connection,
  and never shows a place the reader picked or typed as where they are.
- Saves places: the bookmark on a place's card keeps it with a name and the
  directions to its door. Saved places are listed from a button on the map,
  drawn on it as small rings, and found first as a name is typed, with no
  connection. Opening one brings its directions back, and Go on a saved name
  goes straight there.
- Shares an area as well as a point: a place's card lists the areas around it,
  a region down to a building, each written as its first few characters with
  its size where it lies. Choosing one frames it on the map in brass, and it can
  be read aloud, copied or shared with a link. A link to an area opens as that
  area, and so does a cell typed into the field.

## Building

```
./gradlew :core:test :app:assembleDebug
```

Needs JDK 17 or later and the Android SDK with platform 37. Continuous
integration runs both on every pull request.

To try the map against a local copy of the styles, for instance on a machine
that cannot reach the tile host, point a debug build at it:

```
./gradlew :app:assembleDebug -Pgpc.styles=http://10.0.2.2:8099/styles/
```

`10.0.2.2` is the emulator's name for the machine it runs on. Debug builds allow
plain HTTP to that one address and nowhere else; release builds allow none.

## Decisions that shape the code

**The engine is the published package, not a port.** `core` depends on
`ca.pranavpatel.algo:gridpointcode` from Maven Central, the same jar anyone can
install. It is Java 21 bytecode, and Android's D8 dexes it at every minimum from
API 21 up; the one Java 11 method it calls is replaced with D8's own copy below
API 33.

**The design has one source.** Colours, Material's colour roles, spacing and the
type scale are generated from [`design/tokens.json`](../design/tokens.json) into
[`design/generated/android`](../design/generated/android), and `designsystem`
compiles them from there. Nothing here restates a value.

**One owner for the place.** Every arrival goes through `PlaceState` in `core`
and returns a new state, and every surface is derived from it in one pass. What
survives a move is decided there and tested: directions survive a nudge, because
that is the same doorway lined up, and do not survive going somewhere else.

**The map is an illustration of an answer the screen already has.** It is
drawn only in `map`, it is the only thing that uses the network, and the panel
never reads from it. A tap or a nudge leaves the camera where the reader
is looking; anything arriving from elsewhere is flown to. The camera is padded
for the search bar and the sheet, so the cell is centred in what can be seen.

**The drawing wears the map's colours, not the app's.** A reader can choose a
light map in the dark theme or a dark map in the light one. Measured against
each style's own background, the light theme's brass is about 5:1 on the light
maps and 1.4:1 on fiord; the dark theme's is 3.5:1 on fiord, 8.5:1 on dark and
2.1:1 on the light maps. So a light map is drawn on in the light theme's inks and
a dark map in the dark theme's, whatever the rest of the screen is wearing.

**A fix is kept, never averaged.** Fixes a second apart from one device share
their errors, so an average would claim an accuracy it has not got. The app
keeps the tightest fix and the device's own estimate for it. A looser fix never
undoes a tighter one, a fix that arrives after the reader has tapped, typed or
nudged is ignored, and a new press starts over, since the reader may have
walked a kilometre since the last. Location comes from the platform's own
`LocationManager`, so nothing needs Play services.

**Names come from the website's index, read the way the site reads it.** One
sorted file of a third of a gigabyte and a table of every 512th line, both static
files on the site's host; a search is one range read of about 24 kilobytes, and
no service is asked anything. `core` folds a name exactly as the builder sorted
the file (the tests hold it to the builder's own answers) and decides where to
read. It starts at the last mark strictly before the query, not at or before it:
about a quarter of the marks land partway through a run of one name, and
starting at such a mark skips the largest places of that name.

**The recovery box is 1,562 finest rows, not half a level-5 cell.** Section
12.3 prints it as 0.03598848 by 0.04798464 degrees: the whole rows and columns
within half of a level-5 cell's 3,125. Exactly half a cell is 0.036 by 0.048,
about a metre and a half more, and a landmark in that margin recovers another
cell's copy of the same five characters. `core` takes the figure from the
library's cell sizes and a test holds it to the printed one. The box is degrees,
never a radius in metres, which the tests check at Helsinki as well as the
equator.

**An anchored line is never read against wherever the screen is.** A bare short
form is recovered against the place already shown, because that is the reader's
own reference. A short form given with a landmark names its own reference, and
recovering it against anything else, before the landmark is looked up or when it
cannot be, names somewhere plausible and wrong: `-98NM9` read against London is
`#R0NJ0-98NM9`, a real place about three kilometres east of Westminster. So
nothing moves until one landmark is certain, and `core` has a test that fails if
it ever does.

**Kept means asked for.** Only an area the reader chose to keep is reported as
kept, because an area merely glanced at is not ready for a journey. Shards met
while looking around are cached too, in the app's cache where the system may
clear them, trimmed to 20 MB, and never counted. A kept shard is used when it
comes from the archive the site is serving, or when there is no connection to
ask; offline, an older landmark is better than none. With no connection, an
anchored line is read against the landmarks on the device only when it gives
its region, since nothing on the device can show that a name alone is unique.

**The kept area is centred on the place, not a cell of the grid.** The website
keeps the cell one level above the shards, which is the same size but fixed to
the grid: downtown Toronto sits in the corner of its cell, which reaches 190 km
west and stops 15 km east. The app keeps the five by five block of shards around
the place's own instead, so it reaches 100 km every way.

**The app names other systems, and nothing else in the repository does.** Turning
somebody's existing location into a code is the strongest reason to use one, and
a reader is owed the name of what they gave. The names live in one parser file,
its tests and the app's own strings. Each system is read by its owner's
published rules and tested against its published answers.

**A converted code says how much its source said.** A code names 2.5 m wherever
it came from. When the source named more, a square 14 m across or a link whose
coordinates stop at two decimals, the card says so and calls the code the centre
of that area, not a door. A link that carries only the middle of a map view,
rather than a pinned place, says that instead.

**Ten characters that could be either system's are read as this one's.** One
system writes ten symbols with no separators, all from within this alphabet, so
the same text is a code here and a place there. It opens as a code here, and
the list under the field offers the other reading, one tap away. Written with
that system's old separators, it can only be the other, and opens as that.

**The emergency card puts the coordinates first.** It is for reading to
somebody who has never heard of this format, and any operator can take a
latitude and longitude. The code comes second, with how to say it. The
coordinates are written with a full stop in every language, because a decimal
comma between two numbers already separated by one is a trap.

**Saved places stay on the device.** One small file in the app's own files, which
the device's backup includes, so a new phone has them. No account to sync them
to and no export, by the decision the website made when it took its export down.
One place per code: saving a code again changes it rather than listing it twice.

**The map is not downloaded, only kept as it is drawn.** An area's map at full
detail is about 17,000 tiles, 50 to 150 MB, which a phone could hold. But the
tile provider's terms rule out collecting its data in automated ways without
permission, and fetching an area's tiles in the background would be exactly
that. So the map library's own cache is raised from 50 MB to 200 MB, and offline
the map is whatever the reader has already looked at.

**Every callout word begins with its letter.** Appendix D.2 of the specification:
the listener keeps the first character and discards the rest. So where a
country's table says a letter's name instead of a word, as the Italian one does
for H, J, K, W and X, the table's own alternative word is used, because the
Italian name of K begins with C. German uses DIN 5009 as revised in 2022, the
towns, and says two as "zwo". Spanish has no national table; the words follow
the one the telephone service in Spain used, with Granada for G, since the G of
Gerona is said as a J. A test holds every set to the rule.

**Nothing is read in the wrong voice.** German words in an English voice are a
string of wrong sounds, worse than silence, so with no voice for the listener's
language the button is off and the screen says why. The listener's language
defaults to the reader's own, and to the international words when the reader's
language has no table here.

**Coordinates are read digit by digit after the point.** A speech engine for a
language that writes a decimal comma may take the full stop in `43.650006` for
a thousands separator. So the emergency card says the whole degrees as a number
and each decimal digit on its own, with that language's word for the point,
from the written text, so what is heard is what is on the screen.

**An area is written as a cell, with no hash and no hyphen.** Section 18.1 of
the specification: a cell is never presented as a code, and ten characters is a
code while fewer is a region. A link to an area uses the same address and
parameter as a link to a code, `?c=G3RJM`, and its length says which it is.
Typed into the field, a cell is read as an area only when it mixes letters and
digits: a run of letters alone is a word as often as not, and a run of digits
alone is a postcode. A link is written by a machine, so any cell in one is an
area. A code missing its last character opens as its building-sized area, which
holds the door it meant. The levels are named from the specification's table of
scales.

**Three SDK levels, three meanings.** `compileSdk` 37 because current AndroidX
libraries compile against it. `targetSdk` 36 for runtime behaviour. `minSdk` 26,
which covers the streaming calls in the library, which need 24.

## Not here yet

The bundled typefaces, the launcher icon, verified links (which need the signing
fingerprint published in `/.well-known/assetlinks.json`), and the Wear OS, car
and headset modules.
