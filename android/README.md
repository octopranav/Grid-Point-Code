# Grid Point Code for Android

The application for phones, tablets, foldables and Android on the desktop, a
companion for Wear OS, screens for the car, and later for headsets. One APK for
the large screens, laid out by the size of the window rather than the kind of
device, one for the watch, and one for a car running Android Automotive.

What is built so far is the place: a map with the cell drawn on it, the panel
that says everything about it, and a button that finds where the device is; and
on the watch, where the wrist is and the way to a saved place. The map is the
only thing that uses the network, and nothing waits for it; every code, check
and correction is arithmetic on the device.

## What is here

| Module | What it holds |
| --- | --- |
| [`core`](core) | Plain Kotlin on the JVM, no Android. Wraps the published package and adds only what the website's own `lib/` adds: the written forms, the read-aloud line in the words of seven languages, links with directions, one reader for every kind of input, the nudge pad, cell sizes, the accuracy rule, the rules for what survives a move, reading the website's name index, which landmarks can anchor a short form, saved places, reading locations written in other systems, the emergency card, and areas |
| [`designsystem`](designsystem) | The theme, the type scale, the bundled typefaces, the shapes and the ten-cell mark, built from the files [`design/build-tokens.mjs`](../design/build-tokens.mjs) generates |
| [`map`](map) | MapLibre Native on the website's tile provider, its five styles, the cell drawn in brass with its eight neighbours faint around it, and a tap to place a point |
| [`notices`](notices) | The notices more than one app ships, the Apache License, the Play services notices and the MIT notice the car's libraries bring, and reading them: an app's list of libraries, and a notice laid out in blocks a screen can show |
| [`car`](car) | The car's screens, one set for Android Auto and Android Automotive: where the car is, the saved places on the car's own map, one place with Navigate and Read aloud, and going to a typed code |
| [`app`](app) | The place screen, the view model that holds the place, read aloud, fetching the name index and the landmark archive, and the ways a place arrives from another app |
| [`wear`](wear) | The Wear OS app: where the watch is, the saved places nearest first, and the way to one, with the saved places kept in step with the phone's through the Wear Data Layer; its tile, its complication and its notices |
| [`automotive`](automotive) | The Android Automotive app: the car's screens, run by the car itself with no phone, and their notices |

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
- Offers the website's basemaps on the settings page: match the theme
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
  searches the names kept on the phone, as below, says names need one when
  there are none, and codes and coordinates carry on working.
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
- Keeps a country's place names on request, from the settings page: that
  country's lines of the website's name index, with the size of each shown
  before it is fetched. With no connection a name is then found in every
  country kept, largest first, ahead of the landmarks already seen, and a name
  two places share in one region, which the landmark archive leaves out, is
  found too. A country is kept whole or not at all, and Forget gives it back.
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
  directions to its door. Saved places are listed on their own tab,
  drawn on it as small rings, and found first as a name is typed, with no
  connection. Opening one brings its directions back, and Go on a saved name
  goes straight there. The list puts the nearest first, each with its distance
  and an arrow pointing its way, measured from the device's fix or the place on
  the screen, and can be put back in the order they were saved.
- Puts chosen saved places in a walking order: from the device's fix or the
  place on the screen, or from one end when there is nowhere to start, worked
  out on the phone in straight lines, with each leg, the whole, and the list to
  share in that order, a link to each stop.
- Shows a QR code for a place's link, directions and all, or an area's, to be
  scanned off the screen by any phone, with the screen at full brightness
  while it is up.
- Offers the codes one slip away when a typed code lands far from the reader:
  a code heard over a telephone with one character wrong opens as typed, and
  the panel says where it landed and lists the codes one character changed, or
  two swapped, that land near the reader's fix or the place they had, each
  with its distance and the character that differs.
- Answers a keyboard and a mouse, for Android on a desktop or a tablet with a
  keyboard: the arrow keys nudge the place a cell, Ctrl+C copies its code and
  Ctrl+K goes to the search box; a right-click on the map, or a long press on a
  touch screen, shows any point's code and distance with ways to choose, save
  or copy it; and a wide window has buttons to zoom.
