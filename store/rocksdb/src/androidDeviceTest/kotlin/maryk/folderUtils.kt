package maryk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File

private fun folder(path: String) =
    File(path).takeIf { it.isAbsolute }
        ?: File(ApplicationProvider.getApplicationContext<Context>().filesDir, path)

internal actual fun testDBFolderBasePath(): String =
    File(ApplicationProvider.getApplicationContext<Context>().filesDir, "test-database").absolutePath

actual fun createFolder(path: String): Boolean = folder(path).mkdirs()

actual fun deleteFolder(path: String): Boolean = folder(path).deleteRecursively()

actual fun doesFolderExist(path: String): Boolean = folder(path).exists()
