package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.string
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.processors.readTransactionIntoValuesWithMetaData
import maryk.datastore.shared.encryption.AesGcmHmacSha256EncryptionProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object CollectionProjectionModel : RootDataModel<CollectionProjectionModel>() {
    val entries by map(1u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
    val title by string(2u)
    val optional by string(3u, required = false)
}

private object EncryptedProjectionModel : RootDataModel<EncryptedProjectionModel>() {
    val entries by map(1u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
    val title by string(2u)
    val secret by string(3u, sensitive = true, required = false)
}

class ProjectionSeekTest {
    @Test
    fun scanProjectionUsesEachRecordsKeyAndDeletionMetadata() = runTest {
        val store = RocksDBDataStore.open(relativePath = createTestDBFolder("projection-scan"), dataModelsById = mapOf(1u to CollectionProjectionModel))
        try {
            val added = store.execute(CollectionProjectionModel.add(
                CollectionProjectionModel.create { entries with (0 until 64).associate { "$it" to "hidden" }; title with "first"; optional with "present" },
                CollectionProjectionModel.create { entries with mapOf("other" to "hidden"); title with "second" },
            )).statuses.map { assertIs<AddSuccess<CollectionProjectionModel>>(it) }
            val selected = CollectionProjectionModel.graph { listOf(title, optional) }
            val rows = store.execute(CollectionProjectionModel.scan(select = selected, allowTableScan = true)).values.associateBy { it.key }
            assertEquals("first", rows.getValue(added[0].key).values { title })
            assertEquals("present", rows.getValue(added[0].key).values { optional })
            assertEquals("second", rows.getValue(added[1].key).values { title })
            assertNull(rows.getValue(added[1].key).values { optional })
            rows.values.forEach { assertNull(it.values { entries }) }
            store.execute(CollectionProjectionModel.delete(added[0].key))
            val visible = store.execute(CollectionProjectionModel.scan(select = selected, allowTableScan = true)).values
            assertEquals(listOf(added[1].key), visible.map { it.key })
            val deleted = store.execute(CollectionProjectionModel.scan(select = selected, filterSoftDeleted = false, allowTableScan = true)).values.associateBy { it.key }
            assertTrue(deleted.getValue(added[0].key).isDeleted)
            assertEquals(false, deleted.getValue(added[1].key).isDeleted)
            assertTrue(deleted.getValue(added[0].key).lastVersion > added[0].version)
        } finally { store.close() }
    }

    @Test
    fun scalarProjectionPreservesEncryptedValuesAndMissingProperties() = runTest {
        val store = RocksDBDataStore.open(
            relativePath = createTestDBFolder("projection-encrypted"),
            dataModelsById = mapOf(1u to EncryptedProjectionModel),
            fieldEncryptionProvider = AesGcmHmacSha256EncryptionProvider(
                ByteArray(32) { it.toByte() }, ByteArray(32) { (it + 32).toByte() },
            ),
        )
        try {
            val added = store.execute(EncryptedProjectionModel.add(
                EncryptedProjectionModel.create { entries with mapOf("child" to "hidden"); title with "first"; secret with "Zoë's private note" },
                EncryptedProjectionModel.create { entries with mapOf("child" to "hidden"); title with "second" },
            )).statuses.map { assertIs<AddSuccess<EncryptedProjectionModel>>(it) }
            val selected = EncryptedProjectionModel.graph { listOf(title, secret) }
            val rows = store.execute(EncryptedProjectionModel.get(added[1].key, added[0].key, select = selected)).values
            assertEquals("second", rows[0].values { title })
            assertNull(rows[0].values { secret })
            assertEquals("Zoë's private note", rows[1].values { secret })
            assertNull(rows[1].values { entries })
            assertEquals(added[0].version, rows[1].lastVersion)
        } finally { store.close() }
    }

    @Test
    fun largeScalarProjectionPreservesValuesAndRequestOrder() = runTest {
        val store = RocksDBDataStore.open(relativePath = createTestDBFolder("projection-large"), dataModelsById = mapOf(1u to CollectionProjectionModel))
        try {
            val large = "x".repeat(280_000)
            val added = store.execute(CollectionProjectionModel.add(
                CollectionProjectionModel.create { entries with mapOf("child" to "hidden"); title with large },
                CollectionProjectionModel.create { entries with mapOf("child" to "hidden"); title with "small" },
            )).statuses.map { assertIs<AddSuccess<CollectionProjectionModel>>(it) }
            val rows = store.execute(CollectionProjectionModel.get(added[1].key, added[0].key, added[1].key,
                select = CollectionProjectionModel.graph { listOf(title, optional) })).values
            assertEquals(listOf("small", large, "small"), rows.map { it.values { title } })
            rows.forEach { assertNull(it.values { optional }); assertNull(it.values { entries }) }
        } finally { store.close() }
    }

    @Test
    fun scalarProjectionSkipsCollectionCellsAndPreservesMetadata() = runTest {
        val store = RocksDBDataStore.open(
            relativePath = createTestDBFolder("projection-seek"),
            dataModelsById = mapOf(1u to CollectionProjectionModel),
        )
        try {
            val added = assertIs<AddSuccess<CollectionProjectionModel>>(store.execute(CollectionProjectionModel.add(
                CollectionProjectionModel.create {
                    entries with (0 until 2048).associate { "item-$it" to "value-$it" }
                    title with "metadata after collection"
                },
            )).statuses.single())
            var advances = 0
            val families = store.getColumnFamilies(CollectionProjectionModel)
            val selected = CollectionProjectionModel.graph { listOf(title, optional) }
            object : DBIterator(store.db.newIterator(families.table, store.sequentialReadOptions)) {
                override fun next() { advances++; super.next() }
            }.use { iterator ->
                val result = requireNotNull(CollectionProjectionModel.readTransactionIntoValuesWithMetaData(
                    iterator, store, added.version, families, added.key, selected, null,
                ) { _, _, reader -> reader() })
                assertEquals("metadata after collection", result.values { title })
                assertNull(result.values { optional })
                assertNull(result.values { entries })
                assertEquals(added.version, result.firstVersion)
                assertEquals(added.version, result.lastVersion)
                assertTrue(advances < 10, "Projection walked $advances cells of an unselected collection")
            }
            store.execute(CollectionProjectionModel.delete(added.key))
            val result = store.execute(CollectionProjectionModel.get(
                added.key, select = selected, filterSoftDeleted = false,
            )).values.single()
            assertTrue(result.isDeleted)
            assertTrue(result.lastVersion > added.version)
            assertEquals("metadata after collection", result.values { title })
            val historic = store.execute(CollectionProjectionModel.get(
                added.key, select = selected, toVersion = added.version,
            )).values.single()
            assertEquals("metadata after collection", historic.values { title })
            assertEquals(false, historic.isDeleted)
        } finally {
            store.close()
        }
    }
}