- Shares an area as well as a point: a place's card lists the areas around it,
  a region down to a building, each written as its first few characters with
  its size where it lies. Choosing one frames it on the map in brass, and it can
  be read aloud, copied or shared with a link. A link to an area opens as that
  area, and so does a cell typed into the field.

### On a watch

- Shows where the watch is as the ten-cell mark, in two rows of five, with how
  far to trust the fix, finding it only while the app is open.
- Reads the code aloud in the listener's language, as the phone does, through
  the watch's own speech engine.
- Saves where the watch is, with no name; the phone's keyboard is the place to
  give it one.
- Lists the saved places nearest first, each with its distance and an arrow
  that turns with the watch to point its way.
- Walks to one: a large arrow, the distance, and its short form to say.
- Has a tile with the code the app last found, when and how closely, and Read
  aloud at its foot, which opens the app and reads where the wrist is now.
- Has a complication for any watch face with room for a short text: the last
  short form beside the four bars, and how long ago, where the face shows it.
- Shows its open-source notices, from the foot of the Here screen.
- Works with no phone. The phone only brings the saved places, and takes back
  the ones saved on the watch, whenever the two are in reach.

### In a car

- On Android Auto, the phone app's screens on the car's display: where the car
  is, then the saved places nearest first, each numbered on the car's own map
  with its distance and code.
- One place at a time: its code, how to say it, how far and which way with the
  directions to its door, Navigate, which hands it to the driver's own
  navigation app, and Read aloud, which lowers whatever is playing while it
  speaks.
- Goes to a code typed with the car's keyboard, which the car offers only when
  parked: a code, a short form read against where the car is, coordinates or a
  link, read as the phone reads them.
- On a car running Android Automotive, the same screens from an app of its own,
  with no phone, so no saved places: where the car is, going to a code, and the
  open-source notices.
- Asks where the car is only while its screens show, says when location is
  switched off and where to switch it on, and asks for the permission from the
  car's screen.

## Building

```
./gradlew :core:test :app:assembleDebug :wear:assembleDebug :automotive:assembleDebug
```

Needs JDK 17 or later and the Android SDK with platform 37. Continuous
integration runs these on every pull request.

To try the map against a local copy of the styles, for instance on a machine
that cannot reach the tile host, point a debug build at it:

```
./gradlew :app:assembleDebug -Pgpc.styles=http://10.0.2.2:8099/styles/
```

`10.0.2.2` is the emulator's name for the machine it runs on. Debug builds allow
plain HTTP to that one address and nowhere else; release builds allow none.

The place-name packs can be pointed at a local copy the same way, for instance
one written by `node web/scripts/build-names.mjs --packs <dir>` from a few
places:

```
./gradlew :app:assembleDebug -Pgpc.packs=http://10.0.2.2:8099
```

### A release

```
./gradlew :app:bundleRelease
```

The bundle the store takes, shrunk and optimised by R8. CI builds it on every
pull request, unsigned, because a class removed that something still needed
breaks only the release. It needs no shrinking rules of its own: the JSON the
app reads is parsed with the platform's `org.json`, and the map library brings
its own rules.

It is signed for upload when four values are set, in `~/.gradle/gradle.properties`
or as environment variables, and never in the repository:

| Gradle property | Environment variable | What it is |
| --- | --- | --- |
| `gpc.upload.store` | `GPC_UPLOAD_STORE` | the path to the upload keystore |
| `gpc.upload.storePassword` | `GPC_UPLOAD_STOREPASSWORD` | its password |
| `gpc.upload.alias` | `GPC_UPLOAD_ALIAS` | the key's alias in it |
| `gpc.upload.keyPassword` | `GPC_UPLOAD_KEYPASSWORD` | the key's password |

Without all four the bundle is built unsigned. With the store signing the app
for users, this key only proves an upload came from its owner, and a lost one
can be replaced.

