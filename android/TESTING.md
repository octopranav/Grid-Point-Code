# Testing the Android app

Everything a machine can check is checked by a machine, on every pull request
where it can be. What only a person can judge, on real devices in the real
world, is set out at the end as a guide to work through before each release.

## The layers

| Layer | What it holds the app to | Where | Runs |
| --- | --- | --- | --- |
| Logic | The answers the website gives, for every place, form and rule | `core/src/test`, `map`, `app`, `wear`, `car`, `notices` unit tests | CI, every PR |
| The app on a device | What a reader does, done on an emulator: typing, links, a code heard wrong, nudging, sharing, saving, the emergency card, the keyboard, searching online and off | `app/src/androidTest` | CI, every PR |
| Accessibility | Google's Accessibility Test Framework over the whole screen after every action and at every screen the device tests reach | the same tests, through `accessibleRule()` | CI, every PR |
| Hostile conditions | Turned on its side, the dark theme, the largest text, recreated by the system, location refused, no connection | `ResilienceTest`, `LocationRefusedTest`, `KeyboardAndSearchTest` | CI, every PR |
| Static checks | Lint on every module, the notices, the release's merged manifests | Gradle, `gradle/notices.gradle.kts` | CI, every PR |
| Design | Every screen against a reference image, light and dark, phone, tablet and foldable, large text, long and right-to-left text | next | |
| Robustness | StrictMode, leak detection, a stress run of random input, the process killed and restored | next | |
| Performance | Start-up and frame timing, and a baseline profile so the release starts fast on a reader's phone | next | |
| The watch and the car | Their screens, the tile, the complication and the car's templates | next | |

## Running them

The logic, from `android/`:

```
./gradlew :core:test :map:testDebugUnitTest :app:testDebugUnitTest :wear:testDebugUnitTest :car:testDebugUnitTest
```

The app on a device, on an emulator Gradle makes and boots itself, the same
Pixel 7 on Android 16 here and in CI:

```
./gradlew :app:phoneDebugAndroidTest
```

One class, while working on it:

```
./gradlew :app:phoneDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gridpointcode.SavedPlacesTest
```

The report is `app/build/reports/androidTests/managedDevice/debug/phone/index.html`,
with each test's logcat beside the results in
`app/build/outputs/androidTest-results/managedDevice/debug/phone/`.

### How the device tests are written

- **As a reader, not as the code.** `Reader` drives the app by what the screen
  says and what a screen reader hears: a place on the card is asserted as the
  callouts its mark is heard as, worked out by the same `core` function the app
  uses, never by reading the view model. A refactoring a reader would not
  notice does not break a test; a change a reader would trip over does.
- **Each test from a fresh install.** The test orchestrator runs every test in
  its own process and clears the app's data first, so the saved places, the
  settings and the caches one test leaves cannot change another's answer. A
  test that hangs is stopped after three minutes and the run goes on.
- **Only what is on the screen is touched.** A control pushed below the edge
  still answers a test's click, at the corner of the screen, where the map
  takes it as a place picked. `Reader` checks a control is showing before
  touching it, and `Reader.reach` opens the sheet and scrolls to what is below
  the card, as a reader drags it up.
- **The device's location is the test's.** `Reader.fix` reports a fix through
  every provider the app listens to, twice a second, as a receiver outdoors
  does; a test provider gives a listener only what is sent after it starts.
- **Accessibility on every action.** The rule runs the Accessibility Test
  Framework over the root view after each click, typing and scroll, and
  `Reader.audit()` runs it at screens reached without one. An error, a control
  under 48 dp, text too faint for its background, an unlabelled button, fails
  the test.
- **System dialogs through UiAutomator.** The permission question is the
  system's, outside the app; `LocationRefusedTest` answers it the way a reader
  who says no does, and closes it if the test fails first, since clearing the
  app's data does not close the system's dialog and it would take the focus
  from every test after it.
- **The device as a reader holds it.** `ReadyDevice` runs before every test:
  the screen awake, the lock screen gone and kept away, and no "isn't
  responding" dialogs. An emulator booted fresh, as CI's is, starts on its lock
  screen, and one drawing in software is slow enough that System UI is often
  reported as not responding; either takes the focus from the app, and then the
  keyboard does not open and the clipboard reads as empty.
- **A failure explains itself.** A test that waited for something lists what
  the app's screen held instead, as a screen reader finds it. `RecordOnFailure`
  adds every window on the device, whose it is, which one has the focus and the
  words in it, and whether the lock screen was up, which reaches the system
  dialogs the app's own dump cannot. A screenshot goes to
  `app/build/outputs/managed_device_android_test_additional_output/`, kept only
  from a run's last test, since clearing the app's data before each test
  clears that folder too: run the one test on its own to get it.

## What you test yourself

