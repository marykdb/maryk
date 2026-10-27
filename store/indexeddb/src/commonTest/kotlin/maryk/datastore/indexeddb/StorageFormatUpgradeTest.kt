package maryk.datastore.indexeddb

import maryk.datastore.test.FloatingStorageValue

import maryk.core.properties.definitions.valueObject

import kotlin.test.assertIs

import kotlin.test.assertContentEquals

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
import maryk.core.extensions.bytes.initIntByVar
import maryk.datastore.indexeddb.processors.createIndexRowKey
import maryk.datastore.indexeddb.processors.decodeHistoricSnapshot
import maryk.datastore.indexeddb.processors.encodeHistoricSnapshot
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.datastore.test.StorageUpgradeFixture
import maryk.test.models.SimpleMarykModel
import maryk.datastore.test.StorageUpgradeRecord
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

private object RetainedEncryptedUpgradeModel : RootDataModel<RetainedEncryptedUpgradeModel>() {
    val value by number(1u, type = Float64)
    val secret by string(2u, sensitive = true, unique = true)
}

private object EmptyEncryptedUpgradeModel : RootDataModel<EmptyEncryptedUpgradeModel>() {
    val packed by valueObject(1u, dataModel = FloatingStorageValue)
    val secret by string(2u, sensitive = true)
}

class StorageFormatUpgradeTest {
    @Test
    fun upgradesRetainedPlainFloatsWithoutDecryptingUnchangedSensitiveFields() = runTest {
        installIndexedDbForTests()
        val database = "maryk-retained-encrypted-${Random.nextInt()}"
        val provider = AesGcmHmacSha256EncryptionProvider(ByteArray(32) { it.toByte() }, ByteArray(32) { (it + 32).toByte() })
        suspend fun openModel() = IndexedDbDataStore.open(database, mapOf(1u to RetainedEncryptedUpgradeModel),
            keepAllVersions = false, fieldEncryptionProvider = provider)
        var store = openModel()
        try {
            val key = assertIs<AddSuccess<RetainedEncryptedUpgradeModel>>(store.execute(RetainedEncryptedUpgradeModel.add(
                RetainedEncryptedUpgradeModel.create { value with -1.0; secret with "unchanged" }
            )).statuses.single()).key
            val valueKey = createTableRowKey(key.bytes, RetainedEncryptedUpgradeModel.value.ref().toStorageByteArray())
            val secretKey = createTableRowKey(key.bytes, RetainedEncryptedUpgradeModel.secret.ref().toStorageByteArray())
            val ciphertext = store.byteStore.get("t:1", secretKey)!!
            store.byteStore.put("t:1", valueKey, byteArrayOf(1) + legacyFloatBytes(-1.0))
            store.byteStore.delete("meta", StorageFormatMetadataKey)
            store.close()
            store = IndexedDbDataStore.open(database, mapOf(2u to SimpleMarykModel), keepAllVersions = false)
            store.close()
            store = openModel()
            val values = store.execute(RetainedEncryptedUpgradeModel.get(key)).values.single().values
            assertEquals(-1.0, values { value })
            assertEquals("unchanged", values { secret })
            assertContentEquals(ciphertext, store.byteStore.get("t:1", secretKey))
        } finally { store.close() }
    }

