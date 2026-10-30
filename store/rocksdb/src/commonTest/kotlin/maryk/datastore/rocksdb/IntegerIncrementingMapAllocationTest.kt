package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.AllocationDescriptorRecord
import maryk.datastore.test.assertIntegerIncrementingMapAllocation
import kotlin.test.Test
import maryk.createTestDBFolder
import maryk.deleteFolder

class IntegerIncrementingMapAllocationTest {
    @Test
    fun allIntegerDescriptorsAndExplicitFloatingPointKeysPreserveTheirContracts() = runTest {
        val folder = createTestDBFolder("integer-incmap-descriptors")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to AllocationDescriptorRecord), keepAllVersions = true)
        try {
            assertIntegerIncrementingMapAllocation(store)
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }
}
