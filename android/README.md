# Grid Point Code for Android

The application for phones, tablets, foldables and Android on the desktop, and
later for Wear OS, cars and headsets. One APK for the large screens, laid out by
the size of the window rather than the kind of device.

What is built so far is the place: a map with the cell drawn on it, and the
panel that says everything about it. The map is the only thing that uses the
network, and nothing waits for it; every code, check and correction is
arithmetic on the device.

## What is here

| Module | What it holds |
| --- | --- |
| [`core`](core) | Plain Kotlin on the JVM, no Android. Wraps the published package and adds only what the website's own `lib/` adds: the written forms, the read-aloud line, links with directions, one reader for every kind of input, the nudge pad, cell sizes, the accuracy rule, and the rules for what survives a move |
| [`designsystem`](designsystem) | The theme, the type scale, the shapes and the ten-cell mark, built from the files [`design/build-tokens.mjs`](../design/build-tokens.mjs) generates |
| [`map`](map) | MapLibre Native on the website's tile provider, the cell drawn in brass with its eight neighbours faint around it, and a tap to place a point |
| [`app`](app) | The place screen, the view model that holds the place, read aloud, and the ways a place arrives from another app |

## What it does today

- Draws the place on a map as the cell it names, not a pin, with the eight
  cells around it. Tap the map to move there. The basemap follows the theme,
  positron by day and fiord by night, as the site's does, and north stays up.
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
  engine, offline.
- Takes a code however it is written, a pair of coordinates, degrees and
  minutes, a geo URI, a gridpointcode.com link or a short form, from the search
  field, from a link, from another app's "open location", or from text selected
  anywhere on the phone.

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

**Three SDK levels, three meanings.** `compileSdk` 37 because current AndroidX
libraries compile against it. `targetSdk` 36 for runtime behaviour. `minSdk` 26,
which covers the streaming calls in the library, which need 24.

## Not here yet

A choice of basemap, locating the device, search by place name, the landmark that anchors a short form, the bundled
typefaces, the launcher icon, verified links (which need the signing
fingerprint published in `/.well-known/assetlinks.json`), and the Wear OS, car
and headset modules.
