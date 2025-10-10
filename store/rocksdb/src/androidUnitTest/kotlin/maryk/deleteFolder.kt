package maryk

import java.io.File

actual fun deleteFolder(path: String): Boolean = File(path).deleteRecursively()
