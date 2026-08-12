package maryk.file

import kotlin.random.Random

/**
 * Writes a complete replacement beside [path] before replacing it.
 *
 * A failed write leaves an existing destination unchanged. Replacement uses
 * [File.moveReplace], which is atomic where the platform supports it and
 * otherwise is best-effort. This protects one file only; callers writing
 * multiple files must still define their own set publication contract.
 */
fun File.writeTextViaTemporaryFile(path: String, contents: String) {
    writeAtomically(path) { temporaryPath -> writeText(temporaryPath, contents) }
}

/** See [writeTextViaTemporaryFile]. */
fun File.writeBytesViaTemporaryFile(path: String, contents: ByteArray) {
    writeAtomically(path) { temporaryPath -> writeBytes(temporaryPath, contents) }
}

private inline fun File.writeAtomically(path: String, write: (temporaryPath: String) -> Unit) {
    val temporaryPath = "$path.${Random.nextLong().toString(16)}.tmp"
    try {
        write(temporaryPath)
        moveReplace(temporaryPath, path)
    } finally {
        delete(temporaryPath)
    }
}
