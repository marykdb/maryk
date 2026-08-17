package maryk.file

import kotlin.random.Random

/** One file in a managed export revision. [relativePath] is relative to that revision. */
data class ManagedExportFile(
    val relativePath: String,
    val contents: ByteArray,
)

/** Location of a complete managed export revision. */
data class ManagedExportRevision(
    val id: String,
    val directory: String,
    val manifestPath: String,
)

/**
 * Publishes a complete export set behind an atomic pointer.
 *
 * Files and their SHA-256 manifest are written below
 * `<output>/.maryk-export/revisions/<id>` before `current` is replaced. Readers
 * must resolve `current` before reading a revision. A write failure therefore
 * leaves the prior current revision selected.
 */
fun publishManagedRevision(
    outputDirectory: String,
    files: Iterable<ManagedExportFile>,
    revisionId: String = Random.nextLong().toString(16),
): ManagedExportRevision {
    val checkedFiles = files.toList()
    validateExportPaths(checkedFiles.map { it.relativePath })
    return publishManagedRevision(outputDirectory, revisionId) {
        checkedFiles.forEach { file -> writeBytes(file.relativePath, file.contents) }
    }
}

/**
 * Stages a streaming export before it becomes visible through the `current` pointer.
 *
 * The [write] callback may write or append each declared relative path. Its work is
 * abandoned if it throws; the current pointer is replaced only after every declared
 * output is hashed and the staging directory is moved to its final revision path.
 */
fun publishManagedRevision(
    outputDirectory: String,
    revisionId: String = Random.nextLong().toString(16),
    write: ManagedExportStaging.() -> Unit,
): ManagedExportRevision {
    require(revisionId.isSafeRevisionId()) { "Invalid export revision id: $revisionId" }
    val root = outputDirectory.trimEnd('/', '\\') + "/.maryk-export"
    val revisionDirectory = "$root/revisions/$revisionId"
    val manifestPath = "$revisionDirectory/manifest.sha256"
    require(File.size(manifestPath) == null) { "Managed export revision already exists: $revisionId" }
    val stagingDirectory = "$root/revisions/.staging-$revisionId-${Random.nextLong().toString(16)}"
    val staging = ManagedExportStaging(stagingDirectory)
    try {
        staging.write()
        return publishStagedRevision(root, revisionDirectory, revisionId, staging)
    } catch (exception: Throwable) {
        staging.cleanup()
        throw exception
    }
}

/** Suspend variant for staged exports that stream from a datastore. */
suspend fun publishManagedRevisionStreaming(
    outputDirectory: String,
    revisionId: String = Random.nextLong().toString(16),
    write: suspend ManagedExportStaging.() -> Unit,
): ManagedExportRevision {
    require(revisionId.isSafeRevisionId()) { "Invalid export revision id: $revisionId" }
    val root = outputDirectory.trimEnd('/', '\\') + "/.maryk-export"
    val revisionDirectory = "$root/revisions/$revisionId"
    require(File.size("$revisionDirectory/manifest.sha256") == null) { "Managed export revision already exists: $revisionId" }
    val staging = ManagedExportStaging("$root/revisions/.staging-$revisionId-${Random.nextLong().toString(16)}")
    try {
        staging.write()
        return publishStagedRevision(root, revisionDirectory, revisionId, staging)
    } catch (exception: Throwable) {
        staging.cleanup()
        throw exception
    }
}

