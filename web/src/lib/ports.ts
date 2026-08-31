// The four ports, and where each one is published.
//
// The name and the blurb are the site's own words. **The package identity is
// not** — it is read from each port's own manifest at build time, because
// writing it here is how a page ends up naming a package that no longer exists.
// Two of the four were wrong the first time they were typed out by hand.

// The manifests are imported, not read from disk at run time.
//
// The first version resolved them from `import.meta.url`, which works in dev
// and fails in a build: the module is bundled into `dist/.prerender/chunks/`,
// so the path walked up from the wrong place and the build died looking for
// `web/typescript/package.json`. Vite resolves these against this file's real
// location at build time, in both modes, and there is no path left to get
// wrong.
import typescriptManifest from '../../../typescript/package.json';
import pythonManifest from '../../../python/pyproject.toml?raw';
import javaManifest from '../../../java/pom.xml?raw';
import csharpManifest from '../../../csharp/gpc/gpc.csproj?raw';

/** The first capture of `pattern`, or a stated failure. Never a quiet guess. */
function only(source: string, pattern: RegExp, what: string): string {
    const found = pattern.exec(source);
    if (!found) throw new Error(`could not read ${what} from its manifest`);
    return found[1].trim();
}

export interface Port {
    /** The directory, which is also the content collection's id prefix. */
    id: string;
    /** What to call it in a heading, rather than what its folder is called. */
    name: string;
    /** The registry a reader would fetch it from. */
    registry: string;
    /** The package identifier there, taken from the port's own manifest. */
    packageName: string;
    /** The published version, likewise. */
    version: string;
    /** The one line saying which reader this is for. */
    blurb: string;
}

function typescript(): Pick<Port, 'packageName' | 'version'> {
    return { packageName: typescriptManifest.name, version: typescriptManifest.version };
}

function python(): Pick<Port, 'packageName' | 'version'> {
    return {
        packageName: only(pythonManifest, /^name\s*=\s*"([^"]+)"/m, "Python's package name"),
        version: only(pythonManifest, /^version\s*=\s*"([^"]+)"/m, "Python's version"),
    };
}

function java(): Pick<Port, 'packageName' | 'version'> {
    // No parent block in this pom, so the first of each is the project's own.
    const group = only(javaManifest, /<groupId>([^<]+)<\/groupId>/, "Java's group");
    const artifact = only(javaManifest, /<artifactId>([^<]+)<\/artifactId>/, "Java's artifact");
    return {
        packageName: `${group}:${artifact}`,
        version: only(javaManifest, /<version>([^<]+)<\/version>/, "Java's version"),
    };
}

function csharp(): Pick<Port, 'packageName' | 'version'> {
    return {
        packageName: only(csharpManifest, /<PackageId>([^<]+)<\/PackageId>/, "C#'s package id"),
        version: only(csharpManifest, /<PackageVersion>([^<]+)<\/PackageVersion>/, "C#'s version"),
    };
}

/**
 * Ordered as a reader is most likely to want them, not alphabetically:
 * TypeScript runs this site, and Python is what the conformance vectors are
 * generated from.
 */
export const PORTS: Port[] = [
    {
        id: 'typescript',
        name: 'TypeScript',
        registry: 'npm',
        blurb: 'Runs in a browser and on a server, with no dependencies. This site is built on it.',
        ...typescript(),
    },
    {
        id: 'python',
        name: 'Python',
        registry: 'PyPI',
        blurb: 'Standard library only, and the reference the conformance vectors are generated from.',
        ...python(),
    },
    {
        id: 'java',
        name: 'Java',
        registry: 'Maven Central',
        blurb: 'One class, no dependencies.',
        ...java(),
    },
    {
        id: 'csharp',
        name: 'C#',
        registry: 'NuGet',
        blurb: 'The same arithmetic and the same vectors, on .NET.',
        ...csharp(),
    },
];

export const port = (id: string) => PORTS.find((candidate) => candidate.id === id);
