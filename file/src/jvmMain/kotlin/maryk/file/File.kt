package maryk.file

import java.io.File

private const val maxFileSize = Int.MAX_VALUE.toLong()

actual object File {
    actual fun size(path: String): Long? {
        val file = File(path)
        return if (file.isFile) file.length() else null
    }

    actual fun readText(path: String): String? {
        return readBytes(path)?.decodeToString()
    }

    actual fun readBytes(path: String): ByteArray? {
        val file = File(path)
        if (!file.isFile) return null
        if ((size(path) ?: return null) > maxFileSize) return null
        return file.readBytes()
    }

    actual fun writeText(path: String, contents: String) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(contents)
    }

    actual fun writeBytes(path: String, contents: ByteArray) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(contents)
    }

    actual fun appendText(path: String, contents: String) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.appendText(contents)
    }

    actual fun delete(path: String): Boolean = File(path).delete()
}
