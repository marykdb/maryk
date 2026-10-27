package maryk.datastore.rocksdb

import kotlin.test.assertIs

import maryk.datastore.test.legacyFloatBytes

import maryk.datastore.shared.encryption.AesGcmHmacSha256EncryptionProvider

import maryk.core.query.responses.statuses.AddSuccess

import maryk.core.query.requests.get

import maryk.core.query.requests.add

import maryk.core.properties.types.numeric.Float64

import maryk.core.properties.definitions.string

import maryk.core.properties.definitions.number

import maryk.core.models.RootDataModel

import kotlinx.coroutines.test.runTest
import maryk.core.models.migration.StorageFormat
import maryk.createTestDBFolder
import maryk.deleteFolder
import maryk.datastore.rocksdb.processors.helpers.createIndexKey
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.datastore.test.StorageUpgradeFixture
import maryk.test.models.SimpleMarykModel
import maryk.datastore.shared.migration.StorageMaintenanceMetadataKey
import maryk.datastore.test.StorageUpgradeRecord
import maryk.rocksdb.ColumnFamilyHandle
import kotlin.test.assertNull
import maryk.datastore.rocksdb.model.modelUpdateHistoryBackfillCompleteKey
import maryk.datastore.rocksdb.model.modelReplicationTombstoneBackfillCompleteKey
import maryk.datastore.rocksdb.model.modelTransactionHlcWatermarkRepairCompleteKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

private object RetainedEncryptedUpgradeModel : RootDataModel<RetainedEncryptedUpgradeModel>() {
    val value by number(1u, type = Float64)
    val secret by string(2u, sensitive = true, unique = true)
}