internal fun publishStagedRevision(
    root: String,
    revisionDirectory: String,
    revisionId: String,
    staging: ManagedExportStaging,
    publishCurrent: (String, String) -> Unit = { pointerRoot, id ->
        File.writeTextViaTemporaryFile("$pointerRoot/current", "$id\n")
    },
): ManagedExportRevision {
    require(staging.paths.isNotEmpty()) { "Managed export revision cannot be empty" }
    val manifest = staging.paths.sorted().joinToString(separator = "") { relativePath ->
        "${sha256Hex(staging.pathOf(relativePath))}  $relativePath\n"
    }
    File.writeText("${staging.directory}/manifest.sha256", manifest)
    staging.paths.forEach { relativePath ->
        check(File.syncFile(staging.pathOf(relativePath))) { "Could not sync staged export: $relativePath" }
    }
    check(File.syncFile("${staging.directory}/manifest.sha256")) { "Could not sync staged manifest" }
    File.syncParentDirectory("${staging.directory}/manifest.sha256")

    var moved = false
    try {
        File.moveReplace(staging.directory, revisionDirectory)
        moved = true
        File.syncParentDirectory("$revisionDirectory/manifest.sha256")
        File.syncParentDirectory(revisionDirectory)
        publishCurrent(root, revisionId)
        return ManagedExportRevision(revisionId, revisionDirectory, "$revisionDirectory/manifest.sha256")
    } catch (exception: Throwable) {
        if (moved) staging.cleanup(revisionDirectory)
        throw exception
    }
}

/** Safe output writer for one unpublished managed revision. */
class ManagedExportStaging internal constructor(
    internal val directory: String,
) {
    internal val paths = linkedSetOf<String>()

    fun writeText(relativePath: String, contents: String) {
        File.writeText(pathForWrite(relativePath), contents)
    }

    fun writeBytes(relativePath: String, contents: ByteArray) {
        File.writeBytes(pathForWrite(relativePath), contents)
    }

    fun appendText(relativePath: String, contents: String) {
        File.appendText(pathForAppend(relativePath), contents)
    }

    /** Declares one safe, unique relative path for callers that need direct [File] streaming access. */
    fun path(relativePath: String): String = reservePath(relativePath)

    internal fun pathOf(relativePath: String): String {
        require(relativePath.isSafeExportPath()) { "Invalid managed export path: $relativePath" }
        return "$directory/$relativePath"
    }

    private fun pathForWrite(relativePath: String): String {
        return reservePath(relativePath)
    }

    private fun reservePath(relativePath: String): String {
        val path = pathOf(relativePath)
        require(paths.add(relativePath)) { "Duplicate managed export path: $relativePath" }
        return path
    }

    private fun pathForAppend(relativePath: String): String {
        val path = pathOf(relativePath)
        paths += relativePath
        return path
    }

    internal fun cleanup(directory: String = this.directory) {
        paths.forEach { relativePath -> File.delete("$directory/$relativePath") }
        File.delete("$directory/manifest.sha256")
        paths.flatMap { relativePath ->
            relativePath.split('/').dropLast(1).indices.map { index ->
                relativePath.split('/').take(index + 1).joinToString("/")
            }
        }.distinct().sortedByDescending { it.length }.forEach { parent ->
            File.delete("$directory/$parent")
        }
        File.delete(directory)
    }
}

private fun String.isSafeRevisionId(): Boolean =
    isNotEmpty() && all { it.isLetterOrDigit() || it == '-' || it == '_' }

private fun String.isSafeExportPath(): Boolean {
    if (isEmpty() || this == "manifest.sha256" || startsWith('/') || startsWith('\\') || contains('\\') || contains('\u0000')) return false
    return split('/').all { it.isNotEmpty() && it != "." && it != ".." && !it.contains('\t') && !it.contains('\n') && !it.contains('\r') }
}

private fun validateExportPaths(paths: List<String>) {
    require(paths.isNotEmpty()) { "Managed export revision cannot be empty" }
    val uniquePaths = HashSet<String>(paths.size)
    paths.forEach { relativePath ->
        require(relativePath.isSafeExportPath()) { "Invalid managed export path: $relativePath" }
        require(uniquePaths.add(relativePath)) { "Duplicate managed export path: $relativePath" }
    }
}

private fun sha256Hex(path: String): String {
    val digest = Sha256()
    check(File.readChunks(path) { chunk -> digest.update(chunk) }) { "Managed export output was not written: $path" }
    return digest.finish().joinToString("") { byte ->
    "0123456789abcdef"[(byte.toInt() ushr 4) and 15].toString() + "0123456789abcdef"[byte.toInt() and 15]
}
}

