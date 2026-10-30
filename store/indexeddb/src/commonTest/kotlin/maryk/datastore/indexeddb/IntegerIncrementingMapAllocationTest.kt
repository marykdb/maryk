package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import maryk.datastore.test.AllocationDescriptorRecord
import maryk.datastore.test.assertIntegerIncrementingMapAllocation
import kotlin.test.Test
import kotlin.random.Random

class IntegerIncrementingMapAllocationTest {
    @Test
    fun allIntegerDescriptorsAndExplicitFloatingPointKeysPreserveTheirContracts() = runTest {
        installIndexedDbForTests()
        val store = IndexedDbDataStore.open("integer-incmap-${Random.nextInt()}", mapOf(1u to AllocationDescriptorRecord), keepAllVersions = true)
        try {
            assertIntegerIncrementingMapAllocation(store)
        } finally {
            store.close()
        }
    }
}
