package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.metadata.readStoreMetaFile
import maryk.datastore.shared.incrementingMapWatermarkPrefix
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.datastore.shared.migration.legacyIncrementingMapProofKey
import maryk.deleteFolder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private object LegacyAllocatedRecord : RootDataModel<LegacyAllocatedRecord>() {
    val entries by incrementingMap(1u, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition())
}

class LegacyIncrementingMapUpgradeTest {
    @Test
    fun recoversHistoryMaximaAndRejectsEditedRecordsWithoutAllocationEvidence() = runTest {
        for (history in listOf(false, true)) {
            val folder = createTestDBFolder("legacy-allocation-recovery-$history")
            suspend fun open() = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to LegacyAllocatedRecord), keepAllVersions = history)
            var store = open()
            try {
                val added = assertIs<AddSuccess<LegacyAllocatedRecord>>(store.execute(LegacyAllocatedRecord.add(
                    LegacyAllocatedRecord.create { entries with mapOf(1u to "first", 10u to "removed") },
                )).statuses.single())
                assertIs<ChangeSuccess<LegacyAllocatedRecord>>(store.execute(LegacyAllocatedRecord.change(added.key.change(
                    Change(LegacyAllocatedRecord.ref { entries } with mapOf(1u to "first")),
                ))).statuses.single())
                val columns = store.getColumnFamilies(1u)
                val prefix = incrementingMapWatermarkPrefix(1u, added.key.bytes)
                val counter = prefix + LegacyAllocatedRecord.entries.ref().toStorageByteArray()
                val authoritativeMaximum = store.db.get(columns.model, counter)!!
                store.db.newIterator(columns.model, store.sequentialReadOptions).use { iterator ->
                    iterator.seek(prefix)
                    while (iterator.isValid() && iterator.key().toHexString().startsWith(prefix.toHexString())) {
                        store.db.delete(columns.model, iterator.key())
                        iterator.next()
                    }
                }
                store.db.delete(columns.model, legacyIncrementingMapProofKey(1u, added.key.bytes))
                store.db.delete(store.transactionMetadataHandle, StorageFormatMetadataKey)
                if (!history) {
                    val source = store.db.get(columns.table, added.key.bytes + LegacyAllocatedRecord.entries.ref().toStorageByteArray())
                    val failure = assertFailsWith<MigrationException> {
                        store.upgradeRocksStorageFormat(folder, readStoreMetaFile(folder), preparePublication = {})
                    }
                    assertTrue(failure.message.orEmpty().contains("record=${added.key.bytes.toHexString()}"))
                    assertContentEquals(source, store.db.get(columns.table, added.key.bytes + LegacyAllocatedRecord.entries.ref().toStorageByteArray()))
                    // The test's actual request history authoritatively recovers this lifetime.
                    store.db.put(columns.model, counter, authoritativeMaximum)
                    store.db.put(columns.model, legacyIncrementingMapProofKey(1u, added.key.bytes), byteArrayOf(1))
                }
                store.upgradeRocksStorageFormat(folder, readStoreMetaFile(folder), preparePublication = {})
                assertContentEquals(authoritativeMaximum, store.db.get(columns.model, counter))
                store.close()
                store = open()
                assertIs<ChangeSuccess<LegacyAllocatedRecord>>(store.execute(LegacyAllocatedRecord.change(added.key.change(
                    IncMapChange(LegacyAllocatedRecord.ref { entries }.change(addValues = listOf("next"))),
                ))).statuses.single())
                assertEquals(mapOf(1u to "first", 11u to "next"), store.execute(LegacyAllocatedRecord.get(added.key)).values.single().values { entries })
            } finally {
                store.close()
                deleteFolder(folder)
            }
        }
    }
}
