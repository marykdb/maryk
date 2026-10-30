package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.extensions.bytes.writeBytes
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.types.numeric.Float64
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.foundationdb.processors.helpers.decodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.datastore.test.StorageUpgradeFixture
import maryk.datastore.test.legacyFloatBytes
import maryk.test.models.SimpleMarykModel
import maryk.datastore.shared.migration.StorageMaintenanceMetadataKey
import maryk.datastore.test.StorageUpgradeRecord
import maryk.foundationdb.Range
import kotlin.random.Random
import kotlin.test.assertNull
import maryk.datastore.foundationdb.model.modelUpdateHistoryBackfillCompleteKey
import maryk.datastore.foundationdb.model.modelReplicationTombstoneBackfillCompleteKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private object LargeStorageUpgradeRecord : RootDataModel<LargeStorageUpgradeRecord>() {
    val entries by map(1u, keyDefinition = NumberDefinition(type = Float64), valueDefinition = StringDefinition())
}

class StorageFormatUpgradeTest {
    @Test
    fun fullStageBatchFitsNativeLimitsAndAllChunksAreRemoved() = runTest {
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "full-stage-batch-${Random.nextInt()}"),
            dataModelsById = mapOf(1u to SimpleMarykModel))
        try {
            val chunks = store.tc.run { transaction ->
                store.rootDirectory.createOrOpen(transaction, listOf("__meta__", "storage_format_chunks")).awaitResult().pack()
            }
            val metadata = FoundationDBStorageFormatMetadata(chunks)
            val entries = (1..64).map { sequence ->
                val suffix = ByteArray(8).also { bytes ->
                    var offset = 0
                    sequence.toULong().writeBytes(writer = { bytes[offset++] = it })
                }
                val key = "maryk.storage/stage/".encodeToByteArray() + suffix
                // A full 100,000-byte cell plus two 10,000-byte identities and the entry header.
                key to ByteArray(120_012) { (it + sequence).toByte() }
            }
            store.tc.run { transaction ->
                entries.forEach { (key, value) -> metadata.write(transaction, store.storageFormatPrefix, key, value) }
            }
            for ((key, value) in entries) {
                store.tc.run { transaction ->
                    assertContentEquals(value, metadata.read(transaction, key,
                        transaction.get(store.storageFormatPrefix + key).awaitResult()))
                }
            }
            store.tc.run { transaction ->
                entries.forEach { (key, _) -> metadata.write(transaction, store.storageFormatPrefix, key, null) }
            }
            store.tc.run { transaction ->
                assertTrue(!transaction.getRange(Range.startsWith(chunks), 1, false).iterator().hasNext())
                entries.forEach { (key, _) -> assertNull(transaction.get(store.storageFormatPrefix + key).awaitResult()) }
            }
        } finally {
            store.close()
        }
    }

    @Test
    fun arbitraryTargetKeysCannotMasqueradeAsChunkManifests() = runTest {
        val store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "metadata-marker-collision-${Random.nextInt()}"),
            dataModelsById = mapOf(1u to SimpleMarykModel))
        try {
            val chunks = store.tc.run { transaction ->
                store.rootDirectory.createOrOpen(transaction, listOf("__meta__", "storage_format_chunks")).awaitResult().pack()
            }
            val metadata = FoundationDBStorageFormatMetadata(chunks)
            // A valid 12-byte application primary key can have exactly the manifest bytes.
            val recordKey = byteArrayOf(-1, 77, 75, 83, 70, 67, 72, 1, 0, 1, -122, -95)
            val target = "maryk.storage/target/".encodeToByteArray() + ByteArray(32)
            store.tc.run { transaction -> metadata.write(transaction, store.storageFormatPrefix, target, recordKey) }
            store.tc.run { transaction ->
                val persisted = transaction.get(store.storageFormatPrefix + target).awaitResult()
                assertContentEquals(recordKey, metadata.read(transaction, target, persisted))
            }
        } finally {
            store.close()
        }
    }

    @Test
    fun stagesNearLimitValuesWhoseFloatingMapQualifiersChange() = runTest {
        val directory = listOf("maryk", "large-storage-upgrade-${Random.nextInt()}")
        suspend fun open() = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory,
            dataModelsById = mapOf(1u to LargeStorageUpgradeRecord), keepAllVersions = true)
        val values = LargeStorageUpgradeRecord.create { entries with mapOf(-2.0 to "a".repeat(99_980)) }
        var store = open()
        try {
            val added = assertIs<AddSuccess<LargeStorageUpgradeRecord>>(store.execute(LargeStorageUpgradeRecord.add(values)).statuses.single())
            val dirs = store.getTableDirs(1u) as HistoricTableDirectories
            val qualifier = LargeStorageUpgradeRecord.ref { entries at -2.0 }.toStorageByteArray()
            val legacyQualifier = qualifier.copyOf().apply { legacyFloatBytes(-2.0).copyInto(this, size - 8) }
            store.tc.run { transaction ->
                for ((prefix, historic) in listOf(dirs.tablePrefix to false, dirs.historicTablePrefix to true)) {
                    val rows = transaction.getRange(Range.startsWith(prefix + added.key.bytes)).iterator()
                    while (rows.hasNext()) {
                        val row = rows.nextBlocking()
                        val end = row.key.size - if (historic) 9 else 0
                        if (end <= prefix.size + added.key.bytes.size) continue
                        val start = prefix.size + added.key.bytes.size
                        val actual = if (historic) decodeZeroFreeUsing01(row.key, start, end - start) else row.key.copyOfRange(start, end)
                        if (!actual.contentEquals(qualifier)) continue
                        assertTrue(row.value.size <= 100_000)
                        assertTrue(row.value.size + 2 * (row.key.size - prefix.size) + 12 > 100_000)
                        val replacement = prefix + added.key.bytes +
                            (if (historic) encodeZeroFreeUsing01(legacyQualifier) else legacyQualifier) + row.key.copyOfRange(end, row.key.size)
                        transaction.clear(row.key)
                        transaction.set(replacement, row.value)
                    }
                }
                transaction.clear(store.storageFormatPrefix + StorageFormatMetadataKey)
            }
            store.close()
            store = open()
            val chunks = store.tc.run { transaction ->
                store.rootDirectory.createOrOpen(transaction, listOf("__meta__", "storage_format_chunks")).awaitResult().pack()
            }
            store.tc.run { transaction ->
                assertTrue(!transaction.getRange(Range.startsWith(chunks), 1, false).iterator().hasNext())
            }
            for (version in listOf(null, added.version)) {
                assertEquals(values, store.execute(LargeStorageUpgradeRecord.get(added.key, toVersion = version)).values.single().values)
            }
            store.close()
            store = open()
            assertEquals(values, store.execute(LargeStorageUpgradeRecord.get(added.key)).values.single().values)
        } finally {
            store.close()
        }
    }

    @Test
    fun upgradesReleasedValuesAndIndexesOnce() = runTest {
        val directory = listOf("maryk", "storage-upgrade-${Random.nextInt()}")
        suspend fun open() = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory,
            dataModelsById = mapOf(1u to StorageUpgradeRecord), keepAllVersions = true, keepUpdateHistoryIndex = true)
        val fixture = StorageUpgradeFixture()
        val retiredMarkers = listOf(modelUpdateHistoryBackfillCompleteKey, modelReplicationTombstoneBackfillCompleteKey)
        var store = open()
        try {
            fixture.addTo(store)
            fun snapshot(prefix: ByteArray) = store.tc.run { transaction ->
                val iterator = transaction.getRange(Range.startsWith(prefix)).iterator()
                buildMap {
                    while (iterator.hasNext()) {
                        val row = iterator.nextBlocking()
                        put(row.key.copyOfRange(prefix.size, row.key.size).toHexString(), row.value.toHexString())
                    }
                }
            }
            val dirs = store.getTableDirs(1u) as HistoricTableDirectories
            val untouched = listOf(dirs.keysPrefix, dirs.tablePrefix, dirs.uniquePrefix, dirs.historicTablePrefix, dirs.historicUniquePrefix)
                .map(::snapshot)
            for ((prefix, historic) in listOf(dirs.tablePrefix to false, dirs.historicTablePrefix to true)) {
                val rows = snapshot(prefix).map { (key, value) ->
                    val bytes = key.hexToByteArray()
                    val payload = value.hexToByteArray()
                    if (bytes.size <= StorageUpgradeRecord.Meta.keyByteSize) return@map Triple(bytes, bytes, payload)
                    val end = bytes.size - if (historic) 9 else 0
                    val start = StorageUpgradeRecord.Meta.keyByteSize
                    val qualifier = if (historic) decodeZeroFreeUsing01(bytes, start, end - start) else bytes.copyOfRange(start, end)
                    val valueStart = if (historic) 0 else 8
                    val (target, legacy) = fixture.legacyCell(qualifier, payload.copyOfRange(valueStart, payload.size))
                    Triple(bytes, bytes.copyOfRange(0, start) + (if (historic) encodeZeroFreeUsing01(target) else target) + bytes.copyOfRange(end, bytes.size), payload.copyOfRange(0, valueStart) + legacy)
                }
                store.tc.run { transaction ->
                    rows.forEach { transaction.clear(prefix + it.first) }
                    rows.forEach { transaction.set(prefix + it.second, it.third) }
                }
            }
            for ((prefix, historic) in listOf(dirs.uniquePrefix to false, dirs.historicUniquePrefix to true)) {
                val rows = snapshot(prefix).map { (key, value) ->
                    val bytes = key.hexToByteArray()
                    val end = bytes.size - if (historic) 9 else 0
                    val source = if (historic) decodeZeroFreeUsing01(bytes, 0, end) else bytes
                    val legacy = fixture.legacyUnique(source)
                    Triple(bytes, (if (historic) encodeZeroFreeUsing01(legacy) else legacy) + bytes.copyOfRange(end, bytes.size), value.hexToByteArray())
                }
                store.tc.run { transaction ->
                    rows.forEach { transaction.clear(prefix + it.first) }
                    rows.forEach { transaction.set(prefix + it.second, it.third) }
                }
            }
            val replacements = fixture.records.flatMapIndexed { position, record ->
                StorageUpgradeRecord.Meta.indexes!!.map { index ->
                    val reference = index.referenceStorageByteArray.bytes
                    (reference + index.toStorageByteArrayForIndex(record, fixture.keys[position].bytes)!!).toHexString() to
                        (reference + fixture.legacyEntry(index, record, fixture.keys[position].bytes))
                }
            }.toMap()
            for ((prefix, historic) in listOf(dirs.indexPrefix to false, dirs.historicIndexPrefix to true)) {
                val rows = snapshot(prefix)
                store.tc.run { transaction ->
                    val replacementsForRows = rows.map { (key, value) ->
                        val bytes = key.hexToByteArray()
                        val end = bytes.size - if (historic) 9 else 0
                        val source = if (historic) decodeZeroFreeUsing01(bytes, 0, end) else bytes
                        val legacy = replacements.getValue(source.toHexString())
                        val target = if (historic) encodeZeroFreeUsing01(legacy) + bytes.copyOfRange(end, bytes.size) else legacy
                        Triple(bytes, target, value.hexToByteArray())
                    }
                    replacementsForRows.forEach { transaction.clear(prefix + it.first) }
                    replacementsForRows.forEach { transaction.set(prefix + it.second, it.third) }
                }
            }
            store.tc.run {
                it.clear(dirs.modelPrefix + StorageMaintenanceMetadataKey)
                it.clear(store.storageFormatPrefix + StorageFormatMetadataKey)
            }
            store.close()
            store = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory,
                dataModelsById = mapOf(2u to SimpleMarykModel), keepAllVersions = true, keepUpdateHistoryIndex = true)
            store.close()
            store = open()
            fixture.verify(store)
            val reopened = store.getTableDirs(1u) as HistoricTableDirectories
            assertEquals(untouched, listOf(reopened.keysPrefix, reopened.tablePrefix, reopened.uniquePrefix,
                reopened.historicTablePrefix, reopened.historicUniquePrefix).map(::snapshot))
            val converted = snapshot(reopened.indexPrefix)
            store.tc.run { transaction -> retiredMarkers.forEach {
                transaction.clear(reopened.modelPrefix + it)
            } }
            store.close()
            store = open()
            fixture.verify(store)
            assertEquals(converted, snapshot(store.getTableDirs(1u).indexPrefix))
            store.tc.run { transaction -> retiredMarkers.forEach {
                assertNull(transaction.get(store.getTableDirs(1u).modelPrefix + it).awaitResult())
            } }
        } finally {
            store.close()
        }
    }
}
