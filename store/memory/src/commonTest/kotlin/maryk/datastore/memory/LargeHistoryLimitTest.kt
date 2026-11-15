package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.query.requests.MAX_SCAN_LIMIT
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.requests.scanUpdateHistory
import maryk.core.query.responses.statuses.AddSuccess
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LargeHistoryLimitTest {
    @Test
    fun largeLimitsStillReturnResultsBeyondTheInitialCapacity() = runTest {
        val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to SimpleMarykModel),
            keepAllVersions = true, keepUpdateHistoryIndex = true)
        try {
            assertEquals(0, store.execute(SimpleMarykModel.scanUpdateHistory(limit = MAX_SCAN_LIMIT)).updates.size)
            val values = List(300) { index -> SimpleMarykModel.create { value with "haha-$index" } }
            val added = store.execute(SimpleMarykModel.add(*values.toTypedArray()))
            assertTrue(added.statuses.all { it is AddSuccess })
            assertEquals(300, store.execute(SimpleMarykModel.scan(limit = MAX_SCAN_LIMIT, allowTableScan = true)).values.size)
            assertEquals(300, store.execute(SimpleMarykModel.scanUpdateHistory(
                limit = MAX_SCAN_LIMIT,
            )).updates.size)
        } finally { store.close() }
    }
}
