package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import maryk.core.clock.HLC
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.encodeZeroFreeUsing01
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.foundationdb.processors.helpers.packVersionedKey
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.foundationdb.Range
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object LegacyHistoryCountRecord : RootDataModel<LegacyHistoryCountRecord>() {
    val items by list(1u, valueDefinition = StringDefinition(), required = false)
}

class LegacyCollectionCountUpgradeTest {
    @Test
    fun creationVersionProvesCurrentAndHistoricZeroCountsPresent() = runTest {
        val directory = listOf("maryk", "creation-zero-count-${Random.nextInt()}")
        suspend fun open() = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory,
            dataModelsById = mapOf(1u to LegacyHistoryCountRecord), keepAllVersions = true)
        var store = open()
        try {
            val values = LegacyHistoryCountRecord.create { items with emptyList() }
            val added = assertIs<AddSuccess<LegacyHistoryCountRecord>>(store.execute(LegacyHistoryCountRecord.add(values)).statuses.single())
            store.tc.run { it.clear(store.storageFormatPrefix + StorageFormatMetadataKey) }
            store.close()
            store = open()
            for (version in listOf(null, added.version)) {
                assertEquals(values, store.execute(LegacyHistoryCountRecord.get(added.key, toVersion = version)).values.single().values)
            }
        } finally { store.close() }
    }

    @Test
    fun identicalReleasedHistoriesWithDifferentPresenceFailBeforeAnySourceMutation() = runTest {
        val indistinguishableHistories = mutableListOf<Map<String, String>>()
        for (deleted in listOf(false, true)) {
            val directory = listOf("maryk", "ambiguous-history-$deleted-${Random.nextInt()}")
            suspend fun open() = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory,
                dataModelsById = mapOf(1u to LegacyHistoryCountRecord), keepAllVersions = true)
            var store = open()
            try {
                val initial = LegacyHistoryCountRecord.create { items with listOf("x") }
                val added = assertIs<AddSuccess<LegacyHistoryCountRecord>>(store.execute(LegacyHistoryCountRecord.add(initial)).statuses.single())
                val middle = assertIs<ChangeSuccess<LegacyHistoryCountRecord>>(store.execute(LegacyHistoryCountRecord.change(added.key.change(
                    Change(LegacyHistoryCountRecord.ref { items } with if (deleted) null else emptyList()),
                ))).statuses.single())
                val last = assertIs<ChangeSuccess<LegacyHistoryCountRecord>>(store.execute(LegacyHistoryCountRecord.change(added.key.change(
                    Change(LegacyHistoryCountRecord.ref { items } with listOf("x")),
                ))).statuses.single())
                val dirs = store.getTableDirs(1u) as HistoricTableDirectories
                fun snapshot(prefix: ByteArray) = store.tc.run { transaction ->
                    val iterator = transaction.getRange(Range.startsWith(prefix)).iterator()
                    buildMap {
                        while (iterator.hasNext()) {
                            val row = iterator.nextBlocking()
                            put(row.key.copyOfRange(prefix.size, row.key.size).toHexString(), row.value.toHexString())
                        }
                    }
                }
                // Exact released FDB writer convention: every historic deletion was [0].
                store.tc.run { transaction ->
                    val iterator = transaction.getRange(Range.startsWith(dirs.historicTablePrefix)).iterator()
                    while (iterator.hasNext()) {
                        val row = iterator.nextBlocking()
                        if (row.value.isEmpty()) transaction.set(row.key, byteArrayOf(0))
                    }
                    transaction.clear(store.storageFormatPrefix + StorageFormatMetadataKey)
                }
                val source = listOf(dirs.tablePrefix, dirs.historicTablePrefix).map(::snapshot)
                val versionSlots = listOf(added.version, middle.version, last.version).mapIndexed { index, version ->
                    HLC.toStorageBytes(HLC(version)).toHexString() to index
                }.toMap()
                val historical = source[1].map { (key, value) ->
                    val bytes = key.hexToByteArray()
                    if (bytes.size == added.key.bytes.size) "creation" to "v${versionSlots.getValue(value)}" else {
                        val ordinaryVersion = ByteArray(8) { bytes[bytes.size - 8 + it].toInt().inv().toByte() }.toHexString()
                        bytes.copyOfRange(added.key.bytes.size, bytes.size - 8).toHexString() + ":v${versionSlots.getValue(ordinaryVersion)}" to value
                    }
                }.toMap()
                val current = source[0].map { (key, value) ->
                    val bytes = value.hexToByteArray()
                    "current:" + key.hexToByteArray().copyOfRange(added.key.bytes.size, key.length / 2).toHexString() to
                        "v${versionSlots.getValue(bytes.copyOfRange(0, 8).toHexString())}:" + bytes.copyOfRange(8, bytes.size).toHexString()
                }.toMap()
                indistinguishableHistories += historical + current
                val failure = assertFailsWith<MigrationException> { store.upgradeFoundationDBStorageFormat(preparePublication = {}) }
                assertTrue(failure.message.orEmpty().contains("model=1"))
                assertTrue(failure.message.orEmpty().contains("version=${HLC.toStorageBytes(HLC(middle.version)).toHexString()}"))
                assertEquals(source, listOf(dirs.tablePrefix, dirs.historicTablePrefix).map(::snapshot))
                store.tc.run { assertNull(it.get(store.storageFormatPrefix + StorageFormatMetadataKey).awaitResult()) }
                if (deleted) {
                    // The authoritative request log proves this particular middle state absent.
                    val qualifier = LegacyHistoryCountRecord.items.ref().toStorageByteArray()
                    store.tc.run { transaction ->
                        transaction.set(packVersionedKey(dirs.historicTablePrefix, added.key.bytes,
                            encodeZeroFreeUsing01(qualifier), version = HLC.toStorageBytes(HLC(middle.version))), byteArrayOf())
                    }
                    store.upgradeFoundationDBStorageFormat(preparePublication = {})
                    store.close()
                    store = open()
                    assertNull(store.execute(LegacyHistoryCountRecord.get(added.key, toVersion = middle.version)).values.single().values { items })
                    assertEquals(initial, store.execute(LegacyHistoryCountRecord.get(added.key)).values.single().values)
                }
            } finally { store.close() }
        }
        assertEquals(indistinguishableHistories[0], indistinguishableHistories[1])
    }
}
