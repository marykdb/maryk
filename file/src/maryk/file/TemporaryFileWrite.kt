package maryk.file

import kotlin.random.Random

/**
 * Writes a complete replacement beside [path] before replacing it.
 *
 * A failed write or file sync leaves an existing destination unchanged.
 * Replacement uses [File.moveReplace], which is atomic where the platform
 * supports it and otherwise is best-effort. The temporary file is flushed
 * before replacement and the parent directory is flushed afterwards when the
 * platform supports directory syncing. If [File.syncParentDirectory] returns
 * false, visibility is still atomic where supported, but crash durability of
 * the replacement is not guaranteed. This protects one file only; callers
 * writing multiple files must still define their own set publication contract.
 */
fun File.writeTextViaTemporaryFile(path: String, contents: String) {
    writeAtomically(path, contents.encodeToByteArray())
}

/** Internal seam for exercising failed durable publication without platform-specific fault injection. */
internal fun File.writeTextViaTemporaryFile(path: String, contents: String, syncTemporaryFile: (String) -> Boolean) {
    writeAtomically(path, contents.encodeToByteArray(), syncTemporaryFile)
}

/** See [writeTextViaTemporaryFile]. */
fun File.writeBytesViaTemporaryFile(path: String, contents: ByteArray) {
    writeAtomically(path, contents)
}

private fun File.writeAtomically(
    path: String,
    contents: ByteArray,
    syncTemporaryFile: (String) -> Boolean = ::syncFile,
) {
    val temporaryPath = temporarySiblingPath(path)
    try {
        writeBytesExclusively(temporaryPath, contents)
        check(syncTemporaryFile(temporaryPath)) { "Could not sync temporary file: $temporaryPath" }
        moveReplace(temporaryPath, path)
        syncParentDirectory(path)
    } finally {
        delete(temporaryPath)
    }
}

internal fun temporarySiblingPath(path: String): String {
    val separator = path.indexOfLast { it == '/' || it == '\\' }
    val parent = if (separator < 0) "" else path.substring(0, separator + 1)
    return "${parent}.maryk-${Random.nextLong().toString(16)}.tmp"
}
