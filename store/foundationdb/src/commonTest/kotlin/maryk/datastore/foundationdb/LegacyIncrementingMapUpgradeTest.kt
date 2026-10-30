package maryk.datastore.foundationdb

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
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.shared.incrementingMapWatermarkPrefix
import maryk.datastore.shared.migration.StorageFormatMetadataKey
import maryk.datastore.shared.migration.legacyIncrementingMapProofKey
import maryk.foundationdb.Range
import kotlin.random.Random
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
    fun recoversErasedHistoryMaximaAndRequiresAuthorityWithoutHistory() = runTest {
        for (history in listOf(false, true)) {
            val directory = listOf("maryk", "legacy-allocation-$history-${Random.nextInt()}")
            suspend fun open() = FoundationDBDataStore.open(fdbClusterFilePath = "./fdb.cluster", directoryPath = directory,
                dataModelsById = mapOf(1u to LegacyAllocatedRecord), keepAllVersions = history)
            var store = open()
            try {
                val added = assertIs<AddSuccess<LegacyAllocatedRecord>>(store.execute(LegacyAllocatedRecord.add(
                    LegacyAllocatedRecord.create { entries with mapOf(1u to "first", 10u to "removed") },
                )).statuses.single())
                assertIs<ChangeSuccess<LegacyAllocatedRecord>>(store.execute(LegacyAllocatedRecord.change(added.key.change(
                    Change(LegacyAllocatedRecord.ref { entries } with mapOf(1u to "first")),
                ))).statuses.single())
                val dirs = store.getTableDirs(1u)
                val prefix = incrementingMapWatermarkPrefix(1u, added.key.bytes)
                val counter = dirs.modelPrefix + prefix + LegacyAllocatedRecord.entries.ref().toStorageByteArray()
                val proof = dirs.modelPrefix + legacyIncrementingMapProofKey(1u, added.key.bytes)
                val authoritativeMaximum = store.tc.run { it.get(counter).awaitResult() }!!
                store.tc.run { transaction ->
                    transaction.clear(Range.startsWith(dirs.modelPrefix + prefix))
                    transaction.clear(proof)
                    transaction.clear(store.storageFormatPrefix + StorageFormatMetadataKey)
                }
                if (!history) {
                    val sourceKey = dirs.tablePrefix + added.key.bytes + LegacyAllocatedRecord.entries.ref().toStorageByteArray()
                    val source = store.tc.run { it.get(sourceKey).awaitResult() }
                    val failure = assertFailsWith<MigrationException> { store.upgradeFoundationDBStorageFormat(preparePublication = {}) }
                    assertTrue(failure.message.orEmpty().contains("record=${added.key.bytes.toHexString()}"))
                    assertContentEquals(source, store.tc.run { it.get(sourceKey).awaitResult() })
                    store.tc.run { transaction ->
                        transaction.set(counter, authoritativeMaximum)
                        transaction.set(proof, byteArrayOf(1))
                    }
                }
                store.upgradeFoundationDBStorageFormat(preparePublication = {})
                assertContentEquals(authoritativeMaximum, store.tc.run { it.get(counter).awaitResult() })
                store.close()
                store = open()
                assertIs<ChangeSuccess<LegacyAllocatedRecord>>(store.execute(LegacyAllocatedRecord.change(added.key.change(
                    IncMapChange(LegacyAllocatedRecord.ref { entries }.change(addValues = listOf("next"))),
                ))).statuses.single())
                assertEquals(mapOf(1u to "first", 11u to "next"), store.execute(LegacyAllocatedRecord.get(added.key)).values.single().values { entries })
            } finally { store.close() }
        }
    }
}
