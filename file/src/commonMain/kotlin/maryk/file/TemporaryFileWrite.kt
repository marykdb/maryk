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
    writeAtomically(path) { temporaryPath -> writeText(temporaryPath, contents) }
}

/** Internal seam for exercising failed durable publication without platform-specific fault injection. */
internal fun File.writeTextViaTemporaryFile(path: String, contents: String, syncTemporaryFile: (String) -> Boolean) {
    writeAtomically(path, syncTemporaryFile) { temporaryPath -> writeText(temporaryPath, contents) }
}

/** See [writeTextViaTemporaryFile]. */
fun File.writeBytesViaTemporaryFile(path: String, contents: ByteArray) {
    writeAtomically(path) { temporaryPath -> writeBytes(temporaryPath, contents) }
}

private inline fun File.writeAtomically(
    path: String,
    syncTemporaryFile: (String) -> Boolean = ::syncFile,
    write: (temporaryPath: String) -> Unit,
) {
    val temporaryPath = "$path.${Random.nextLong().toString(16)}.tmp"
    try {
        write(temporaryPath)
        check(syncTemporaryFile(temporaryPath)) { "Could not sync temporary file: $temporaryPath" }
        moveReplace(temporaryPath, path)
        syncParentDirectory(path)
    } finally {
        delete(temporaryPath)
    }
}
