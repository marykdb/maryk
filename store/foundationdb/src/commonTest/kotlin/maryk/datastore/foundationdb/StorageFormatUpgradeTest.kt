package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.datastore.foundationdb.processors.helpers.decodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.datastore.test.StorageUpgradeFixture
import maryk.test.models.SimpleMarykModel
import maryk.datastore.shared.migration.StorageMaintenanceMetadataKey
import maryk.datastore.test.StorageUpgradeRecord
import maryk.foundationdb.Range
import kotlin.random.Random
import kotlin.test.assertNull
import maryk.datastore.foundationdb.model.modelUpdateHistoryBackfillCompleteKey
import maryk.datastore.foundationdb.model.modelReplicationTombstoneBackfillCompleteKey
import kotlin.test.Test
import kotlin.test.assertEquals

class StorageFormatUpgradeTest {
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
