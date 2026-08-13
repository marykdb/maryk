package io.maryk.app

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class PackagedStoreSmokeTest {
    @Test
    fun packagedStoreSmokeWritesAndReadsAfterReopeningRocksDbStore() {
        val directory = Files.createTempDirectory("maryk-packaged-store-smoke")
        try {
            assertEquals(PACKAGED_STORE_SMOKE_SUCCESS, runPackagedStoreSmoke(directory.toString()))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
