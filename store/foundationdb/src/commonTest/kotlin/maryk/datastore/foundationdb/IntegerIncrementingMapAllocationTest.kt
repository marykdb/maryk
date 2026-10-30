package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.AllocationDescriptorRecord
import maryk.datastore.test.assertIntegerIncrementingMapAllocation
import kotlin.test.Test
import kotlin.random.Random

class IntegerIncrementingMapAllocationTest {
    @Test
    fun allIntegerDescriptorsAndExplicitFloatingPointKeysPreserveTheirContracts() = runTest {
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = listOf("maryk", "integer-incmap-${Random.nextInt()}"),
            dataModelsById = mapOf(1u to AllocationDescriptorRecord), keepAllVersions = true)
        try {
            assertIntegerIncrementingMapAllocation(store)
        } finally {
            store.close()
        }
    }
}
