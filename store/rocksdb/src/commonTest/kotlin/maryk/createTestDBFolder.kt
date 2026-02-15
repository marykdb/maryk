package maryk

import kotlin.random.Random
import kotlin.random.nextUInt

fun createTestDBFolder(name: String?) =
    ("build/test-database/${name!!}_" + Random.nextUInt()).also {
        if(!doesFolderExist(it)) {
            createFolder(it)
        }
    }
