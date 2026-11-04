package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.extensions.bytes.toByteArray
import maryk.core.properties.types.Key
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.processors.helpers.readCreationVersion
import maryk.deleteFolder
import maryk.test.models.Log
import kotlin.test.Test
import kotlin.test.assertEquals

class CreationVersionReadTest {
    @Test
    fun creationVersionReadsRetainSnapshotOrderDuplicatesAndHistoricFallback() = runTest {
        val folder = createTestDBFolder("multi-get-creation-versions")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to Log), keepAllVersions = true)
        try {
            val families = store.getColumnFamilies(1u) as HistoricTableColumnFamilies
            val keys = (0..260).map(::key)
            for ((index, key) in keys.withIndex()) {
                if (index % 5 == 0) continue
                store.db.put(families.keys, key.bytes, 2uL.toByteArray())
                when (index % 3) {
                    0 -> store.db.put(families.historic.table, key.bytes, 1uL.toByteArray())
                    1 -> store.db.put(families.historic.table, key.bytes, byteArrayOf(1))
                }
            }
            val requested = keys + listOf(keys[2], keys[1], key(999))
            store.createReadContext().use { context ->
                store.db.put(families.keys, keys[1].bytes, 99uL.toByteArray())
                store.db.put(families.historic.table, keys[3].bytes, 99uL.toByteArray())
                context.withAccessor(store) { accessor ->
                    fun readVersions(keys: List<Key<Log>>, toVersion: ULong? = null) = keys.map { key ->
                        key to readCreationVersion(accessor, families, context.defaultReadOptions, key.bytes, toVersion)
                    }
                    val historical = readVersions(requested, 10uL)
                    assertEquals(requested, historical.map { it.first })
                    assertEquals(keys.indices.map { index ->
                        when {
                            index % 5 == 0 -> null
                            index % 3 == 0 -> 1uL
                            else -> 2uL
                        }
                    } + listOf(2uL, 2uL, null), historical.map { it.second })
                    val current = readVersions(requested)
                    assertEquals(keys.indices.map { if (it % 5 == 0) null else 2uL } + listOf(2uL, 2uL, null), current.map { it.second })
                    assertEquals(emptyList(), readVersions(emptyList()))
                    assertEquals(listOf(keys[1] to 2uL), readVersions(listOf(keys[1])))
                }
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    private fun key(index: Int) = Key<Log>(ByteArray(Log.Meta.keyByteSize).also {
        it[it.lastIndex - 1] = (index shr 8).toByte()
        it[it.lastIndex] = index.toByte()
    })
}
