package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.models.key
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.extensions.bytes.invert
import maryk.datastore.rocksdb.processors.SOFT_DELETE_INDICATOR
import maryk.datastore.test.dataModelsForTests
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.lib.bytes.combineToByteArray
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SoftDeleteLegacyTimeTravelTest {
    @Test
    fun softDeleteFallbackUsesCurrentTableVersion() = runTest {
        val folder = createTestDBFolder("soft-delete-legacy-time-travel")

        val dataStore = RocksDBDataStore.open(
            relativePath = folder,
            keepAllVersions = true,
            dataModelsById = dataModelsForTests,
        )

        val values = SimpleMarykModel.create {
            value with "haha-legacy"
        }
        val key = SimpleMarykModel.key(values)

        val addResponse = dataStore.execute(
            SimpleMarykModel.add(key to values)
        )
        assertIs<AddSuccess<*>>(addResponse.statuses.first())

        val deleteResponse = dataStore.execute(
            SimpleMarykModel.delete(key, hardDelete = false)
        )
        val deleteStatus = assertIs<DeleteSuccess<*>>(deleteResponse.statuses.first())

        val columnFamilies = dataStore.getColumnFamilies(SimpleMarykModel) as HistoricTableColumnFamilies
        val versionBytes = HLC.toStorageBytes(HLC(deleteStatus.version))
        val historicReference = combineToByteArray(key.bytes, SOFT_DELETE_INDICATOR, versionBytes).apply {
            invert(size - versionBytes.size)
        }
        dataStore.db.delete(columnFamilies.historic.table, historicReference)

        val getAtDelete = dataStore.execute(
            SimpleMarykModel.get(
                key,
                toVersion = deleteStatus.version,
                filterSoftDeleted = false,
            )
        )
        assertEquals(1, getAtDelete.values.size)
        assertTrue(getAtDelete.values.first().isDeleted)

        val getBeforeDelete = dataStore.execute(
            SimpleMarykModel.get(
                key,
                toVersion = deleteStatus.version - 1uL,
                filterSoftDeleted = false,
            )
        )
        assertEquals(1, getBeforeDelete.values.size)
        assertTrue(!getBeforeDelete.values.first().isDeleted)

        val scanAtDelete = dataStore.execute(
            SimpleMarykModel.scan(
                toVersion = deleteStatus.version,
                filterSoftDeleted = false,
                allowTableScan = true,
            )
        )
        assertTrue(scanAtDelete.values.any { it.key == key })
        assertTrue(scanAtDelete.values.first { it.key == key }.isDeleted)

        val scanBeforeDelete = dataStore.execute(
            SimpleMarykModel.scan(
                toVersion = deleteStatus.version - 1uL,
                filterSoftDeleted = false,
                allowTableScan = true,
            )
        )
        assertTrue(scanBeforeDelete.values.any { it.key == key })
        assertTrue(!scanBeforeDelete.values.first { it.key == key }.isDeleted)

        dataStore.close()
        deleteFolder(folder)
    }
}
