package org.organicprogramming.holons

import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class HolonBuild(
    val runner: String = "",
    val main: String = "",
)

data class HolonArtifacts(
    val binary: String = "",
    val primary: String = "",
)

data class HolonManifest(
    val kind: String = "",
    val build: HolonBuild = HolonBuild(),
    val artifacts: HolonArtifacts = HolonArtifacts(),
)

data class HolonEntry(
    val slug: String,
    val uuid: String,
    val dir: Path,
    val relativePath: String,
    val origin: String,
    val identity: HolonIdentity,
    val manifest: HolonManifest?,
)

object Discover {
    fun discover(root: Path): List<HolonEntry> = discoverInRoot(root, "local")

    fun discoverLocal(): List<HolonEntry> = discover(Paths.get(currentDir()))

    fun discoverAll(): List<HolonEntry> {
        val entries = mutableListOf<HolonEntry>()
        val seen = mutableSetOf<String>()
        listOf(
            Paths.get(currentDir()) to "local",
            opbin() to "\$OPBIN",
            cacheDir() to "cache",
        ).forEach { (root, origin) ->
            discoverInRoot(root, origin).forEach { entry ->
                val key = entry.uuid.trim().ifEmpty { entry.dir.toString() }
                if (seen.add(key)) {
                    entries += entry
                }
            }
        }
        return entries
    }

    fun findBySlug(slug: String): HolonEntry? {
        val needle = slug.trim()
        if (needle.isEmpty()) return null

        var match: HolonEntry? = null
        discoverAll().forEach { entry ->
            if (entry.slug != needle) return@forEach
            if (match != null && match!!.uuid != entry.uuid) {
                error("ambiguous holon \"$needle\"")
            }
            match = entry
        }
        return match
    }

    fun findByUUID(prefix: String): HolonEntry? {
        val needle = prefix.trim()
        if (needle.isEmpty()) return null

        var match: HolonEntry? = null
        discoverAll().forEach { entry ->
            if (!entry.uuid.startsWith(needle)) return@forEach
            if (match != null && match!!.uuid != entry.uuid) {
                error("ambiguous UUID prefix \"$needle\"")
            }
            match = entry
        }
        return match
    }

    private fun discoverInRoot(root: Path, origin: String): List<HolonEntry> {
        val resolvedRoot = (if (root.toString().isBlank()) Paths.get(currentDir()) else root)
            .toAbsolutePath()
            .normalize()
        if (!Files.isDirectory(resolvedRoot)) return emptyList()

        val entriesByKey = linkedMapOf<String, HolonEntry>()
        scanDir(resolvedRoot, resolvedRoot, origin, entriesByKey)
        return entriesByKey.values.sortedWith(compareBy<HolonEntry> { it.relativePath }.thenBy { it.uuid })
    }

    private fun scanDir(
        root: Path,
        dir: Path,
        origin: String,
        entriesByKey: LinkedHashMap<String, HolonEntry>,
    ) {
        val children = try {
            Files.list(dir).use { stream -> stream.toList() }
        } catch (_: Exception) {
            return
        }

        children.forEach { child ->
            val name = child.fileName?.toString() ?: ""
            if (Files.isDirectory(child)) {
                if (!shouldSkipDirectory(root, child, name)) {
                    scanDir(root, child, origin, entriesByKey)
                }
                return@forEach
            }
            if (!Files.isRegularFile(child) || name != "holon.yaml") return@forEach

            try {
                val identity = Identity.parseHolon(child.toString())
                val manifest = parseManifest(child)
                val holonDir = child.parent.toAbsolutePath().normalize()
                val entry = HolonEntry(
                    slug = slugFor(identity),
                    uuid = identity.uuid,
                    dir = holonDir,
                    relativePath = relativePath(root, holonDir),
                    origin = origin,
                    identity = identity,
                    manifest = manifest,
                )
                val key = entry.uuid.trim().ifEmpty { entry.dir.toString() }
                val existing = entriesByKey[key]
                if (existing != null) {
                    if (pathDepth(entry.relativePath) < pathDepth(existing.relativePath)) {
                        entriesByKey[key] = entry
                    }
                } else {
                    entriesByKey[key] = entry
                }
            } catch (_: Exception) {
                // Skip invalid holon manifests.
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseManifest(path: Path): HolonManifest {
        val loaded = Yaml().load<Any?>(Files.readString(path))
        require(loaded is Map<*, *>) { "$path: holon.yaml must be a YAML mapping" }
        val data = loaded as Map<String, Any?>
        val build = data["build"] as? Map<String, Any?> ?: emptyMap()
        val artifacts = data["artifacts"] as? Map<String, Any?> ?: emptyMap()

        return HolonManifest(
            kind = data["kind"]?.toString() ?: "",
            build = HolonBuild(
                runner = build["runner"]?.toString() ?: "",
                main = build["main"]?.toString() ?: "",
            ),
            artifacts = HolonArtifacts(
                binary = artifacts["binary"]?.toString() ?: "",
                primary = artifacts["primary"]?.toString() ?: "",
            ),
        )
    }

    private fun slugFor(identity: HolonIdentity): String {
        val given = identity.givenName.trim()
        val family = identity.familyName.trim().removeSuffix("?")
        if (given.isEmpty() && family.isEmpty()) return ""
        return "$given-$family".trim().lowercase().replace(" ", "-").trim('-')
    }

    private fun shouldSkipDirectory(root: Path, dir: Path, name: String): Boolean {
        if (root == dir) return false
        return name in setOf(".git", ".op", "node_modules", "vendor", "build") || name.startsWith(".")
    }

    private fun relativePath(root: Path, dir: Path): String {
        val rel = root.relativize(dir).toString().replace('\\', '/')
        return if (rel.isEmpty()) "." else rel
    }

    private fun pathDepth(relativePath: String): Int {
        val trimmed = relativePath.trim().trim('/')
        if (trimmed.isEmpty() || trimmed == ".") return 0
        return trimmed.split('/').size
    }

    private fun currentDir(): String = System.getProperty("user.dir", ".").trim()

    private fun opPath(): Path {
        val configured = getenvOrProperty("OPPATH")
        if (configured.isNotBlank()) return Paths.get(configured).toAbsolutePath().normalize()
        return Paths.get(System.getProperty("user.home", "."), ".op").toAbsolutePath().normalize()
    }

    private fun opbin(): Path {
        val configured = getenvOrProperty("OPBIN")
        if (configured.isNotBlank()) return Paths.get(configured).toAbsolutePath().normalize()
        return opPath().resolve("bin")
    }

    private fun cacheDir(): Path = opPath().resolve("cache")

    private fun getenvOrProperty(name: String): String {
        val env = System.getenv(name)
        if (!env.isNullOrBlank()) return env.trim()
        return System.getProperty(name)?.trim() ?: ""
    }
}