class StorageFormatUpgradeTest {
    @Test
    fun upgradesRetainedPlainFloatsWithoutDecryptingUnchangedSensitiveFields() = runTest {
        val folder = createTestDBFolder("retained-encrypted-upgrade")
        val provider = AesGcmHmacSha256EncryptionProvider(ByteArray(32) { it.toByte() }, ByteArray(32) { (it + 32).toByte() })
        suspend fun openModel() = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to RetainedEncryptedUpgradeModel),
            keepAllVersions = false, fieldEncryptionProvider = provider)
        var store = openModel()
        try {
            val key = assertIs<AddSuccess<RetainedEncryptedUpgradeModel>>(store.execute(RetainedEncryptedUpgradeModel.add(
                RetainedEncryptedUpgradeModel.create { value with -1.0; secret with "unchanged" }
            )).statuses.single()).key
            val families = store.getColumnFamilies(1u)
            val valueKey = key.bytes + RetainedEncryptedUpgradeModel.value.ref().toStorageByteArray()
            val secretKey = key.bytes + RetainedEncryptedUpgradeModel.secret.ref().toStorageByteArray()
            val ciphertext = store.db.get(families.table, secretKey)!!
            val old = store.db.get(families.table, valueKey)!!
            store.db.put(families.table, valueKey, old.copyOfRange(0, 8) + byteArrayOf(1) + legacyFloatBytes(-1.0))
            store.db.delete(store.transactionMetadataHandle, StorageFormatMetadataKey)
            store.close()
            store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(2u to SimpleMarykModel), keepAllVersions = false)
            store.close()
            store = openModel()
            val values = store.execute(RetainedEncryptedUpgradeModel.get(key)).values.single().values
            assertEquals(-1.0, values { value })
            assertEquals("unchanged", values { secret })
            assertContentEquals(ciphertext, store.db.get(store.getColumnFamilies(1u).table, secretKey))
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    @Test
    fun upgradesReleasedValuesAndIndexesOnce() = runTest {
        val folder = createTestDBFolder("storage-format-upgrade")
        suspend fun open() = RocksDBDataStore.open(relativePath = folder,
            dataModelsById = mapOf(1u to StorageUpgradeRecord), keepAllVersions = true, keepUpdateHistoryIndex = true)
        val fixture = StorageUpgradeFixture()
        val retiredMarkers = listOf(modelUpdateHistoryBackfillCompleteKey, modelReplicationTombstoneBackfillCompleteKey, modelTransactionHlcWatermarkRepairCompleteKey)
        var store = open()
        try {
            fixture.addTo(store)
            val families = store.getColumnFamilies(1u) as HistoricTableColumnFamilies
            val untouched = listOf(families.keys, families.table, families.unique, families.historic.table, families.historic.unique)
                .map { snapshot(store, it) }
            for ((handle, historic) in listOf(families.table to false, families.historic.table to true)) {
                val rows = snapshot(store, handle).map { (key, value) ->
                    val bytes = key.hexToByteArray()
                    val payload = value.hexToByteArray()
                    if (bytes.size <= StorageUpgradeRecord.Meta.keyByteSize) return@map Triple(bytes, bytes, payload)
                    val end = bytes.size - if (historic) 8 else 0
                    val start = StorageUpgradeRecord.Meta.keyByteSize
                    val valueStart = if (historic) 0 else 8
                    val (qualifier, legacy) = fixture.legacyCell(bytes.copyOfRange(start, end), payload.copyOfRange(valueStart, payload.size))
                    Triple(bytes, bytes.copyOfRange(0, start) + qualifier + bytes.copyOfRange(end, bytes.size), payload.copyOfRange(0, valueStart) + legacy)
                }
                rows.forEach { store.db.delete(handle, it.first) }
                rows.forEach { store.db.put(handle, it.second, it.third) }
            }
            for ((handle, historic) in listOf(families.unique to false, families.historic.unique to true)) {
                val rows = snapshot(store, handle).map { (key, value) ->
                    val bytes = key.hexToByteArray()
                    val end = bytes.size - if (historic) 8 else 0
                    Triple(bytes, fixture.legacyUnique(bytes.copyOfRange(0, end)) + bytes.copyOfRange(end, bytes.size), value.hexToByteArray())
                }
                rows.forEach { store.db.delete(handle, it.first) }
                rows.forEach { store.db.put(handle, it.second, it.third) }
            }
            val indexes = StorageUpgradeRecord.Meta.indexes!!
            val fixedPrefix = createIndexKey(indexes.last().referenceStorageByteArray.bytes, byteArrayOf()).toHexString()
            val fixed = snapshot(store, families.index).filterKeys { it.startsWith(fixedPrefix) }
            for ((handle, historic) in listOf(families.index to false, families.historic.index to true)) {
                val replacements = fixture.records.flatMapIndexed { position, record ->
                    indexes.map { index ->
                        createIndexKey(index.referenceStorageByteArray.bytes,
                            index.toStorageByteArrayForIndex(record, fixture.keys[position].bytes)!!).toHexString() to
                            createIndexKey(index.referenceStorageByteArray.bytes,
                                fixture.legacyEntry(index, record, fixture.keys[position].bytes))
                    }
                }.toMap()
                val rows = snapshot(store, handle)
                rows.forEach { (key, value) ->
                    val bytes = key.hexToByteArray()
                    val end = bytes.size - if (historic) 8 else 0
                    val legacy = replacements.getValue(bytes.copyOfRange(0, end).toHexString()) + bytes.copyOfRange(end, bytes.size)
                    store.db.delete(handle, bytes)
                    store.db.put(handle, legacy, value.hexToByteArray())
                }
            }
            store.db.delete(families.model, StorageMaintenanceMetadataKey)
            store.db.delete(store.transactionMetadataHandle, StorageFormatMetadataKey)
            store.close()
            // Retained models must be upgraded even when not configured on this open.
            store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(2u to SimpleMarykModel), keepAllVersions = true, keepUpdateHistoryIndex = true)
            store.close()
            store = open()
            fixture.verify(store)
            val upgraded = store.getColumnFamilies(1u) as HistoricTableColumnFamilies
            assertEquals(untouched, listOf(upgraded.keys, upgraded.table, upgraded.unique, upgraded.historic.table, upgraded.historic.unique)
                .map { snapshot(store, it) })
            assertEquals(fixed, snapshot(store, upgraded.index).filterKeys { it.startsWith(fixedPrefix) })
            assertContentEquals(StorageFormat.CURRENT.toString().encodeToByteArray(), store.db.get(store.transactionMetadataHandle, StorageFormatMetadataKey))
            val converted = snapshot(store, upgraded.index)
            retiredMarkers.forEach { store.db.delete(upgraded.model, it) }
            store.close()
            store = open()
            fixture.verify(store)
            assertEquals(converted, snapshot(store, store.getColumnFamilies(1u).index))
            retiredMarkers.forEach { assertNull(store.db.get(store.getColumnFamilies(1u).model, it)) }
        } finally {
            store.close()
            deleteFolder(folder)
        }
    }

    private fun snapshot(store: RocksDBDataStore, handle: ColumnFamilyHandle) = buildMap {
        store.db.newIterator(handle).use { iterator ->
            iterator.seekToFirst()
            while (iterator.isValid()) {
                put(iterator.key().toHexString(), iterator.value().toHexString())
                iterator.next()
            }
        }
    }
}
