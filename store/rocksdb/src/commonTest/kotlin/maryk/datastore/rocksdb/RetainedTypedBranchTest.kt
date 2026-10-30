package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.createTestDBFolder
import maryk.datastore.test.assertRetainedTypedBranchFiltering
import maryk.datastore.test.assertRetainedTypedUniqueClaims
import maryk.datastore.test.RetainedUniqueRecord
import maryk.datastore.test.StructuralFilterSource
import maryk.datastore.test.assertStructuralFilterEquivalence
import maryk.deleteFolder
import maryk.lib.extensions.compare.matchesRangePart
import maryk.test.models.ComplexModel
import kotlin.test.Test

class RetainedTypedBranchTest {
    @Test
    fun filtersIgnoreRowsBelongingToAnInactiveType() = runTest {
        val folder = createTestDBFolder("retained-typed-branch")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to ComplexModel, 2u to StructuralFilterSource), keepAllVersions = true)
        try {
            val families = store.getColumnFamilies(1u)
            assertStructuralFilterEquivalence(store)
            assertRetainedTypedBranchFiltering(store) { key, root, change ->
                val prefix = key.bytes + root
                val rows = mutableListOf<Pair<ByteArray, ByteArray>>()
                store.db.newIterator(families.table, store.defaultReadOptions).use { iterator ->
                    iterator.seek(prefix)
                    while (iterator.isValid() && iterator.key().matchesRangePart(0, prefix)) {
                        if (iterator.key().size > prefix.size) rows += iterator.key() to iterator.value()
                        iterator.next()
                    }
                }
                change()
                for ((row, value) in rows) store.db.put(families.table, row, value)
            }
        } finally { store.close(); deleteFolder(folder) }
    }

    @Test
    fun undeletingDoesNotReclaimAnInactiveUniqueLeaf() = runTest {
        val folder = createTestDBFolder("retained-typed-unique")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to RetainedUniqueRecord), keepAllVersions = true)
        try {
            val families = store.getColumnFamilies(1u)
            assertRetainedTypedUniqueClaims(store) { key, root, change ->
                val prefix = key.bytes + root
                val rows = mutableListOf<Pair<ByteArray, ByteArray>>()
                store.db.newIterator(families.table, store.defaultReadOptions).use { iterator ->
                    iterator.seek(prefix)
                    while (iterator.isValid() && iterator.key().matchesRangePart(0, prefix)) {
                        if (iterator.key().size > prefix.size) rows += iterator.key() to iterator.value()
                        iterator.next()
                    }
                }
                change()
                for ((row, value) in rows) store.db.put(families.table, row, value)
            }
        } finally { store.close(); deleteFolder(folder) }
    }
}
