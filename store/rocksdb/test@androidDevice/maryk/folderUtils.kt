package maryk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlin.random.Random
import kotlin.random.nextUInt

private fun folder(path: String) =
    File(path).takeIf { it.isAbsolute }
        ?: File(ApplicationProvider.getApplicationContext<Context>().filesDir, path)

fun createTestDBFolder(name: String?) =
    File(ApplicationProvider.getApplicationContext<Context>().filesDir, "test-database/${name!!}_${Random.nextUInt()}")
        .absolutePath
        .also {
            if (!doesFolderExist(it)) {
                createFolder(it)
            }
        }

fun createFolder(path: String): Boolean = folder(path).mkdirs()

fun deleteFolder(path: String): Boolean = folder(path).deleteRecursively()

fun doesFolderExist(path: String): Boolean = folder(path).exists()
