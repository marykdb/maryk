package maryk

import kotlin.random.Random
import kotlin.random.nextUInt

internal expect fun testDBFolderBasePath(): String

fun createTestDBFolder(name: String?) =
    ("${testDBFolderBasePath()}/${name!!}_" + Random.nextUInt()).also {
        if (!doesFolderExist(it)) {
            createFolder(it)
        }
    }
