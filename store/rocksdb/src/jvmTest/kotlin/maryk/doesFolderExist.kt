package maryk

import java.io.File

actual fun doesFolderExist(path: String): Boolean = File(path).exists()