The bundle is about 23 MB, of which the map library's native code for four
processor families is most. The store hands each device only its own, so a
phone downloads about 16 MB.

```
./gradlew :wear:bundleRelease
```

The watch's bundle, about 6 MB, signed by the same four values. It has the
phone's application ID, `com.gridpointcode`, and must have its key: the Data
Layer carries items only between apps that share both.

```
./gradlew :automotive:bundleRelease
```

The car's bundle, under 1 MB, signed by the same four values, with the same
application ID, so the store lists all three under one name. Android Auto needs
no bundle of its own: it is the phone app's.

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

**A country's names are the index's own lines, from the repository's releases.**
The builder writes each country's lines, in the index's order, beside the index
it writes, gzipped, with a list of every country's lines and bytes and the
regions table. The Landmarks workflow publishes them as the `place-packs`
release, the list last, so the list never names a pack not yet there. They are
not on the site because the site's host allows a gigabyte and the site already
uses more than half of it; the whole index gzipped is 120 MB. On the phone a
pack is unzipped as it arrives and gets the same table of every 512th line the
site's file has, so a keystroke reads one block of one file, and the kept packs
are searched together and merged into the order the site's file has, by the
same code. A pack counts as kept only when its lines and bytes are what the list
says; one cut short is thrown away. A landmark already seen whose name and
region a kept pack also gives is that place, since the archive keeps only names
unique within their region, and is shown once, as the pack writes it.

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

**Saved places stay on the device, and on a watch that has the app.** One small
file in the app's own files, which the device's backup includes, so a new phone
has them. No account to sync them to and no export, by the decision the website
made when it took its export down. One place per code: saving a code again
changes it rather than listing it twice. `SavedShelf` owns the list for the
whole app, so the screen and the listener that takes in the watch's places
change it in one place, and each change is written in the order it was made.

**A walking order is worked out, not sorted.** The canvas proposed sorting the
codes, since codes sort by cell. Measured against the reference implementation
over two hundred sets of twenty stops scattered across central Toronto, a round
in code order came out 1.55 times the length of a worked-out one on the median
and twice at the worst, with single legs longer than the area was wide: sorted
cells jump across the boundaries between them. `roundOf` in `core` takes the
nearest stop next, then swaps any two legs while that shortens the whole, from
the reader, or from each stop in turn when there is no start. The end is left
free, since nobody has to walk back. The round is short, not proven the
shortest, and the page says the distances are straight lines. A test holds it
to being shorter than code order with no two legs crossing.

**The watch shares the phone's list through the Wear Data Layer.** The phone's
whole list is one item, `/saved`, in the encoding `SavedSync.kt` in `core`
writes: a header naming its version, then a place to a line, with tabs, line
breaks and backslashes in a name escaped, so a name arrives exactly. A place
saved on the watch is its own item, `/from-watch/<code>`; the phone merges it,
the later saving of a code winning, and deletes the item. Until the phone has,
its list does not have the place, so the watch lays the items still waiting over
each list that arrives, and deletes its own once the phone's list shows the
place, so a place saved on the watch never vanishes from it on the way. A list
with no line that reads is damage and is not taken; a list emptied on purpose
is. The watch app says it is there with the Data Layer capability `gpc_watch`,
and the phone hands its list to the Data Layer only when a watch says so, and
takes it back out when none does, so a phone with no watch app keeps its places
in its own file alone. On a phone with no Wear OS companion app, the Data Layer
answers every call as unavailable, and the app carries on without it.

**The map's credit is drawn by the screen, in full.** Every basemap is
OpenStreetMap data, whose licence asks for credit in a corner of the map with a
way to its copyright page, and the tile provider asks for its own line beside
it. The map library's own credit button sits under the sheet, so it is turned
off and the line the map's sources declare is drawn in the corner that can be
seen, each name a link, shown in full as the website shows it. Until a source
has declared its line, the provider's own words stand in.

