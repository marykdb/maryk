package maryk.datastore.memory

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import maryk.core.exceptions.StorageException
import maryk.core.query.requests.AtomicRequest
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.AtomicStatus
import maryk.datastore.shared.executeAtomic
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClosedStoreMemoryTest {
    @Test
    fun concurrentCloseReleasesRecordAndReceiptGraphsAndRemainsTerminal() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel))
        try {
            assertEquals(AtomicStatus.Committed, store.executeAtomic(AtomicRequest("retained-receipt",
                SimpleMarykModel.add(SimpleMarykModel.create { value with "ha close" }),
            )).status)
            fun retainedMap(name: String): Map<*, *> = store.javaClass.getDeclaredField(name).let { field ->
                field.isAccessible = true
                field.get(store) as Map<*, *>
            }
            val records = retainedMap("atomicDataStores")
            val receipts = retainedMap("atomicReceipts")
            assertTrue(records.isNotEmpty())
            assertTrue(receipts.isNotEmpty())
            List(4) { async { store.close() } }.awaitAll()
            assertTrue(records.isEmpty())
            assertTrue(receipts.isEmpty())
            assertFailsWith<StorageException> { store.execute(SimpleMarykModel.scan(allowTableScan = true)) }
        } finally { store.close() }
    }
}
