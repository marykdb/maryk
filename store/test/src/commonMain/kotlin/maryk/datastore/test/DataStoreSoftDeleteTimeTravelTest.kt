package maryk.datastore.test

import maryk.core.exceptions.RequestException
import maryk.core.models.key
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.properties.types.Key
import maryk.datastore.shared.IsDataStore
import maryk.test.models.SimpleMarykModel
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DataStoreSoftDeleteTimeTravelTest(
    val dataStore: IsDataStore
) : IsDataStoreTest {
    private val keys = mutableListOf<Key<SimpleMarykModel>>()

    override val allTests = mapOf(
        "softDeleteRespectsToVersion" to ::softDeleteRespectsToVersion
    )

    override suspend fun resetData() {
        if (keys.isEmpty()) return
        dataStore.execute(
            SimpleMarykModel.delete(*keys.toTypedArray(), hardDelete = true)
        )
        keys.clear()
    }

    private suspend fun softDeleteRespectsToVersion() {
        if (!dataStore.keepAllVersions) {
            assertFailsWith<RequestException> {
                dataStore.execute(
                    SimpleMarykModel.scan(
                        toVersion = 1uL,
                        allowTableScan = true,
                    )
                )
            }
            return
        }

        val marker = "haha-soft-delete-time-travel"
        val filter = Equals(SimpleMarykModel.value.ref() with marker)
        val values = SimpleMarykModel.create {
            value with marker
        }
        val key = SimpleMarykModel.key(values)
        keys.add(key)

        val addResponse = dataStore.execute(
            SimpleMarykModel.add(key to values)
        )
        val addStatus = assertIs<AddSuccess<*>>(addResponse.statuses.first())

        val deleteResponse = dataStore.execute(
            SimpleMarykModel.delete(key, hardDelete = false)
        )
        val deleteStatus = assertIs<DeleteSuccess<*>>(deleteResponse.statuses.first())

        val scanFiltered = dataStore.execute(
            SimpleMarykModel.scan(
                where = filter,
                filterSoftDeleted = true,
                allowTableScan = true,
            )
        )
        val scanAll = dataStore.execute(
            SimpleMarykModel.scan(
                where = filter,
                filterSoftDeleted = false,
                allowTableScan = true,
            )
        )
        assertEquals(0, scanFiltered.values.size)
        assertEquals(1, scanAll.values.size)
        assertTrue(scanAll.values.first().isDeleted)

        val scanAtAdd = dataStore.execute(
            SimpleMarykModel.scan(
                toVersion = addStatus.version,
                where = filter,
                filterSoftDeleted = true,
                allowTableScan = true,
            )
        )
        assertEquals(1, scanAtAdd.values.size)
        assertTrue(!scanAtAdd.values.first().isDeleted)

        val scanAtDeleteFiltered = dataStore.execute(
            SimpleMarykModel.scan(
                toVersion = deleteStatus.version,
                where = filter,
                filterSoftDeleted = true,
                allowTableScan = true,
            )
        )
        val scanAtDeleteAll = dataStore.execute(
            SimpleMarykModel.scan(
                toVersion = deleteStatus.version,
                where = filter,
                filterSoftDeleted = false,
                allowTableScan = true,
            )
        )
        assertEquals(0, scanAtDeleteFiltered.values.size)
        assertEquals(1, scanAtDeleteAll.values.size)
        assertTrue(scanAtDeleteAll.values.first().isDeleted)
    }
}
