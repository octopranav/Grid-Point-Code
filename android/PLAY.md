# Releasing on Google Play

What the Play Console asks for before the first release, worked out here so it
can be pasted in and checked against the code. Nothing in this file is submitted
by itself.

The [privacy page](https://gridpointcode.com/privacy) is the source for every
answer about data. A change that sends something new somewhere new already
changes that page in the same pull request; it changes the
[Data safety](#data-safety) answers here too.

## Before the first upload

- [ ] **The developer account.** A personal account created after 13 November
  2023 must run a closed test with at least 12 testers, opted in for 14 days in
  a row, before it can apply for production access. The rule names personal
  accounts only. Google's help says "after" that day while the rule began on
  it, so an account registered on the 13th itself cannot be placed by its date,
  and an account is created when its registration completes, which can be later
  than the fee was paid. Play Console settles it: if the app's dashboard asks
  for a closed test before production access can be applied for, the rule
  applies.
- [ ] **Create the app** as Grid Point Code, an app, free. The application ID is
  `com.gridpointcode` and is permanent once the first bundle is uploaded. The
  default listing language is English (United Kingdom), which is the spelling
  the app and the site use.
- [ ] **Play App Signing.** Google keeps the key the app is signed with for
  readers; this repository's four upload values (see
  [A release](README.md#a-release)) only prove an upload came from its owner.
- [ ] **Verified links.** Copy the SHA-256 of the app signing key from Setup,
  App signing, into
  [`web/public/.well-known/assetlinks.json`](../web/public/.well-known/assetlinks.json)
  as [Not here yet](README.md#not-here-yet) shows, merge, and let the site
  deploy before the first install anyone keeps: a device that checked before the
  file was right keeps its answer until the app is installed again.
- [ ] **The bundles.** `./gradlew :app:bundleRelease :wear:bundleRelease
  :automotive:bundleRelease` with the four upload values set. All three share
  the application ID, so each upload needs its own version code; the ranges are
  already apart, the phone from 1, the watch from 1001 and the car from 2001,
  and each is raised with every upload of that bundle.
- [ ] **The place-name packs** are live as the `place-packs` release. They are
  published by the Landmarks workflow, run by hand.
- [ ] **Store listing, graphics and screenshots**, as [below](#store-listing).
  The icon, the feature graphic and the screenshots, phone, tablet and watch,
  are made.
- [ ] **App content**: the privacy policy, ads, app access, content rating,
  target audience and Data safety, as [below](#app-content).
- [ ] **Form factors.** The watch and the car are opted in separately, under
  Advanced settings, Form factors: Wear OS, Android Auto and Android Automotive
  OS each have their own review against Google's quality guidelines for that
  surface, and their own screenshots. Android Auto lists the app as a point of
  interest app, which is the category it declares.
- [ ] **Internal testing first.** The pre-launch report runs the release build
  on real devices; read it before the closed test.

## Store listing

**App name** (30 characters at most):

> Grid Point Code

**Short description** (80 at most, this is 78):

> Ten characters for any place on Earth: find, say, share and keep them offline.

**Full description** (4,000 at most):

> Grid Point Code writes any place on Earth as ten characters, #G3RJM-98NM9, a
> cell about 2.5 metres across. A code needs no address and no account, and the
> app works it out on the phone with no connection.
>
> Find where you are. Press the button and the app keeps listening while you
> hold still, until the fix is inside one cell, and says how far to trust it.
>
> Say it. Read the code aloud with its check character, in the spelling words of
> the person listening: English, German, Spanish, French, Italian, Dutch or
> Swedish. When a typed code lands far from you, the codes one slip away are
> offered, in case one character was heard wrong.
>
> Share it. Copy it, share a link that carries the directions to the door, or
> show a QR code to scan. Share an area as well as a point: a district, a block,
> a building.
>
> Anchor it. Give the short form with a landmark, "98NM9 near Old Toronto,
> Ontario, Canada", and the app reads such a line back.
>
> Find places by name among 7.3 million, and keep a country's names on the phone
> to search them with no connection. Keep an area's landmarks for a journey.
>
> Read what you were sent. Coordinates, degrees and minutes, a geo link, a
> location written in several other systems, or a map link that says where it
> points: each opens as a code.
>
> Save places with a name and directions, nearest first with an arrow to each,
> and put the stops of a round in walking order.
>
> Emergency card. Hold the app's icon: where you are in large type, the latitude
> and longitude first, then the code and the landmark it is near, read aloud on
> request, with no connection needed.
>
> On a Wear OS watch: the code where you are, read aloud, your saved places with
> an arrow that turns with the watch, a tile and a complication.
>
> In the car: on Android Auto, where the car is, your saved places on the car's
> own map, and Navigate to hand a place to your navigation app. Cars with
> Android Automotive have their own app, with no phone and so no saved places.
>
> No account, no ads, no analytics. Your location is read only when you ask for
> it or while the app is on a car's screen, and the app sends it nowhere: the
> code is worked out on the device. The map is OpenFreeMap, drawn from
> OpenStreetMap data.
>
> The format is open. Its specification, and libraries for Java, C#, Python and
> TypeScript, are at gridpointcode.com.

**Category**: Maps & Navigation. **Website**: https://gridpointcode.com.
**Privacy policy**: https://gridpointcode.com/privacy. **Contact email**: the
address the site is run from.

### Graphics

| Asset | Play requires | Here |
| --- | --- | --- |
| App icon | 512 by 512, 32-bit PNG, at most 1,024 KB | [`web/public/icon-512.png`](../web/public/icon-512.png): a full, opaque square from `design/build-icons.mjs` |
| Feature graphic | 1024 by 500, JPEG or 24-bit PNG with no alpha | [`play/feature-graphic.png`](play/feature-graphic.png): the same four bars on the same ground, from the same script, with no words |
| Phone screenshots | 2 to 8; the long side at most twice the short; four at 9:16 and 1080 or more to be eligible for promotion | six in [`play/screenshots/phone`](play/screenshots/phone), 1080 by 1920: a place on the map, search by name, the short form anchored to a landmark, saved places, the emergency card and the QR code |
| Tablet screenshots | 4 or more to be shown for tablets, 1080 to 7680 pixels, 16:9 or 9:16 | four in [`play/screenshots/tablet`](play/screenshots/tablet), 1920 by 1080, the wide layout: a place, search by name beside the map, the short form anchored, and the dark theme over its night basemap |
| Wear OS screenshots | at least 384 by 384, square | four in [`play/screenshots/wear`](play/screenshots/wear), 454 by 454: the code where the watch is, saved places, walking to one, and the tile |

All are 24-bit PNG with no alpha, as Play asks. A phone that draws at 1080 by
2400, as many do, is more than twice as long as it is wide, and its screenshots
are refused, so these were taken with the screen set to 9:16, and the tablet's
with a 16:9 screen at a tablet's density:

```
adb shell wm size 1080x1920
adb shell wm size 1920x1080
adb shell wm density 240
```

with `adb shell wm size reset` and `adb shell wm density reset` afterwards. The
status bar is Android's demo mode, so the clock reads 9:30 and nothing waits in
the notification shade. The places are real ones in Toronto, saved through the
app's own Save, and the fix the emulator was given is Union Station's.

## App content

**Privacy policy**: https://gridpointcode.com/privacy.

**Ads**: the app contains no ads.

**App access**: every feature is available without an account or any special
access.

**Content rating** (the IARC questionnaire): category Utility, Productivity,
Communication or Other. No violence, sexual content, profanity, controlled
substances or gambling. Users do not interact or exchange content inside the
app; a code leaves it only through Share or Copy, to an app the reader chooses.
The app does not share the reader's location with other users, offers no
purchases and is not a web browser, so it should come out at the lowest age
rating.

**Target audience**: 13 and over. The app is not designed for children and its
listing does not appeal to them; choosing younger ages brings in the Families
policy, which nothing here needs.

**News, government, financial and health apps**: none of these.

## Data safety

Play's definitions, from its guidance: data is **collected** when the app, or a
library inside it, sends it off the device. It is **shared** when it goes to a
third party, except to a service provider acting for the app, or when the reader
starts the transfer and expects it. Data **processed ephemerally**, held in
memory only for the request, is still declared, and Play leaves it off the
listing. A location inside an area under 3 square kilometres is **precise**;
one over it is **approximate**.

**Does the app collect or share any of the required user data types?** Yes.

| Data type | Collected | Shared | Ephemeral | Required | Purpose |
| --- | --- | --- | --- | --- | --- |
| Location: precise location | Yes | No | No | Yes | App functionality |
| App activity: in-app search history | Yes | No | No | No | App functionality |
| App activity: other user-generated content | Yes | No | No | No | App functionality |

Why each, and why nothing else:

- **Precise location.** The map asks OpenFreeMap for the tiles of the area on
  the screen, and the site's host for the landmark file around a place, so both
  learn the area the reader is looking at, which is often where they are. A map
  tile at the most detailed level is about 2.45 km wide at the equator and
  narrower towards the poles; north of 45 degrees one tile is under 3 square
  kilometres, so this is precise by Play's measure, not approximate. The saved
  places that go to a watch are points too. None of it is the device's own fix,
  which never leaves the phone, but the area on the screen is enough to count.
  Not shared: the tile host, the site's host and Google Play services each carry
  the data for the app, as a service provider does, which Play does not count as
  sharing. Not ephemeral: those hosts
  keep ordinary server logs under their own policies, and this project cannot
  vouch for less. Required: there is no switch to turn the map off.
- **In-app search history.** A search by name reads one block of the site's name
  index, which tells the host roughly what the reader typed. Optional: it
  happens only when the reader searches by name.
- **Other user-generated content.** Saved places, with their names and
  directions, go through Google Play services to a watch that has the app, by
  way of Google's servers when the two are apart. Optional: only with such a
  watch.
- **Not declared, and why.** The device's location: worked out into a code on
  the phone and sent nowhere. IP addresses: every host receives one, and Play
  counts an IP address only where it is used to find the reader, which nothing
  here does. A country's place names kept from GitHub: a download the reader
  asks for, of a country they choose, which is not where they are. Android
  Auto, the reader's speech engine, Share, Copy and Navigate: each is a
  transfer the reader starts, to something they chose, and expects. Android's
  own device backup, which the reader turns on, is the system's, and it is
  end-to-end encrypted when the phone has a screen lock.

**Is all of the user data collected by the app encrypted in transit?** Yes. The
tile host, the site, GitHub's downloads and Google Play services are reached
over HTTPS or Play services' own encrypted transport. Only a debug build may use
plain HTTP, and only to the emulator's own host.

**Do you provide a way for users to request that their data be deleted?** No.
There is no account, and this project keeps nothing; what is on the phone or
the watch goes when the app is uninstalled there.

The watch app and the Android Automotive app have no internet access of their
own, which CI checks in each release's merged manifest; they collect nothing
beyond what is above.
