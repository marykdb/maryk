package maryk.file

/**
 * Minimal cross-platform file IO needed by the CLI/storage tooling.
 *
 * Only implemented on platforms that support local file system access (JVM, Android, Native).
 */
expect object File {
    fun size(path: String): Long?
    fun readText(path: String): String?
    fun readBytes(path: String): ByteArray?
    /** Calls [onChunk] with bounded chunks from a regular file. Returns false when unavailable. */
    fun readChunks(path: String, chunkSize: Int = 64 * 1024, onChunk: (ByteArray) -> Unit): Boolean
    fun writeText(path: String, contents: String)
    fun writeBytes(path: String, contents: ByteArray)
    fun appendText(path: String, contents: String)
    fun moveReplace(sourcePath: String, destinationPath: String)
    fun delete(path: String): Boolean
}