**The open-source notices ship as their authors wrote them.** MapLibre Native
and its gesture library are under the BSD licence, which requires their notice
in whatever is distributed, and OkHttp carries a notice for the list of public
suffixes it bundles. Each is in the app's assets, copied unchanged from the
release the app uses, and the Apache License 2.0 is there once for the rest.
The Play services libraries, under the Android Software Development Kit
License, carry the notices of what is compiled into them; `play-services.txt`
has each of those once, as the four libraries carry it. The two notices both
apps need, that one and the Apache License, are in the `notices` module once.
Each app's `licences/components.txt` names every library its release ships with
its licence, and `checkNotices`, one script both apps apply, fails when that
list and the release's resolved libraries disagree, or when a notice was copied
from a release other than the one resolved. A licence is read from each
library's own POM, not assumed from its group: the tile library's
`protolayout-external-protobuf` is Protocol Buffers under the BSD licence, and
its copy of the terms leaves out the copyright line they ask to be reproduced,
so `protobuf-lite.txt` gives that line from the project's own LICENSE before
the terms. The phone shows them all on its settings page, and the watch from the
foot of its Here screen.

**The tile and the complication show what the app last found, and never
look.** The watch is found only while the app is open, as the privacy page
says, so neither asks where it is. The app keeps its last fix, and asks both to
redraw when the code changes, the fix tightens, or a minute passes, not with
every fix. Each shows it for an hour, as the first entry of a timeline whose
second entry offers to open the app, so an old code goes without another
request; an entry left without an end ends at zero, not never, and draws the
tile empty. The tile says when the code was found as a time, beside the clock
the watch draws above every tile, where the canvas has "2 min ago": a count of
minutes would need the tile redrawn every minute or a dynamic string older
watches cannot show. The complication's title is the watch's own count since
then. Read aloud on the tile opens the app, which reads the next place it
finds, not the code the tile showed.

**Three tabs: the map, the saved places, the settings.** A bar along the bottom
of a phone and a rail down the side of a wide screen, as the canvas draws both.
The map keeps only the buttons that act on it now, the emergency card, the zoom
on a wide screen, and finding the device; the basemap, the listener's language,
folded sections, the keys, the privacy page, the notices and the version are on
the settings page, set once and left. A place arriving from anywhere, a link, a
saved place opened, a code, is shown on the map whichever tab it arrived on,
and Back from the other tabs is the map.

**The panel is laid out as the canvas draws it.** The card under the code has
four tiles, an icon over each word, reading aloud first and filled because
saying the code is what the card is most often for; the icons are the canvas's
own paths, not an icon library's. Every section below folds, and what a reader
folds stays folded across launches, so somebody who never nudges does not
scroll past the pad each time; nothing is folded until they fold it, so nothing
is hidden from a reader who has not chosen to hide it.

**The QR code is the website's, drawn by the standard encoder.** The payload is
the link, as the site's is, so scanning it opens the place with or without this
app; error correction M and a four-module quiet zone, as the site sets them;
dark on white in either theme, because a camera nobody here has seen reads it.
ZXing's core draws it, the one library added for it, which the release shrinks
to its encoder; the notices check refused the build until it was listed. Each
module is a whole number of pixels, so none is drawn blurred across two.

**A slip is offered, never declared.** Section 15 of the specification forbids
claiming to detect typos, and a code can be right and far. So a typed code
always opens as typed, on the map, and the offer says only where it landed and
what one slip would have made of it. The candidates and their order are the
library's `SuggestCorrections`, section 15.3, at level 6, the window the
specification recommends for a device fix or a named place. It asks only about
codes typed or pasted in, not links, which nobody heard; not a code with a
check character that holds; and only against a reference the reader had, the
device's fix or the place chosen before, never the opening example.

