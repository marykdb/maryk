package maryk.datastore.indexeddb

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.toInstant
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.valueObject
import maryk.core.properties.types.TimePrecision.NANOS
import maryk.core.properties.types.numeric.Float64
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.datastore.indexeddb.processors.createChangeLogRowKey
import maryk.datastore.indexeddb.processors.createIndexRowKey
import maryk.datastore.indexeddb.processors.createUpdateHistoryRowKey
import maryk.datastore.indexeddb.processors.decodeHistoricSnapshot
import maryk.datastore.indexeddb.processors.encodeHistoricSnapshot
import maryk.datastore.shared.encryption.AesGcmHmacSha256EncryptionProvider
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.datastore.test.FloatingStorageValue
import maryk.datastore.test.StorageUpgradeFixture
import maryk.datastore.test.StorageUpgradeRecord
import maryk.datastore.test.legacyFloatBytes
import maryk.test.models.SimpleMarykModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime

private object RetainedEncryptedUpgradeModel : RootDataModel<RetainedEncryptedUpgradeModel>() {
    val value by number(1u, type = Float64)
    val secret by string(2u, sensitive = true, unique = true)
}

private object EmptyEncryptedUpgradeModel : RootDataModel<EmptyEncryptedUpgradeModel>() {
    val packed by valueObject(1u, dataModel = FloatingStorageValue)
    val secret by string(2u, sensitive = true)
}

private object LegacyNanosUpgradeModel : RootDataModel<LegacyNanosUpgradeModel>() {
    val stamps by list(1u, valueDefinition = DateTimeDefinition(precision = NANOS))
    val secret by string(2u, sensitive = true)
}

private object LegacyNanosDefaultsUpgradeModel : RootDataModel<LegacyNanosDefaultsUpgradeModel>() {
    val stamps by list(1u, valueDefinition = DateTimeDefinition(precision = NANOS),
        default = listOf(LocalDateTime(1970, 1, 1, 0, 0, 8, 1)))
}

