package maryk.file

import java.io.File

actual object File {
    actual fun readText(path: String): String? {
        val file = File(path)
        return if (file.exists()) file.readText() else null
    }

    actual fun readBytes(path: String): ByteArray? {
        val file = File(path)
        return if (file.exists()) file.readBytes() else null
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
