# Workflows

| File | Trigger | What it does |
| --- | --- | --- |
| `ci.yml` | every push, every pull request | Builds and tests all four ports, checks that the shared vectors and the advisory list still regenerate byte for byte, runs the four ports against each other case by case, and checks every claim the specification makes |
| `release-python.yml` | a `v*` tag | Builds and publishes the Python package to PyPI |
| `release-npm.yml` | a `v*` tag | Builds and publishes the TypeScript package to npm |
| `release-nuget.yml` | a `v*` tag | Builds and publishes the C# package to NuGet |

Nothing publishes on a push to a branch. A release happens because a version
tag was pushed, and for no other reason.

## What CI proves that a single port's tests cannot

Each port's suite reads the vector files in `test_data/` and reproduces the
digest of the generated sample pinned in `test_data/v2_sample.csv`. Four green
ports therefore mean four byte-identical encoders. Before this existed, nothing
in the repository checked that four independent implementations of the same
format agreed, and they did not.

The `vectors` job closes the other half of that loop by regenerating the corpus
and failing on any diff, so the committed expectations are always exactly what
the reference generator produces rather than something edited by hand.

The `screening` job does the same for the advisory list: it re-expands
`screening/words.zip` and fails if any of the four generated files moves, which
catches one edited by hand and an archive changed without rebuilding them.

The `reference` job runs `reference/verify.py`, which checks every exact claim
`SPEC.md` makes and holds the transcription of its Appendix A against the
working implementation over 200,000 coordinates. Nothing it touches ships. It
is there because the ports are meant to be implementable from the document
alone, and this is the job that fails when they stop being so.

The `differential` job runs [`conformance/`](../../conformance/), which puts one
battery of awkward inputs through all four ports and diffs the answers against
each other. It catches what a vector cannot: a vector records an answer somebody
already worked out, so it only pins cases the ports were already known to agree
on. This is the only job needing all four toolchains at once.

## Actions are pinned to commits

Every `uses:` names a commit, with the version it corresponded to in a trailing
comment. A tag can be moved to point at different code; a commit cannot. When
updating one, read what changed between the two commits rather than trusting
the tag that moved.

## Publishing without secrets

No release workflow holds an API token, and none needs one in the repository
secrets. All three registries are configured to trust this repository and a
named workflow file, and issue a short-lived credential for a single upload. A
leaked publishing secret is not a risk that has to be managed if there is no
secret to leak.

Each release job also declares the `release` deployment environment, so the
upload can carry protection rules and appears in the repository's deployment
history. All three name the same environment: they are parts of one release and
never move apart, so there is nothing separate names would let you say.

Setting this up is done once, outside the repository:

* **PyPI** — add a trusted publisher to the project: owner `octopranav`,
  repository `Grid-Point-Code`, workflow `release-python.yml`, environment
  `release`.
* **npm** — add a trusted publisher to the package: the same repository, with
  workflow `release-npm.yml` and environment `release`.
* **NuGet** — add a trusted publishing policy under the account: package owner
  `pranavpatel.ca`, provider GitHub Actions, repository owner `octopranav`,
  repository `Grid-Point-Code`, workflow file `release-nuget.yml` (the file name
  only, without the `.github/workflows/` path) and environment `release`.

Both names must match exactly, including the environment, or the exchange is
refused and the release fails without publishing anything.

## Provenance

The Python artifacts are attested with `actions/attest-build-provenance`, which
records against this repository which workflow run produced which file. The
npm publish carries provenance as part of the same credential exchange that
authorises it.

The NuGet package is not attested, because nuget.org does not surface
attestations and one nobody can read is not provenance. It carries its origin
another way: the nuspec records the repository URL and the exact commit the
package was built from, which `dotnet pack` writes in without being asked.

## Maven Central is the one still published by hand

It needs the GPG signing key inside the runner, which is a long-lived secret of
exactly the kind the other three workflows exist to avoid holding. So the jar,
the sources, the javadoc and their signatures are built locally and uploaded
through the Portal.

Two things about that upload are easy to get wrong, and both cost a round trip:

* **The Portal takes a bundle, not files.** A ZIP in Maven repository layout —
  `ca/pranavpatel/algo/gridpointcode/<version>/` holding the four artifacts,
  their `.asc` signatures, and `.md5` and `.sha1` for each of those eight.
  Loose files out of `java/target` are refused. `mvn deploy -DskipPublishing=true`
  does not build the bundle either; it skips creating one along with sending it.
* **Nothing is public until it is published twice.** The upload validates and
  then stops, because `autoPublish` is not set on the plugin and defaults to
  false. The deployment sits in the Portal until Publish is clicked, which is
  the last chance to look at a version that can never be replaced.

`mvn deploy` is the other route, and needs a Portal user token in
`~/.m2/settings.xml` under the server id `central`, matching
`<publishingServerId>` in `java/pom.xml`. The token is not the account password.

CI still builds and tests this port on every push, so a release is never cut
from code that has not been through the same conformance run as the other
three. Only the upload itself is manual.

## A note on the NuGet policy

`NuGet/login`'s `user` is the nuget.org **profile name**, not an email address,
and it is in `release-nuget.yml` in plain sight rather than in a secret: it is
already printed on the package's public page, and a secret holding a public name
is one more thing to rotate for nothing.

A newly created policy can start out *temporarily active* for seven days and
lapse if nothing publishes in that window — it can be restarted, but it is worth
registering close to when a release is actually due rather than long before.

## Cutting a release

All four packages carry the same version number, so the manifests move together
and the tag is what starts everything.

| Port | The version lives in |
| --- | --- |
| Python | `python/pyproject.toml` |
| TypeScript | `typescript/package.json` and `typescript/package-lock.json` |
| C# | `csharp/gpc/gpc.csproj` |
| Java | `java/pom.xml` |

1. On a branch, set all five files to the new version, write the release into
   [`CHANGELOG.md`](../../CHANGELOG.md), and merge once CI is green.
2. Tag the merge commit `vX.Y.Z` and push the tag. Each workflow checks the tag
   against its own manifest and fails before uploading anything if the two
   disagree, so a mistyped tag costs a failed run rather than a wrong release.
3. PyPI, npm and NuGet publish themselves. Watch all three runs to the end.
4. Publish Maven Central by hand from that same commit.

The failure worth avoiding is a version that reaches three registries and not
the fourth, so do not push the tag until the signing key step 4 needs is to
hand.
