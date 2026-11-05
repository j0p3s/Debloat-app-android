package io.github.j0p3s.debloater.leftovers

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.util.UUID

/** Runs inside the Shizuku process. No arbitrary paths, shell expansion or guessed app-name folders. */
class LeftoverStore(private val storage: File, private val knownPackages: () -> Set<String>) {
    private val root = storage.absoluteFile.normalize()
    private val recovery = File(root, ".DebloaterRecovery")
    private val areas = setOf("data", "obb", "media")
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
    private val tokenPattern = Regex("[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}")
    private var deadline = 0L
    private var visited = 0

    private fun begin() { deadline = System.nanoTime() + 15_000_000_000; visited = 0; requireSafe(root) }
    private fun tick() { check(++visited <= 100_000 && System.nanoTime() < deadline) { "File operation limit reached; rescan required" } }
    private fun exists(file: File) = Files.exists(file.toPath(), NOFOLLOW_LINKS)
    private fun requireSafe(file: File) {
        val absolute = file.absoluteFile.normalize()
        require(absolute == root || absolute.toPath().startsWith(root.toPath())) { "Path outside primary storage" }
        var part: File? = absolute
        while (part != null && part.toPath().startsWith(root.toPath())) {
            require(!Files.isSymbolicLink(part.toPath())) { "Symbolic links are not supported" }
            part = part.parentFile
        }
        require(file.canonicalFile == absolute) { "Non-canonical path" }
    }

    private fun source(key: String): File {
        val parts = key.split('/')
        require(parts.size == 2 && parts[0] in areas && packagePattern.matches(parts[1])) { "Invalid app folder" }
        return File(root, "Android/$key").also(::requireSafe)
    }

    private fun list(directory: File): Array<File> {
        tick(); requireSafe(directory)
        check(directory.isDirectory) { "Directory unavailable: ${directory.name}" }
        return (directory.listFiles() ?: error("Access denied: ${directory.name}"))
            .also { check(it.size <= 10_000) { "Directory too large" } }
    }

    private fun bytes(file: File, depth: Int = 0): Long {
        tick(); requireSafe(file)
        check(depth < 128) { "Directory nesting limit reached" }
        check(Files.isDirectory(file.toPath(), NOFOLLOW_LINKS) || Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)) { "Unsupported file type" }
        if (file.isFile) return file.length()
        return list(file).fold(0L) { total, child -> Math.addExact(total, bytes(child, depth + 1)) }
    }

    fun scan(): JSONObject {
        begin()
        val owners = knownPackages() // Failure must abort, never mean "no installed apps".
        check(owners.isNotEmpty()) { "Package inventory unavailable" }
        val candidates = JSONArray()
        val warnings = JSONArray()
        for (area in areas) {
            try {
                val entries = list(File(root, "Android/$area"))
                for (entry in entries.sortedBy { it.name }) {
                    tick()
                    if (!packagePattern.matches(entry.name) || entry.name in owners) continue
                    try {
                        val key = "$area/${entry.name}"
                        val folder = source(key)
                        if (!folder.isDirectory) continue
                        val size = bytes(folder)
                        if (candidates.length() >= 200) { warnings.put("Result limit reached; clean reviewed items and rescan"); break }
                        candidates.put(JSONObject().put("key", key).put("package", entry.name).put("bytes", size)
                            .put("path", folder.path).put("modified", folder.lastModified()))
                    } catch (error: Exception) { warnings.put("$area/${entry.name}: ${error.message}") }
                    if (System.nanoTime() >= deadline) break
                }
            } catch (error: Exception) { warnings.put("Android/$area: ${error.message}") }
        }
        return JSONObject().put("candidates", candidates).put("warnings", warnings)
    }

    fun quarantine(key: String): String {
        begin()
        val folder = source(key)
        check(folder.isDirectory) { "App folder is no longer present" }
        val size = bytes(folder) // Reject unsafe trees before moving them.
        val owners = knownPackages()
        check(owners.isNotEmpty() && folder.name !in owners) { "Package is installed or retained; rescan required" }
        tick()
        requireSafe(recovery)
        check(recovery.isDirectory || recovery.mkdirs()) { "Recovery folder unavailable" }
        val id = UUID.randomUUID().toString()
        val entry = File(recovery, id).also(::requireSafe)
        check(entry.mkdir()) { "Could not create recovery record" }
        val manifest = JSONObject().put("id", id).put("key", key).put("bytes", size).put("created", System.currentTimeMillis())
        // Persist the original location before the atomic same-volume move; never delete on move failure.
        FileOutputStream(File(entry, "record.json")).use { output -> output.write(manifest.toString().toByteArray(Charsets.UTF_8)); output.fd.sync() }
        requireSafe(folder)
        Files.move(folder.toPath(), File(entry, "payload").toPath())
        return id
    }

    private fun record(id: String): Pair<File, JSONObject> {
        require(tokenPattern.matches(id)) { "Invalid recovery ID" }
        val entry = File(recovery, id).also(::requireSafe)
        val manifest = File(entry, "record.json").also(::requireSafe)
        check(manifest.isFile && manifest.length() in 1..8192) { "Recovery record unavailable" }
        val record = JSONObject(manifest.readText())
        require(record.getString("id") == id) { "Recovery record mismatch" }
        source(record.getString("key")) // Validate untrusted metadata too.
        return entry to record
    }

    fun recoveryList(): JSONArray {
        begin()
        val result = JSONArray()
        if (!exists(recovery)) return result
        for (entry in list(recovery).sortedByDescending { it.lastModified() }) {
            tick()
            if (result.length() >= 200) break
            if (!tokenPattern.matches(entry.name)) continue
            runCatching {
                val (directory, record) = record(entry.name)
                val payload = File(directory, "payload").also(::requireSafe)
                if (exists(payload)) result.put(record.put("path", source(record.getString("key")).path))
            }
        }
        return result
    }

    fun restore(id: String) {
        begin()
        val (entry, record) = record(id)
        val payload = File(entry, "payload").also(::requireSafe)
        val destination = source(record.getString("key"))
        check(!exists(destination)) { "Original folder already exists; restore never overwrites or merges files" }
        check(destination.parentFile?.isDirectory == true) { "Original storage area is unavailable" }
        bytes(payload)
        Files.move(payload.toPath(), destination.toPath()) // No REPLACE_EXISTING.
        check(File(entry, "record.json").delete()) { "Files restored; could not remove recovery metadata" }
        entry.delete()
    }

    fun purge(id: String) {
        begin()
        val (entry, _) = record(id)
        val payload = File(entry, "payload").also(::requireSafe)
        bytes(payload) // Full no-symlink preflight before any permanent deletion.
        fun deleteTree(file: File, depth: Int = 0) {
            tick(); requireSafe(file)
            check(depth < 128) { "Directory nesting limit reached" }
            if (file.isDirectory) list(file).forEach { deleteTree(it, depth + 1) }
            check(file.delete()) { "Could not delete recovery file; remaining files kept" }
        }
        deleteTree(payload)
        check(File(entry, "record.json").delete()) { "Recovery metadata could not be removed" }
        entry.delete()
    }
}
