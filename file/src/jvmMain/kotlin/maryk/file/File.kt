package maryk.file

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
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

    actual fun readText(path: String, maxBytes: Int): String? =
        readBytes(path, maxBytes)?.decodeToString()

    actual fun readBytes(path: String): ByteArray? {
        return readBytes(path, Int.MAX_VALUE)
    }

    actual fun readBytes(path: String, maxBytes: Int): ByteArray? {
        require(maxBytes >= 0) { "Maximum byte count cannot be negative" }
        val file = File(path)
        if (!file.isFile) return null
        if ((size(path) ?: return null) > minOf(maxFileSize, maxBytes.toLong())) return null
        file.inputStream().use { input ->
            val initialCapacity = minOf(file.length(), maxBytes.toLong(), 8192L).toInt()
            val output = ByteArrayOutputStream(initialCapacity)
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (total < maxBytes) {
                val count = input.read(buffer, 0, minOf(buffer.size, maxBytes - total))
                if (count < 0) return output.toByteArray()
                if (count > 0) {
                    output.write(buffer, 0, count)
                    total += count
                }
            }
            return if (input.read() < 0) output.toByteArray() else null
        }
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

    actual fun syncFile(path: String): Boolean =
        runCatching {
            FileChannel.open(Path.of(path), READ).use { it.force(true) }
        }.isSuccess

    actual fun syncParentDirectory(path: String): Boolean =
        runCatching {
            val parent = Path.of(path).parent ?: Path.of(".")
            FileChannel.open(parent, READ).use { it.force(true) }
        }.isSuccess

    actual fun delete(path: String): Boolean = File(path).delete()
}
