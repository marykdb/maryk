package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.createTestDBFolder
import maryk.datastore.rocksdb.metadata.readStoreMetaFile
import maryk.datastore.shared.StoredPropertyDeleteMarker
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.datastore.shared.migration.legacyIncrementingMapProofKey
import maryk.deleteFolder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object LegacyCountRecord : RootDataModel<LegacyCountRecord>() {
    val entries by incrementingMap(1u, keyNumberDescriptor = UInt32,
        valueDefinition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = StringDefinition()))
}

class LegacyCollectionCountUpgradeTest {
    @Test
    fun creationVersionProvesZeroCountPresentWithoutHistory() = runTest {
        val folder = createTestDBFolder("legacy-creation-zero-count")
        val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to LegacyCountRecord))
        try {
            val initial = LegacyCountRecord.create { entries with mapOf(1u to emptyMap()) }
            val added = assertIs<AddSuccess<LegacyCountRecord>>(store.execute(LegacyCountRecord.add(initial)).statuses.single())
            val qualifier = LegacyCountRecord.ref { entries at 1u }.toStorageByteArray()
            val columns = store.getColumnFamilies(1u)
            val before = store.db.get(columns.table, added.key.bytes + qualifier)
            store.db.delete(store.transactionMetadataHandle, StorageFormatMetadataKey)
            store.upgradeRocksStorageFormat(folder, readStoreMetaFile(folder), preparePublication = {})
            assertContentEquals(before, store.db.get(columns.table, added.key.bytes + qualifier))
            assertEquals(initial, store.execute(LegacyCountRecord.get(added.key)).values.single().values)
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun exactHistoricDeletionProvesRetainedCurrentZeroIsDeleted() = exerciseRetainedDeletion(true)

    @Test
    fun ambiguousZeroStopsBeforeSourceMutationAndResumesAfterAuthoritativeRepair() = exerciseRetainedDeletion(false)

    private fun exerciseRetainedDeletion(history: Boolean) = runTest {
        val folder = createTestDBFolder("legacy-retained-zero-$history")
        suspend fun open() = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to LegacyCountRecord), keepAllVersions = history)
        var store = open()
        try {
            val initial = LegacyCountRecord.create { entries with mapOf(1u to mapOf("x" to "a"), 2u to mapOf("y" to "b")) }
            val added = assertIs<AddSuccess<LegacyCountRecord>>(store.execute(LegacyCountRecord.add(initial)).statuses.single())
            store.execute(LegacyCountRecord.change(added.key.change(Change(LegacyCountRecord.ref { entries at 1u } with null))))
            val qualifier = LegacyCountRecord.ref { entries at 1u }.toStorageByteArray()
            val columns = store.getColumnFamilies(1u)
            val key = added.key.bytes + qualifier
            val deleted = store.db.get(columns.table, key)!!
            val released = deleted.copyOfRange(0, 8) + byteArrayOf(0)
            store.db.put(columns.table, key, released)
            if (!history) {
                // This fixture has an authoritative complete allocation lifecycle.
                // Keep the count-presence ambiguity independent of allocation recovery.
                store.db.put(columns.model, legacyIncrementingMapProofKey(1u, added.key.bytes), byteArrayOf(1))
            }
            store.db.delete(store.transactionMetadataHandle, StorageFormatMetadataKey)
            if (!history) {
                fun snapshot() = store.db.newIterator(columns.table, store.sequentialReadOptions).use { iterator ->
                    buildMap {
                        iterator.seekToFirst()
                        while (iterator.isValid()) {
                            put(iterator.key().toHexString(), iterator.value().toHexString())
                            iterator.next()
                        }
                    }
                }
                val before = snapshot()
                val failure = assertFailsWith<MigrationException> {
                    store.upgradeRocksStorageFormat(folder, readStoreMetaFile(folder), preparePublication = {})
                }
                assertTrue(failure.message.orEmpty().contains("qualifier=${qualifier.toHexString()}"))
                assertContentEquals(released, store.db.get(columns.table, key))
                assertEquals(before, snapshot())
                assertNull(store.db.get(store.transactionMetadataHandle, StorageFormatMetadataKey))
                // This test has the authoritative deletion request; restore its explicit marker.
                store.db.put(columns.table, key, deleted.copyOfRange(0, 8) + StoredPropertyDeleteMarker)
            }
            store.upgradeRocksStorageFormat(folder, readStoreMetaFile(folder), preparePublication = {})
            assertContentEquals(deleted.copyOfRange(0, 8) + StoredPropertyDeleteMarker, store.db.get(columns.table, key))
            store.close()
            store = open()
            assertEquals(mapOf(2u to mapOf("y" to "b")), store.execute(LegacyCountRecord.get(added.key)).values.single().values { entries })
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }
}
