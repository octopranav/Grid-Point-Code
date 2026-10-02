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
| Design | Every screen against a reference image: light and dark, a small phone, a phone, a foldable and a tablet, the largest text, a longer language and right to left | `app/src/test/kotlin/com/gridpointcode/screens`, images in `app/src/test/screenshots` | CI, every PR |
| Robustness | StrictMode and leak detection around every device test, a seeded stress run of random input, the process ended and the screen put back | `NoStrictModeViolations`, LeakCanary, `RandomInputTest`, `app/src/test/.../ProcessDeathTest.kt` | CI, every PR |
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

The screens, drawn on this computer and compared with the images kept in the
repository, which is what CI does:

```
./gradlew :app:verifyRoborazziDebug
```

After a change that is meant to move a screen, draw them again, look at every
image that changed, and commit the new ones with the change, so the review
shows the screens beside the code that moved them:

```
./gradlew :app:recordRoborazziDebug
```

A screen that no longer matches leaves the old image, the new one and the
difference between them in `app/build/outputs/roborazzi/`, and CI keeps that
folder when the check fails.

### How the screens are drawn

- **The real screen, on the computer.** Robolectric runs the app's own screen,
  view model and resources on the desktop's JVM, drawing with Android 16's own
  graphics stack, and Roborazzi records the whole screen, dialogs included.
  Thirty-five screens take about a minute.
- **Held still, so a change means something.** The map cannot be drawn off a
  device, so `PlaceMap` draws a stand-in in inspection mode: the ground, the cell
  and the credit where the real ones go. Every request to the network is turned
  away at once by a proxy on the computer, so nothing the site serves can change
  a screen. The landmarks are Toronto's shard of the archive, kept with the
  tests and credited in `app/src/test/resources/landmarks/SOURCE.md`, found where
  the app caches what it met while looking around.
- **Each screen waits for its answers.** The landmarks and the offline standing
  are worked out off the main thread, so a screen is recorded only once they are
  in for the place shown. A dialog that opens with a text field never goes
  still, since its cursor blinks; its clock is stopped and stepped by hand to
  the same moment every time.
- **Where the computer differs from a phone.** A dialog is drawn the full width
  of the screen; on a device it has the platform's margins. Everything else
  checked against the emulator matched, right to left included.
- **Looked at, not only compared.** The images are for reading. Looking at the
  first set found a code with a wrong check character searched as a name, the
  library's reason codes shown to readers, codes, the compass pad and the
  emergency card's coordinates turned round in right-to-left languages, the
  code too wide for a 360 dp phone, and the card's actions hidden at the
  largest text. Running the device tests over those fixes then found the map
  taking a reader away from Saved the moment a fix arrived.

### How robustness is held

- **StrictMode, on every device test.** The debug build turns it on before
  anything else runs (`DebugApplication`): a file read or written on the main
  thread, a request to the network there, anything left open or registered.
  `NoStrictModeViolations` fails a test on any violation the app's own code
  caused, judged by the first frame that is not the platform's or the
  language's, so a font Compose loads is Compose's and a preferences read in
  `Preferences` is the app's. The few small reads the first frame needs are
  made on purpose, in `beforeTheFirstFrame`, where the choice can be seen.
- **Leaks, after every device test that passes.** LeakCanary watches every
  activity, view model and view the debug build throws away, and fails the test
  on any still held. The first run found the map writing into a screen already
  thrown away when the phone was turned, which kept the destroyed activity
  alive, 1.5 MB at every turn.
- **Random input, from fixed seeds.** `RandomInputTest` taps whatever the app
  shows, types codes, coordinates, links, names and text no field expects,
  swipes the map, presses Back and turns the phone, 120 times for each of three
  seeds, with the accessibility checks on every tap. Any exception the app
  throws fails it; each action is logged under `RandomInput`, so a failure can
  be replayed from its seed. It stays inside the app and leaves alone what only
  downloads, and links, which open the browser.
- **The process ended, and the screen put back.** Android ends a background
  app whenever it wants the memory. `ProcessDeathTest` saves an activity's state,
  throws the activity and its view model away, and makes new ones from the saved
  state alone: the place and its directions, an open emergency card and a code
  half typed all come back. It runs on the computer, under Robolectric, because
  on the emulator "Don't keep activities" set from a shell is not seen by the
  system and the activity only stops, so a device test passes whether or not
  anything was saved.

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

### The app put away and brought back

- [ ] On the low-end phone, open the emergency card, go to the telephone app,
  open several other apps, and come back: the card is still open, and so is the
  place behind it.
- [ ] Type half a code, switch to a messaging app to copy the rest, come back:
  what was typed is still there.

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
