# Grid Point Code for Android

The application for phones, tablets, foldables and Android on the desktop, and
later for Wear OS, cars and headsets. One APK for the large screens, laid out by
the size of the window rather than the kind of device.

This is the first slice: the place panel, wired to the published library, with
no permissions and no network. Everything on it is arithmetic on the device.

## What is here

| Module | What it holds |
| --- | --- |
| [`core`](core) | Plain Kotlin on the JVM, no Android. Wraps the published package and adds only what the website's own `lib/` adds: the written forms, the read-aloud line, links with directions, one reader for every kind of input, the nudge pad, cell sizes, the accuracy rule, and the rules for what survives a move |
| [`designsystem`](designsystem) | The theme, the type scale, the shapes and the ten-cell mark, built from the files [`design/build-tokens.mjs`](../design/build-tokens.mjs) generates |
| [`app`](app) | The place screen, the view model that holds the place, read aloud, and the ways a place arrives from another app |

## What it does today

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

**Three SDK levels, three meanings.** `compileSdk` 37 because current AndroidX
libraries compile against it. `targetSdk` 36 for runtime behaviour. `minSdk` 26,
which covers the streaming calls in the library, which need 24.

## Not here yet

The map (MapLibre Native on the website's tile provider), locating the device,
search by place name, the landmark that anchors a short form, the bundled
typefaces, the launcher icon, verified links (which need the signing
fingerprint published in `/.well-known/assetlinks.json`), and the Wear OS, car
and headset modules.
