package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.AtomicFailureCode
import maryk.core.query.responses.TransactionStatus
import maryk.core.query.responses.statuses.AddSuccess
import maryk.createTestDBFolder
import maryk.datastore.shared.MAX_ATOMIC_WORK_BYTES
import maryk.datastore.shared.beginTransaction
import maryk.deleteFolder
import maryk.rocksdb.DBOptions
import maryk.rocksdb.FlushOptions
import maryk.rocksdb.RocksDBException
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object HistoryBudgetModel : RootDataModel<HistoryBudgetModel>() {
    val body by string(1u)
}

class ConflictHistoryMemoryTest {
    @Test
    fun managedHistoryIsBoundedAndCallerOptionsKeepEngineDefaults() = runTest {
        for (managed in listOf(true, false)) {
            val folder = createTestDBFolder("conflict-history-options-$managed")
            DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true).use { caller ->
                val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to HistoryBudgetModel),
                    rocksDBOptions = if (managed) null else caller)
                try {
                    val targets = historyTargets(folder)
                    assertTrue(targets.isNotEmpty())
                    if (managed) assertTrue(targets.all { it == MAX_ATOMIC_WORK_BYTES.toLong() * 4 })
                    else assertTrue(targets.none { it == MAX_ATOMIC_WORK_BYTES.toLong() * 4 })
                } finally { store.close() }
                assertTrue(caller.isOwningHandle())
            }
            deleteFolder(folder)
        }
    }

    @Test
    fun flushPressureRetainsStoredHistoryAndDefiniteJournalConflicts() = runTest {
        val folder = createTestDBFolder("conflict-history-pressure")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to HistoryBudgetModel))
        try {
            val added = assertIs<AddSuccess<HistoryBudgetModel>>(store.execute(HistoryBudgetModel.add(
                HistoryBudgetModel.create { body with "before" }
            )).statuses.single())
            val ref = HistoryBudgetModel.body.ref()
            val stale = store.beginTransaction()
            stale.execute(HistoryBudgetModel.get(added.key))
            val families = store.getColumnFamilies(1u) as HistoricTableColumnFamilies
            val payload = "x".repeat(1024 * 1024)
            FlushOptions().setWaitForFlush(true).use { flush ->
                repeat(40) { index ->
                    assertIs<AddSuccess<HistoryBudgetModel>>(store.execute(HistoryBudgetModel.add(
                        HistoryBudgetModel.create { body with "$index-$payload" }
                    )).statuses.single())
                    if (index % 4 == 3) store.db.flush(flush, listOf(families.table, families.historic.table))
                }
            }
            val rejected = stale.commit()
            assertEquals(TransactionStatus.Aborted, rejected.status)
            assertEquals(AtomicFailureCode.Conflict, rejected.failure?.code)
            val fresh = store.beginTransaction()
            fresh.execute(HistoryBudgetModel.get(added.key))
            fresh.execute(HistoryBudgetModel.change(added.key.change(Change(ref with "after"))))
            assertEquals(TransactionStatus.Committed, fresh.commit().status)
            assertEquals("after", store.execute(HistoryBudgetModel.get(added.key)).values.single().values[ref])
            assertEquals("before", store.execute(HistoryBudgetModel.get(added.key, toVersion = added.version)).values.single().values[ref])
            assertTrue(store.diagnostics().memtableBytes < 96L * 1024 * 1024,
                "Flushed write buffers should be trimmed instead of retained per family")
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun nativeTransactionsStillRejectConflictsAfterOldBuffersAreTrimmed() = runTest {
        val folder = createTestDBFolder("conflict-history-native")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to HistoryBudgetModel))
        try {
            val family = store.getColumnFamilies(1u).table
            val conflict = ByteArray(16) { 100 }
            val staged = ByteArray(16) { 101 }
            store.db.put(family, conflict, byteArrayOf(1))
            Transaction(store).use { transaction ->
                assertContentEquals(byteArrayOf(1), transaction.getForUpdate(store.defaultReadOptions, family, conflict))
                store.db.put(family, conflict, byteArrayOf(2))
                val payload = ByteArray(2 * 1024 * 1024)
                FlushOptions().setWaitForFlush(true).use { flush ->
                    repeat(20) { index ->
                        val key = ByteArray(16).also { it[it.lastIndex] = index.toByte() }
                        store.db.put(family, key, payload)
                        if (index % 2 == 1) store.db.flush(flush, family)
                    }
                }
                transaction.put(family, staged, byteArrayOf(3))
                assertFailsWith<RocksDBException> { transaction.commit() }
                assertContentEquals(byteArrayOf(2), store.db.get(family, conflict))
                assertNull(store.db.get(family, staged))
            }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    private fun historyTargets(folder: String): List<Long> {
        val latest = requireNotNull(File(folder).listFiles()?.filter { it.name.startsWith("OPTIONS-") }?.maxByOrNull { it.name })
        return Regex("max_write_buffer_size_to_maintain\\s*=\\s*(-?\\d+)")
            .findAll(latest.readText()).map { it.groupValues[1].toLong() }.toList()
    }
}
