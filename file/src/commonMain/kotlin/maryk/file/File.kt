package maryk.file

/**
 * Minimal cross-platform file IO needed by the CLI/storage tooling.
 *
 * Only implemented on platforms that support local file system access (JVM, Android, Native).
 */
expect object File {
    fun readText(path: String): String?
    fun writeText(path: String, contents: String)
    fun writeBytes(path: String, contents: ByteArray)
    fun appendText(path: String, contents: String)
    fun delete(path: String): Boolean
}