These need a person, real hardware, an account or the outdoors. Work through
them before each release that changes what they touch, and before the first one
in full. Each says what to look for; anything that surprises you is a bug.

### Devices

Test the release build installed from the Play Console's internal testing
track, not a debug build:

- [ ] A current Pixel or similar on the newest Android.
- [ ] A Samsung phone, whose system differs from Google's in fonts, dialogs and
  power management.
- [ ] A low-end or old phone, Android 8 to 10 with 2 to 3 GB of memory, which is
  where start-up time, the map's memory and scrolling suffer first.
- [ ] A tablet, and a foldable if you can borrow one: fold and unfold with a
  place open, and the place stays.
- [ ] Android on a desktop or a Chromebook, with a keyboard and a mouse: the
  arrow keys, Ctrl+C, Ctrl+K, right-click on the map, resizing the window.

### Location in the real world

- [ ] Outdoors, press the button and hold still: the fix tightens to one cell
  within the thirty seconds, the disc shrinks with it, and listening stops on its
  own. Compare the code with the website's for the same spot.
- [ ] Indoors and in a car park: the accuracy the card states is honest, and the
  code is not presented as your door when the fix is wide.
- [ ] Airplane mode with location on: the fix still comes, the code still works,
  the map shows what was kept.
- [ ] Walk a few hundred metres with the app open: nothing keeps listening after
  the fix, and the battery use the system reports for the app is small.

### Reading aloud

- [ ] Each of the seven listener's languages with the phone's own voices: the
  words are that language's spelling table, read in that language's voice.
- [ ] A language with no voice installed: the words stay on the screen and the
  speech settings are one tap away.
- [ ] Read a code to another person over a telephone, and have them type it into
  the website: the check character catches a slip.

### Accessibility, by people

The automated checks find what can be measured. These find what cannot:

- [ ] TalkBack, eyes closed: search a code, hear the card, nudge, save with a
  name, find it in Saved, open the emergency card, and hear the coordinates.
  Every control says what it does, and the order makes sense.
- [ ] Switch Access or a keyboard alone: reach every control.
- [ ] Display size and font size both at their largest: nothing cut off, nothing
  overlapping, every button reachable.
- [ ] Voice Access: "tap Save", "tap Share".
- [ ] Colour correction and the colour-blind simulations in Developer options:
  the cell, the level tints and the brass still read.

### Sharing, in the apps people use

- [ ] Share a place to a messaging app, email and SMS: the link previews sensibly,
  the directions arrive, and the link opens the app on a phone that has it and
  the website on one that does not.
- [ ] Show the QR code to another phone's camera, an iPhone too: it opens the
  place, directions included, with or without the app.
- [ ] Select a code in a message and choose Grid Point Code from the menu.
- [ ] Open a location shared from a map app and from a geo link.

### The watch

- [ ] Pair a real Wear OS watch, save a place on the phone and see it on the
  watch; save one on the watch and see it on the phone. Then again with the
  phone and the watch apart, both online, which goes through Google's servers.
- [ ] The tile and the complication on a real watch face: the last code, its
  age, and Read aloud opening the app and speaking.
- [ ] A day of wearing it: the battery the watch reports for the app is small.

### The car

- [ ] Android Auto on the Desktop Head Unit or a real car: where the car is, the
  saved places on the car's map, a place's card, Navigate handing over to your
  navigation app, Read aloud lowering the music, the keyboard only when parked.
- [ ] An Android Automotive car or its emulator with Google's apps: the same,
  without saved places.

### Offline, in the field

- [ ] Keep an area and a country's names before a trip, then switch off mobile
  data: the anchor list, reading an anchored line and searching by name work.
- [ ] A few days later, still offline: the kept areas and names are still there,
  and the map shows what was looked at.

### The emergency card, rehearsed

- [ ] Hold the app's icon and open the card: the latitude and longitude come
  first in large type, the screen stays awake, and it works with no network and
  no SIM.
- [ ] Read the card to someone as you would to a dispatcher: the coordinates are
  enough on their own.

### Performance on a real phone

- [ ] Cold start on the low-end phone: the map and the card appear quickly, with
  no frozen frames.
- [ ] Scroll the saved list and the card's sections: smooth.
- [ ] Leave the app open on the map for ten minutes: memory and battery stay calm.

### Privacy, verified

- [ ] With a traffic monitor on the device, such as PCAPdroid: the only hosts the
  app reaches are the ones the privacy page names.
- [ ] Restore a backup to a new phone: the saved places and the two settings
  come back; the tile cache, kept areas and kept names do not.

### Play

- [ ] Read the pre-launch report from the internal testing track: Google's robot
  runs the release on real devices and reports crashes, accessibility issues and
  screenshots.
- [ ] Watch Android vitals through the closed test: crashes and ANRs at or near
  zero.
- [ ] Install the previous release, save places, then update to the new one:
  everything is still there.
