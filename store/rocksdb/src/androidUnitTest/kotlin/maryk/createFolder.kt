package maryk

import java.io.File

actual fun createFolder(path: String): Boolean = File(path).mkdirs()
