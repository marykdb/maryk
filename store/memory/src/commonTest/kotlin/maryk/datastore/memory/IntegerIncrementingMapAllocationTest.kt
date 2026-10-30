package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.AllocationDescriptorRecord
import maryk.datastore.test.assertIntegerIncrementingMapAllocation
import kotlin.test.Test

class IntegerIncrementingMapAllocationTest {
    @Test
    fun allIntegerDescriptorsAndExplicitFloatingPointKeysPreserveTheirContracts() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to AllocationDescriptorRecord), keepAllVersions = true)
        try {
            assertIntegerIncrementingMapAllocation(store)
        } finally {
            store.close()
        }
    }
}
