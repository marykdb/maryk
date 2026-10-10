package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.pairs.with
import maryk.core.query.responses.AtomicStatus
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.executeAtomic
import maryk.test.models.SimpleMarykModel
import kotlin.test.assertEquals
import maryk.datastore.test.dataModelsForTests
import maryk.datastore.test.runAtomicDataStoreTests
import maryk.datastore.shared.supportsAtomicRequests
import kotlin.test.assertTrue
import kotlin.test.Test

class AtomicRequestTest {
    @Test
    fun sharedAtomicContract() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = dataModelsForTests, keepAllVersions = true, keepUpdateHistoryIndex = true)
        try { assertTrue(store.supportsAtomicRequests); runAtomicDataStoreTests(store) } finally { store.close() }
    }
    @Test
    fun olderImplementationsRejectWithoutFallback() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = dataModelsForTests)
        try {
            val legacy = object : IsDataStore by store {}
            assertEquals(false, legacy.supportsAtomicRequests)
            val request = AtomicRequest("unsupported", SimpleMarykModel.add(SimpleMarykModel.create { value with "haha-unsupported" }))
            assertEquals(AtomicStatus.Unsupported, legacy.executeAtomic(request).status)
            assertTrue(store.execute(SimpleMarykModel.scan(allowTableScan = true)).values.isEmpty())
        } finally { store.close() }
    }
}
