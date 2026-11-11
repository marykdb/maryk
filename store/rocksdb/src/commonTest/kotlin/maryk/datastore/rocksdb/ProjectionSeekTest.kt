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
import maryk.core.query.responses.statuses.AddSuccess
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.processors.readTransactionIntoValuesWithMetaData
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

class ProjectionSeekTest {
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