    @Test
    fun upgradesReleasedValuesAndIndexesOnce() = runTest {
        installIndexedDbForTests()
        val database = "maryk-storage-upgrade-${Random.nextInt()}"
        val provider = AesGcmHmacSha256EncryptionProvider(ByteArray(32) { it.toByte() }, ByteArray(32) { (it + 32).toByte() })
        suspend fun open() = IndexedDbDataStore.open(database, mapOf(1u to StorageUpgradeRecord, 3u to EmptyEncryptedUpgradeModel),
            keepAllVersions = true, fieldEncryptionProvider = provider)
        val fixture = StorageUpgradeFixture()
        var store = open()
        try {
            fixture.addTo(store)
            suspend fun snapshot(names: List<String>) = names.associateWith { name ->
                store.byteStore.scan(name).associate { it.first.toHexString() to it.second.toHexString() }
            }
            val unchangedStores = listOf("k:1", "t:1", "u:1", "ht:1", "hu:1")
            val untouched = snapshot(unchangedStores)
            for (name in listOf("t:1", "ht:1", "u:1", "hu:1", "huk:1")) {
                val rows = store.byteStore.scan(name)
                val replacementsForRows = rows.map { (key, value) ->
                    var target = key
                    var payload = value
                    if (name == "t:1") {
                        val record = objectKeyBytesFromScopedRowKey(key)
                        val (qualifier, legacy) = fixture.legacyCell(tableQualifierFromRowKey(key, record), value)
                        target = createTableRowKey(record, qualifier)
                        payload = legacy
                    } else if (name == "ht:1") {
                        val (meta, cells) = decodeHistoricSnapshot(value)
                        payload = encodeHistoricSnapshot(meta, cells.map { fixture.legacyCell(it.first, it.second) })
                    } else {
                        var start = 0
                        if (name == "huk:1") {
                            val length = initIntByVar { key[start++] }
                            start += length
                        }
                        val end = key.size - if (name == "u:1") 0 else 8
                        target = key.copyOfRange(0, start) + fixture.legacyUnique(key.copyOfRange(start, end)) + key.copyOfRange(end, key.size)
                        if (name == "huk:1") payload = target.copyOfRange(start, target.size)
                    }
                    Triple(key, target, payload)
                }
                store.byteStore.writeBatch(replacementsForRows.map { IndexedDbWriteOperation.Delete(name, it.first) } +
                    replacementsForRows.map { IndexedDbWriteOperation.Put(name, it.second, it.third) })
            }
            for ((key, value) in store.byteStore.scan("c:1")) {
                store.byteStore.put("c:1", key, fixture.legacyChangeLog(value))
            }
            val replacements = fixture.records.flatMapIndexed { position, record ->
                StorageUpgradeRecord.Meta.indexes!!.map { index ->
                    val reference = index.referenceStorageByteArray.bytes
                    createIndexRowKey(reference, index.toStorageByteArrayForIndex(record, fixture.keys[position].bytes)!!).toHexString() to
                        createIndexRowKey(reference, fixture.legacyEntry(index, record, fixture.keys[position].bytes))
                }
            }.toMap()
            for (name in listOf("i:1", "hi:1", "hik:1")) {
                val rows = store.byteStore.scan(name)
                val operations = rows.flatMap { (key, value) ->
                    var start = 0
                    if (name == "hik:1") {
                        val length = initIntByVar { key[start++] }
                        start += length
                    }
                    val end = key.size - if (name != "i:1") 8 else 0
                    val legacy = key.copyOfRange(0, start) + replacements.getValue(key.copyOfRange(start, end).toHexString()) + key.copyOfRange(end, key.size)
                    listOf(IndexedDbWriteOperation.Delete(name, key), IndexedDbWriteOperation.Put(name, legacy,
                        if (name == "hik:1") legacy.copyOfRange(start, legacy.size) else value))
                }
                // Delete first: the fixture also exercises unchanged fixed index identities.
                store.byteStore.writeBatch(operations.filterIsInstance<IndexedDbWriteOperation.Delete>() + operations.filterIsInstance<IndexedDbWriteOperation.Put>())
            }
            store.byteStore.delete("meta", StorageFormatMetadataKey)
            store.close()
            store = IndexedDbDataStore.open(database, mapOf(2u to SimpleMarykModel), keepAllVersions = true)
            store.close()
            store = open()
            fixture.verify(store)
            assertEquals(untouched, snapshot(unchangedStores))
            val converted = snapshot(listOf("i:1", "hi:1", "hik:1"))
            store.close()
            store = open()
            fixture.verify(store)
            assertEquals(converted, snapshot(listOf("i:1", "hi:1", "hik:1")))
        } finally {
            store.close()
        }
    }
}
