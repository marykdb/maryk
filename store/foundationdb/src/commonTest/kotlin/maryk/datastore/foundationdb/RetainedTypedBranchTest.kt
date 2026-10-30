package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.test.assertRetainedTypedBranchFiltering
import maryk.datastore.test.assertRetainedTypedUniqueClaims
import maryk.datastore.test.RetainedUniqueRecord
import maryk.datastore.test.StructuralFilterSource
import maryk.datastore.test.assertStructuralFilterEquivalence
import maryk.foundationdb.Range
import maryk.test.models.ComplexModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class RetainedTypedBranchTest {
    @Test
    fun filtersIgnoreRowsBelongingToAnInactiveType() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "retained-typed-branch-${Random.nextInt()}"),
            dataModelsById = mapOf(1u to ComplexModel, 2u to StructuralFilterSource), keepAllVersions = true)
        try {
            val directories = store.getTableDirs(1u)
            assertStructuralFilterEquivalence(store)
            assertRetainedTypedBranchFiltering(store) { key, root, change ->
                val prefix = packKey(directories.tablePrefix, key.bytes) + root
                val rows = store.tc.run { transaction ->
                    val result = mutableListOf<Pair<ByteArray, ByteArray>>()
                    val iterator = transaction.getRange(Range.startsWith(prefix)).iterator()
                    while (iterator.hasNext()) {
                        val row = iterator.nextBlocking()
                        if (row.key.size > prefix.size) result += row.key to row.value
                    }
                    result
                }
                change()
                store.tc.run { transaction -> for ((row, value) in rows) transaction.set(row, value) }
            }
        } finally { store.close() }
    }

    @Test
    fun undeletingDoesNotReclaimAnInactiveUniqueLeaf() = runTest(timeout = 3.minutes) {
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "retained-typed-unique-${Random.nextInt()}"),
            dataModelsById = mapOf(1u to RetainedUniqueRecord), keepAllVersions = true)
        try {
            val directories = store.getTableDirs(1u)
            assertRetainedTypedUniqueClaims(store) { key, root, change ->
                val prefix = packKey(directories.tablePrefix, key.bytes) + root
                val rows = store.tc.run { transaction ->
                    val result = mutableListOf<Pair<ByteArray, ByteArray>>()
                    val iterator = transaction.getRange(Range.startsWith(prefix)).iterator()
                    while (iterator.hasNext()) {
                        val row = iterator.nextBlocking()
                        if (row.key.size > prefix.size) result += row.key to row.value
                    }
                    result
                }
                change()
                store.tc.run { transaction -> for ((row, value) in rows) transaction.set(row, value) }
            }
        } finally { store.close() }
    }
}