**At a desk, the keys do what a finger does.** The arrows nudge as the pad
does, and wait while a text field has the keyboard, where they move the cursor,
as Ctrl+C then copies the selection rather than the code. Ctrl+K reaches the
search box from anywhere, and on a phone it lowers the sheet first, so the box
the keys go to can be seen. The screen keeps focus from the start, so a key
pressed before anything is touched still counts, and it takes focus back when
a search is done or Escape leaves the box. The keys are named at the end of the
panel only when a keyboard is attached.

**The backup leaves the map's tile cache out.** The map library keeps its cache
in the app's own files, where Android's backup looks by default, and lets it
grow to 200 MB. A cloud backup over 25 MB is refused whole, so the cache would
have taken the saved places down with it. The backup rules in `res/xml` exclude
it; what a backup takes is the saved places and the two settings, a few
kilobytes.

**What the app sends is on the site's privacy page.** Three hosts, the tile
provider, this site's own files and GitHub's release downloads for a country's
names, and Google Play services for a watch that has the app, and nothing else; the app links to
[`/privacy`](https://gridpointcode.com/privacy) from the end of the panel, as
the store requires of an app that reads the device's location. A change that
sends something new somewhere new changes that page in the same pull request.

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

**The typefaces are the designers' own files, unmodified.** Bitter, IBM Plex
Sans and IBM Plex Mono, the site's three, under the Open Font Licence. Both
licences reserve the font's name, so a subset or a converted copy could not ship
under it; the files are the ones published in the google/fonts repository, byte
for byte, with each licence in the app's assets. They add about 0.6 MB to the
download. [`audit/fonts.py`](../audit/fonts.py) pins each to the hash of its
upstream file. Bitter and Plex Sans are variable fonts, and each weight is set on
the weight axis explicitly: asked only for a weight, Compose draws Bitter in its
default master, its thinnest.

**The icon is the site's, drawn by the same script.** Four bars at levels 1,
4, 7 and 10, the ten-cell mark reduced to its ramp, as the site's favicon and
installable icon are. [`design/build-icons.mjs`](../design/build-icons.mjs)
writes it as an adaptive icon from the same tokens and the same geometry, so an
installed site and the installed app look alike, and CI's check of the site's
icons covers the app's. Vector layers rather than pictures, because every
Android the app runs on takes an adaptive icon. With themed icons on, one colour
cannot carry the ramp, so the monochrome layer carries it in opacity, each bar
as opaque as its tint is dark. The window behind the app while it starts is the
page's own ground in either theme, so the icon on it is not a flash of another
colour. For the store listing, the site's `icon-512.png` is already the 512
pixel square the store asks for.

**The car draws the screens; the app describes them.** Android Auto and Android
Automotive both take templates from the Car App Library and draw them in the
car's own style, so `car` holds one set of screens and each app says only what
its car may have: `PhoneCarService` hands over the phone's saved places and the
listener's language, and `CarService` in `automotive` hands over none, since a
car with no phone cannot reach them. The places are a point-of-interest list on
the car's own map, which holds only rows with a distance: so it is shown once
there are saved places and a fix to measure them from, and until then, and in a
car running Android Automotive, the same rows are a list. A row the map refuses
is not reported as such: the car shows its placeholder for a missing maps app
instead, which is how the rule was found. The car draws a distance on a line of
its own and drops whatever follows it on that line, so a place's code and a
direction each have their own line. What is typed is read by the phone's own
`PlaceState.opened`, and a short form waits for a fix rather than being read
against anywhere else. A release answers only the car hosts in the library's
own list; a debug build answers any, for a test head unit. The oldest car host
supported is level 2, for the long message the notices use; a header replaces
a pane's title at level 7, and is used where the car has it.

**Three SDK levels, three meanings.** `compileSdk` 37 because current AndroidX
libraries compile against it. `targetSdk` 36 for runtime behaviour. `minSdk` 26,
which covers the streaming calls in the library, which need 24. The watch app's
`minSdk` is 30, Wear OS 3, the first release built on Android 11, and the car
app's is 29, the oldest release the car's template host runs on.

## Not here yet

Verified links (which need the signing
fingerprint published in `/.well-known/assetlinks.json`), and the headset
module.
