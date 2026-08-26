# Workflows

| File | Trigger | What it does |
| --- | --- | --- |
| `ci.yml` | every push, every pull request | Builds and tests all four ports, checks that the shared vectors still regenerate byte for byte, and checks every claim the specification makes |
| `release-python.yml` | a `v*` tag | Builds and publishes the Python package to PyPI |
| `release-npm.yml` | a `v*` tag | Builds and publishes the TypeScript package to npm |

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

The `reference` job runs `reference/verify.py`, which checks every exact claim
`SPEC.md` makes and holds the transcription of its Appendix A against the
working implementation over 200,000 coordinates. Nothing it touches ships. It
is there because the ports are meant to be implementable from the document
alone, and this is the job that fails when they stop being so.

## Actions are pinned to commits

Every `uses:` names a commit, with the version it corresponded to in a trailing
comment. A tag can be moved to point at different code; a commit cannot. When
updating one, read what changed between the two commits rather than trusting
the tag that moved.

## Publishing without secrets

Neither release workflow holds an API token, and neither needs one in the
repository secrets. Both registries are configured to trust this repository and
a named workflow file, and issue a short-lived credential for a single upload.
A leaked publishing secret is not a risk that has to be managed if there is no
secret to leak.

Each release job also declares a deployment environment, `pypi` and `npm`, so
the upload can carry its own protection rules and appears in the repository's
deployment history.

Setting this up is done once, outside the repository:

* **PyPI** — add a trusted publisher to the project: owner `octopranav`,
  repository `Grid-Point-Code`, workflow `release-python.yml`, environment
  `pypi`.
* **npm** — add a trusted publisher to the package: the same repository, with
  workflow `release-npm.yml` and environment `npm`.

Both names must match exactly, including the environment, or the exchange is
refused and the release fails without publishing anything.

## Provenance

The Python artifacts are attested with `actions/attest-build-provenance`, which
records against this repository which workflow run produced which file. The
npm publish carries provenance as part of the same credential exchange that
authorises it.

## NuGet and Maven Central are still published by hand

Both are deliberately left out for now. NuGet has no trusted-publishing
equivalent, so automating it would mean putting a long-lived API key into
repository secrets, which is the thing the two workflows above exist to avoid.
Maven Central additionally needs the signing key inside the runner.

CI still builds and tests both ports on every push, so a release is never cut
from code that has not been through the same conformance run as the other two.
Only the upload itself is manual.

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
3. PyPI and npm publish themselves. Watch both runs to the end.
4. Publish NuGet and Maven Central by hand from that same commit.

The failure worth avoiding is a version that reaches two registries and not the
other two, so do not push the tag until whatever step 4 needs is to hand.
