package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.map
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.foundationdb.Range
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

private object ConcurrentUniqueRecord : RootDataModel<ConcurrentUniqueRecord>() {
    val entries by map(1u, required = false, keyDefinition = StringDefinition(), valueDefinition = StringDefinition(unique = true))
}

class ConcurrentUniqueDescriptorTest {
    @Test
    fun hardDeleteDiscoversDynamicUniqueHistoryCreatedByAnotherOpenWriter() = runTest(timeout = 3.minutes) {
        val directory = listOf("maryk", "test", "concurrent-unique-descriptor", Uuid.random().toString())
        suspend fun open() = FoundationDBDataStore.open(directoryPath = directory,
            dataModelsById = mapOf(1u to ConcurrentUniqueRecord), keepAllVersions = true)
        val first = open()
        try {
            val second = open()
            try {
                // An ordinary hard delete caches the original empty descriptor inventory.
                val primer = assertIs<AddSuccess<ConcurrentUniqueRecord>>(first.execute(ConcurrentUniqueRecord.add(
                    ConcurrentUniqueRecord.create { entries with emptyMap() },
                )).statuses.single())
                assertIs<DeleteSuccess<ConcurrentUniqueRecord>>(first.execute(ConcurrentUniqueRecord.delete(primer.key, hardDelete = true)).statuses.single())

                val original = assertIs<AddSuccess<ConcurrentUniqueRecord>>(second.execute(ConcurrentUniqueRecord.add(
                    ConcurrentUniqueRecord.create { entries with mapOf("dynamic-slot" to "transferred") },
                )).statuses.single())
                assertIs<ChangeSuccess<ConcurrentUniqueRecord>>(second.execute(ConcurrentUniqueRecord.change(original.key.change(
                    Change(ConcurrentUniqueRecord.ref { entries } with emptyMap<String, String>()),
                ))).statuses.single())
                val successor = assertIs<AddSuccess<ConcurrentUniqueRecord>>(second.execute(ConcurrentUniqueRecord.add(
                    ConcurrentUniqueRecord.create { entries with mapOf("dynamic-slot" to "transferred") },
                )).statuses.single())
                val directories = first.getTableDirs(ConcurrentUniqueRecord) as HistoricTableDirectories
                fun owners() = first.runTransaction { transaction ->
                    buildList {
                        val rows = transaction.getRange(Range.startsWith(directories.historicUniquePrefix)).iterator()
                        while (rows.hasNext()) add(rows.nextBlocking().value)
                    }
                }
                assertTrue(owners().any { it.contentEquals(original.key.bytes) })
                assertTrue(owners().any { it.contentEquals(successor.key.bytes) })

                assertIs<DeleteSuccess<ConcurrentUniqueRecord>>(first.execute(ConcurrentUniqueRecord.delete(original.key, hardDelete = true)).statuses.single())
                assertTrue(owners().none { it.contentEquals(original.key.bytes) },
                    "Hard deletion must purge historic unique ownership despite another writer's descriptor cache")
                assertTrue(owners().any { it.contentEquals(successor.key.bytes) }, "The successor's history must be preserved")
                assertEquals(mapOf("dynamic-slot" to "transferred"), second.execute(ConcurrentUniqueRecord.get(successor.key)).values.single().values { entries })
            } finally { second.close() }
        } finally { first.close() }
    }
}
