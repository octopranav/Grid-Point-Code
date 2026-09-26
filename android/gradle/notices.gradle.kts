import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

// Applied by each app, the phone and the watch, to its own release and its own
// list, src/main/assets/licences/components.txt.

// The notices name every library the release ships, because several of their
// licences require their notice to travel with the app. This holds the list to
// what the release really resolves, so a dependency added, dropped or upgraded
// past the notice copied from it cannot go out unmentioned.
// Looked up when the task runs: the plugin creates the release's
// configurations after this script has been read.
val shipped = provider { configurations.getByName("releaseRuntimeClasspath") }
    .flatMap { it.incoming.resolutionResult.rootComponent }
val components = layout.projectDirectory.file("src/main/assets/licences/components.txt")

tasks.register("checkNotices") {
    group = "verification"
    description = "Fails when the open-source notices and the release's libraries disagree."
    inputs.file(components)
    doLast {
        val seen = mutableSetOf<ResolvedComponentResult>()
        fun walk(component: ResolvedComponentResult) {
            if (!seen.add(component)) return
            component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { walk(it.selected) }
        }
        walk(shipped.get())
        val versions = seen.mapNotNull { it.id as? ModuleComponentIdentifier }
            .filterNot { it.module.endsWith("-bom") }
            .associate { "${it.group}:${it.module}" to it.version }

        val listed = components.asFile.readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.split(Regex("\\s+")) }
        val named = listed.associate { it[0] to it.getOrNull(2)?.substringAfter('@', "")?.ifEmpty { null } }

        val problems = buildList {
            (versions.keys - named.keys).sorted().forEach { add("$it is shipped but not in the notices") }
            (named.keys - versions.keys).sorted().forEach { add("$it is in the notices but no longer shipped") }
            named.forEach { (module, copied) ->
                val resolved = versions[module]
                if (copied != null && resolved != null && copied != resolved) {
                    add("$module is $resolved, but its notice was copied from $copied")
                }
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException(problems.joinToString("\n", postfix = "\nUpdate ${project.name}/src/main/assets/licences."))
        }
        logger.lifecycle("${versions.size} libraries shipped, every one in the notices")
    }
}
