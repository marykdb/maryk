package maryk.file

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

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

    actual fun readChunks(path: String, chunkSize: Int, onChunk: (ByteArray) -> Unit): Boolean {
        require(chunkSize > 0) { "Chunk size must be positive" }
        val file = File(path)
        if (!file.isFile) return false
        file.inputStream().use { input ->
            val buffer = ByteArray(chunkSize)
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                if (size > 0) onChunk(buffer.copyOf(size))
            }
        }
        return true
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

    actual fun moveReplace(sourcePath: String, destinationPath: String) {
        try {
            Files.move(Path.of(sourcePath), Path.of(destinationPath), ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(Path.of(sourcePath), Path.of(destinationPath), REPLACE_EXISTING)
        }
    }

    actual fun delete(path: String): Boolean = File(path).delete()
}