private class Sha256 {
    private val state = intArrayOf(0x6a09e667, 0xbb67ae85.toInt(), 0x3c6ef372, 0xa54ff53a.toInt(), 0x510e527f, 0x9b05688c.toInt(), 0x1f83d9ab, 0x5be0cd19)
    private val pending = ByteArray(64)
    private var pendingSize = 0
    private var totalBytes = 0L

    fun update(input: ByteArray) {
        totalBytes += input.size
        var offset = 0
        if (pendingSize > 0) {
            val copied = minOf(64 - pendingSize, input.size)
            input.copyInto(pending, pendingSize, 0, copied)
            pendingSize += copied
            offset += copied
            if (pendingSize == 64) {
                processBlock(pending)
                pendingSize = 0
            }
        }
        while (offset + 64 <= input.size) {
            processBlock(input, offset)
            offset += 64
        }
        if (offset < input.size) {
            input.copyInto(pending, 0, offset)
            pendingSize = input.size - offset
        }
    }

    fun finish(): ByteArray {
        val finalBlock = ByteArray(if (pendingSize + 9 <= 64) 64 else 128)
        pending.copyInto(finalBlock, 0, 0, pendingSize)
        finalBlock[pendingSize] = 0x80.toByte()
        val bitLength = totalBytes * 8
        for (index in 0 until 8) finalBlock[finalBlock.lastIndex - index] = (bitLength ushr (index * 8)).toByte()
        processBlock(finalBlock)
        if (finalBlock.size == 128) processBlock(finalBlock, 64)
        return ByteArray(32) { index -> (state[index / 4] ushr (24 - (index % 4) * 8)).toByte() }
    }

    private fun processBlock(input: ByteArray, offset: Int = 0) {
        val words = IntArray(64)
        for (index in 0 until 16) {
            val start = offset + index * 4
            words[index] = ((input[start].toInt() and 255) shl 24) or ((input[start + 1].toInt() and 255) shl 16) or
                ((input[start + 2].toInt() and 255) shl 8) or (input[start + 3].toInt() and 255)
        }
        for (index in 16 until 64) words[index] = small1(words[index - 2]) + words[index - 7] + small0(words[index - 15]) + words[index - 16]
        var a = state[0]; var b = state[1]; var c = state[2]; var d = state[3]
        var e = state[4]; var f = state[5]; var g = state[6]; var h = state[7]
        for (index in words.indices) {
            val temporary1 = h + big1(e) + choose(e, f, g) + constants[index] + words[index]
            val temporary2 = big0(a) + majority(a, b, c)
            h = g; g = f; f = e; e = d + temporary1
            d = c; c = b; b = a; a = temporary1 + temporary2
        }
        state[0] += a; state[1] += b; state[2] += c; state[3] += d
        state[4] += e; state[5] += f; state[6] += g; state[7] += h
    }

    private companion object {
    private val constants = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(), 0x3956c25b, 0x59f111f1,
        0x923f82a4.toInt(), 0xab1c5ed5.toInt(), 0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(), 0xe49b69c1.toInt(), 0xefbe4786.toInt(),
        0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(), 0xc6e00bf3.toInt(), 0xd5a79147.toInt(),
        0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(), 0xa2bfe8a1.toInt(), 0xa81a664b.toInt(),
        0xc24b8b70.toInt(), 0xc76c51a3.toInt(), 0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a,
        0x5b9cca4f, 0x682e6ff3, 0x748f82ee.toInt(), 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
        0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )
    }

    private fun choose(x: Int, y: Int, z: Int) = (x and y) xor (x.inv() and z)
    private fun majority(x: Int, y: Int, z: Int) = (x and y) xor (x and z) xor (y and z)
    private fun big0(value: Int) = value.rotateRight(2) xor value.rotateRight(13) xor value.rotateRight(22)
    private fun big1(value: Int) = value.rotateRight(6) xor value.rotateRight(11) xor value.rotateRight(25)
    private fun small0(value: Int) = value.rotateRight(7) xor value.rotateRight(18) xor (value ushr 3)
    private fun small1(value: Int) = value.rotateRight(17) xor value.rotateRight(19) xor (value ushr 10)
}