class StorageFormatUpgradeTest {
    @Test
    fun resumesAfterRewritingLegacySourceSchemaDefaults() = runTest {
        installIndexedDbForTests()
        val database = "maryk-nanos-schema-upgrade-${Random.nextInt()}"
        val definitionKey = byteArrayOf(3, 0, 0, 0, 1)
        val sourceSchemaKey = sourceSchemaFormatMetadataKey(1u)
        val expected = LegacyNanosDefaultsUpgradeModel.stamps.definition.default
        val store = IndexedDbDataStore.open(database, mapOf(1u to LegacyNanosDefaultsUpgradeModel))
        val currentSchema = assertNotNull(store.byteStore.get("meta", definitionKey))
        store.close()

        // Root property -> wrapper definition -> ListDefinition. The released default is packed (seconds=8, nanos=1).
        // A current decoder can accept those same two bytes as a framed message and silently change the timestamp.
        val legacySchema = replaceEmbeddedProtoBufField(currentSchema, listOf(1, 4, 10), byteArrayOf(42, 4, 26, 2, 32, 4, 50, 2, 8, 1))
        val raw = openIndexedDbByteStore(database, setOf("meta"))
        try {
            raw.writeBatch(listOf(
                IndexedDbWriteOperation.Put("meta", definitionKey, legacySchema),
                IndexedDbWriteOperation.Delete("meta", sourceSchemaKey),
                IndexedDbWriteOperation.Delete("meta", StorageFormatMetadataKey),
            ))
            val interrupted = object : IndexedDbByteStore by raw {
                override suspend fun writeBatch(operations: List<IndexedDbWriteOperation>) {
                    raw.writeBatch(operations)
                    if (operations.any { it.key.contentEquals(sourceSchemaKey) && it is IndexedDbWriteOperation.Put }) {
                        throw IllegalStateException("Interrupted after source schema publication")
                    }
                }
            }
            assertFailsWith<IllegalStateException> {
                raw.withStartupWriteLock {
                    interrupted.upgradeIndexedDbStorageFormat(false, false, null, preparePublication = {})
                }
            }
            assertNull(raw.get("meta", StorageFormatMetadataKey))
            assertContentEquals("2".encodeToByteArray(), raw.get("meta", sourceSchemaKey))
            val rewritten = assertNotNull(raw.readStoredModelDefinition(1u, DefinitionsConversionContext()))
            assertEquals(expected, (rewritten[1u]!!.definition as ListDefinition<*, *>).default)
        } finally { raw.close() }

        val resumed = IndexedDbDataStore.open(database, mapOf(1u to LegacyNanosDefaultsUpgradeModel))
        try {
            assertContentEquals("2".encodeToByteArray(), resumed.byteStore.get("meta", StorageFormatMetadataKey))
            val rewritten = assertNotNull(resumed.byteStore.readStoredModelDefinition(1u, DefinitionsConversionContext()))
            assertEquals(expected, (rewritten[1u]!!.definition as ListDefinition<*, *>).default)
        } finally { resumed.close() }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun upgradesLegacyNanosOnlyChangeAndUpdateHistoryLogs() = runTest {
        installIndexedDbForTests()
        val database = "maryk-nanos-log-upgrade-${Random.nextInt()}"
        val provider = AesGcmHmacSha256EncryptionProvider(ByteArray(32) { it.toByte() }, ByteArray(32) { (it + 32).toByte() })
        suspend fun open() = IndexedDbDataStore.open(database, mapOf(1u to LegacyNanosUpgradeModel),
            keepAllVersions = true, keepUpdateHistoryIndex = true, fieldEncryptionProvider = provider)
        var store = open()
        try {
            val added = assertIs<AddSuccess<LegacyNanosUpgradeModel>>(store.execute(LegacyNanosUpgradeModel.add(
                LegacyNanosUpgradeModel.create { stamps with emptyList(); secret with "unchanged" }
            )).statuses.single())
            val timestamps = listOf(LocalDateTime(1969, 12, 31, 23, 59, 59, 999_999_999), LocalDateTime(2026, 1, 1, 0, 0, 0, 123))
            val changed = assertIs<ChangeSuccess<LegacyNanosUpgradeModel>>(store.execute(LegacyNanosUpgradeModel.change(
                added.key.change(Change(LegacyNanosUpgradeModel.ref { stamps } with timestamps))
            )).statuses.single())
            val sensitive = IndexedDbSensitiveFieldSupport(mapOf(1u to LegacyNanosUpgradeModel), provider)
            val current = assertNotNull(store.byteStore.get("c:1", createChangeLogRowKey(added.key.bytes, changed.version)))
            val expected = sensitive.decryptChangeLogPayloadIfNeeded(1u, added.key.bytes, changed.version, current)

            // Encode the released packed seconds/nanos pairs, independently of the new DateTime writer.
            fun field(tag: Int, payload: ByteArray): ByteArray = buildList {
                add(((tag shl 3) or 2).toByte())
                payload.size.writeVarBytes(::add)
                addAll(payload.toList())
            }.toByteArray()
            val reference = LegacyNanosUpgradeModel.stamps.ref()
            val cache = WriteCache()
            val referenceBytes = ByteArray(reference.calculateTransportByteLength(cache))
            var offset = 0
            reference.writeTransportBytes(cache) { referenceBytes[offset++] = it }
            val packed = buildList {
                for (timestamp in timestamps) {
                    val instant = timestamp.toInstant(UTC)
                    writeLegacyVarUInt64(instant.epochSeconds.toULong(), ::add)
                    instant.nanosecondsOfSecond.writeVarBytes(::add)
                }
            }.toByteArray()
            val pair = field(1, referenceBytes) + field(2, field(1, packed))
            val revision = buildList {
                add(8.toByte())
                writeLegacyVarUInt64(changed.version, ::add)
            }.toByteArray() + field(2, field(2, field(1, pair)))
            val legacy = field(1, added.key.bytes) + field(3, revision)
            val encryptedLegacy = sensitive.encryptChangeLogPayload(1u, added.key.bytes, changed.version, legacy)
            val changeKey = createChangeLogRowKey(added.key.bytes, changed.version)
            val historyKey = createUpdateHistoryRowKey(changed.version, added.key.bytes)
            store.byteStore.writeBatch(listOf(
                IndexedDbWriteOperation.Put("c:1", changeKey, encryptedLegacy),
                IndexedDbWriteOperation.Put("uh:1", historyKey, encryptedLegacy),
                IndexedDbWriteOperation.Delete("meta", StorageFormatMetadataKey),
            ))
            store.close()
            store = open()
            assertEquals(timestamps, store.execute(LegacyNanosUpgradeModel.get(added.key)).values.single().values { stamps })
            for ((name, key) in listOf("c:1" to changeKey, "uh:1" to historyKey)) {
                val converted = assertNotNull(store.byteStore.get(name, key))
                assertContentEquals(expected, sensitive.decryptChangeLogPayloadIfNeeded(1u, added.key.bytes, changed.version, converted))
            }
        } finally { store.close() }
    }

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
            val (meta, rows) = decodeHistoricSnapshot(store.byteStore.get("k:1", key.bytes)!!)
            store.byteStore.put("k:1", key.bytes, encodeHistoricSnapshot(meta, rows.map { (qualifier, value) ->
                qualifier to if (qualifier.contentEquals(RetainedEncryptedUpgradeModel.value.ref().toStorageByteArray()))
                    byteArrayOf(1) + legacyFloatBytes(-1.0) else value
            }))
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
            keepAllVersions = true, keepUpdateHistoryIndex = true, fieldEncryptionProvider = provider)
        val fixture = StorageUpgradeFixture()
        var store = open()
        try {
            fixture.addTo(store)
            suspend fun snapshot(names: List<String>) = names.associateWith { name ->
                store.byteStore.scan(name).associate { it.first.toHexString() to it.second.toHexString() }
            }
            val unchangedStores = listOf("k:1", "t:1", "u:1", "ht:1", "hu:1", "c:1", "uh:1")
            val untouched = snapshot(unchangedStores)
            for (name in listOf("t:1", "k:1", "ht:1", "u:1", "hu:1", "huk:1")) {
                val rows = store.byteStore.scan(name)
                val replacementsForRows = rows.map { (key, value) ->
                    var target = key
                    var payload = value
                    if (name == "t:1") {
                        val record = objectKeyBytesFromScopedRowKey(key)
                        val (qualifier, legacy) = fixture.legacyCell(tableQualifierFromRowKey(key, record), value)
                        target = createTableRowKey(record, qualifier)
                        payload = legacy
                    } else if (name == "k:1" || name == "ht:1") {
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
            for (name in listOf("c:1", "uh:1")) {
                for ((key, value) in store.byteStore.scan(name)) {
                    store.byteStore.put(name, key, fixture.legacyChangeLog(value))
                }
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
            store = IndexedDbDataStore.open(database, mapOf(2u to SimpleMarykModel), keepAllVersions = true, keepUpdateHistoryIndex = true)
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

/** Standard unsigned varint; converting signed seconds to ULong retains the released two's-complement bits. */
private fun writeLegacyVarUInt64(value: ULong, writer: (Byte) -> Unit) {
    var remaining = value
    while (remaining >= 128uL) {
        writer(((remaining % 128uL).toInt() or 0x80).toByte())
        remaining /= 128uL
    }
    writer(remaining.toByte())
}

/** Replace one known embedded schema payload while preserving all surrounding field identities. */
private fun replaceEmbeddedProtoBufField(bytes: ByteArray, path: List<Int>, replacement: ByteArray): ByteArray {
    if (path.isEmpty()) return replacement
    var offset = 0
    var replaced = false
    val result = mutableListOf<Byte>()
    while (offset < bytes.size) {
        val start = offset
        val key = initIntByVar { bytes[offset++] }
        when (key and 7) {
            0 -> while (bytes[offset++].toInt() and 0x80 != 0) {
                // Skip the remaining bytes of this unrelated varint field.
            }
            1 -> offset += 8
            2 -> {
                val keyEnd = offset
                val size = initIntByVar { bytes[offset++] }
                val end = offset + size
                if (key ushr 3 == path.first()) {
                    val converted = replaceEmbeddedProtoBufField(bytes.copyOfRange(offset, end), path.drop(1), replacement)
                    result.addAll(bytes.copyOfRange(start, keyEnd).toList())
                    converted.size.writeVarBytes(result::add)
                    result.addAll(converted.toList())
                    replaced = true
                } else result.addAll(bytes.copyOfRange(start, end).toList())
                offset = end
                continue
            }
            5 -> offset += 4
            else -> error("Unexpected fixture wire type")
        }
        result.addAll(bytes.copyOfRange(start, offset).toList())
    }
    check(replaced) { "Fixture schema field path is missing" }
    return result.toByteArray()
}
