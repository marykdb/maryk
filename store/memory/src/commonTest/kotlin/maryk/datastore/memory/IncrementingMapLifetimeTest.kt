package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.datastore.shared.DataStoreBackupChunk
import maryk.datastore.shared.DataStoreBackupManifest
import maryk.datastore.shared.DataStoreBackupReader
import maryk.datastore.shared.DataStoreBackupWriter
import maryk.datastore.shared.StateBackupArchiveWriter
import maryk.datastore.shared.backupState
import maryk.datastore.shared.openStateBackupArchive
import maryk.datastore.shared.restoreState
import maryk.datastore.shared.backup
import maryk.datastore.shared.restore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private object AllocationDetails : DataModel<AllocationDetails>() {
    val entries by incrementingMap(1u, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition())
}

private object LifetimeAllocatedRecord : RootDataModel<LifetimeAllocatedRecord>() {
    val entries by incrementingMap(1u, keyNumberDescriptor = UInt32,
        valueDefinition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = StringDefinition()))
    val details by embed(2u, required = false, dataModel = { AllocationDetails })
}

class IncrementingMapLifetimeTest {
    @Test
    fun stateBackupMustPreserveDeletedAllocationMaxima() = runTest {
        val models = mapOf(1u to LifetimeAllocatedRecord)
        val source = InMemoryDataStore.open(dataModelsById = models, keepAllVersions = false)
        val target = InMemoryDataStore.open(dataModelsById = models, keepAllVersions = false)
        val nextTarget = InMemoryDataStore.open(dataModelsById = models, keepAllVersions = false)
        try {
            val added = assertIs<AddSuccess<LifetimeAllocatedRecord>>(source.execute(LifetimeAllocatedRecord.add(
                LifetimeAllocatedRecord.create {
                    entries with mapOf(1u to emptyMap(), 2u to emptyMap())
                    details with AllocationDetails.create { entries with mapOf(1u to "earlier allocation", 9u to "previously allocated") }
                },
            )).statuses.single())
            source.execute(LifetimeAllocatedRecord.change(added.key.change(Change(
                LifetimeAllocatedRecord.ref { entries } with emptyMap<UInt, Map<String, String>>(),
                LifetimeAllocatedRecord.ref { details } with null,
            ))))
            source.execute(LifetimeAllocatedRecord.change(added.key.change(Change(
                LifetimeAllocatedRecord.ref { details } with AllocationDetails.create { entries with emptyMap() },
            ))))
            val frames = mutableListOf<ByteArray>()
            source.backupState(StateBackupArchiveWriter(models, { frames += it }, {}))
            val bytes = frames.reduce { a, b -> a + b }
            var position = 0
            val reader = openStateBackupArchive(models) { maximum ->
                val end = minOf(bytes.size, position + maximum)
                bytes.copyOfRange(position, end).also { position = end }
            }
            target.restoreState(reader)
            frames.clear()
            target.backupState(StateBackupArchiveWriter(models, { frames += it }, {}))
            val secondBytes = frames.reduce { a, b -> a + b }
            position = 0
            nextTarget.restoreState(openStateBackupArchive(models) { maximum ->
                val end = minOf(secondBytes.size, position + maximum)
                secondBytes.copyOfRange(position, end).also { position = end }
            })
            for (store in listOf(source, target, nextTarget)) {
                assertIs<ChangeSuccess<LifetimeAllocatedRecord>>(store.execute(LifetimeAllocatedRecord.change(added.key.change(IncMapChange(
                    LifetimeAllocatedRecord.ref { entries }.change(addValues = listOf(emptyMap<String, String>())),
                    LifetimeAllocatedRecord.ref { details { entries } }.change(addValues = listOf("next allocation")),
                )))).statuses.single())
            }
            val expected = source.execute(LifetimeAllocatedRecord.get(added.key)).values.single().values
            val restored = target.execute(LifetimeAllocatedRecord.get(added.key)).values.single().values
            assertEquals(mapOf(3u to emptyMap()), expected { entries })
            assertEquals(mapOf(10u to "next allocation"), expected { details }?.get(AllocationDetails.ref { entries }))
            assertEquals(expected { entries }, nextTarget.execute(LifetimeAllocatedRecord.get(added.key)).values.single().values { entries })
            assertEquals(expected { entries }, restored { entries }, "State restore must not reuse previously allocated identifiers")
            assertEquals(expected { details }, restored { details }, "Nested allocation maxima survive parent removal too")
        } finally { source.close(); target.close(); nextTarget.close() }
    }

    @Test
    fun replacementParentRemovalAndBackupKeepRecordLifetimeMaxima() = runTest {

        for (history in listOf(false, true)) {
            suspend fun open(label: String) = InMemoryDataStore.open(dataModelsById = mapOf(1u to LifetimeAllocatedRecord), keepAllVersions = history)
            var source = open("source")
            val target = open("restored")
            try {
                val added = assertIs<AddSuccess<LifetimeAllocatedRecord>>(source.execute(LifetimeAllocatedRecord.add(
                    LifetimeAllocatedRecord.create {
                        entries with mapOf(1u to emptyMap(), 2u to emptyMap())
                        details with AllocationDetails.create { entries with mapOf(9u to "last") }
                    },
                )).statuses.single())
                assertIs<ChangeSuccess<LifetimeAllocatedRecord>>(source.execute(LifetimeAllocatedRecord.change(added.key.change(Change(
                    LifetimeAllocatedRecord.ref { entries } with emptyMap<UInt, Map<String, String>>(),
                    LifetimeAllocatedRecord.ref { details } with null,
                )))).statuses.single())
                assertIs<ChangeSuccess<LifetimeAllocatedRecord>>(source.execute(LifetimeAllocatedRecord.change(added.key.change(Change(
                    LifetimeAllocatedRecord.ref { details } with AllocationDetails.create { entries with emptyMap() },
                )))).statuses.single())

                assertIs<ChangeSuccess<LifetimeAllocatedRecord>>(source.execute(LifetimeAllocatedRecord.change(added.key.change(IncMapChange(
                    LifetimeAllocatedRecord.ref { entries }.change(addValues = listOf(emptyMap<String, String>())),
                    LifetimeAllocatedRecord.ref { details { entries } }.change(addValues = listOf("next")),
                )))).statuses.single())
                val afterRemoval = source.execute(LifetimeAllocatedRecord.get(added.key)).values.single().values
                assertEquals(mapOf(3u to emptyMap()), afterRemoval { entries })
                assertEquals(mapOf(10u to "next"), afterRemoval { details }?.get(AllocationDetails.ref { entries }))
                assertIs<ChangeSuccess<LifetimeAllocatedRecord>>(source.execute(LifetimeAllocatedRecord.change(added.key.change(Change(
                    LifetimeAllocatedRecord.ref { entries at 3u } with null,
                    LifetimeAllocatedRecord.ref { details { entries at 10u } } with null,
                )))).statuses.single())
                assertEquals(emptyMap(), source.execute(LifetimeAllocatedRecord.get(added.key)).values.single().values { details }?.get(AllocationDetails.ref { entries }), "empty nested map before backup, history=$history")
                val resumed = if (history) {
                    val backup = LifetimeAllocationBackup()
                    source.backup(backup)
                    target.restore(backup)
                    target
                } else source
                assertIs<ChangeSuccess<LifetimeAllocatedRecord>>(resumed.execute(LifetimeAllocatedRecord.change(added.key.change(IncMapChange(
                    LifetimeAllocatedRecord.ref { entries }.change(addValues = listOf(emptyMap<String, String>())),
                    LifetimeAllocatedRecord.ref { details { entries } }.change(addValues = listOf("after restore")),
                )))).statuses.single())
                val restored = resumed.execute(LifetimeAllocatedRecord.get(added.key)).values.single().values
                assertEquals(mapOf(4u to emptyMap()), restored { entries })
                assertEquals(mapOf(11u to "after restore"), restored { details }?.get(AllocationDetails.ref { entries }), "history=$history; values=$restored")
                assertIs<DeleteSuccess<LifetimeAllocatedRecord>>(resumed.execute(LifetimeAllocatedRecord.delete(added.key, hardDelete = true)).statuses.single())
                val reset = LifetimeAllocatedRecord.create {
                    entries with emptyMap()
                    details with AllocationDetails.create { entries with emptyMap() }
                }
                assertIs<AddSuccess<LifetimeAllocatedRecord>>(resumed.execute(LifetimeAllocatedRecord.add(added.key to reset)).statuses.single())
                assertIs<ChangeSuccess<LifetimeAllocatedRecord>>(resumed.execute(LifetimeAllocatedRecord.change(added.key.change(IncMapChange(
                    LifetimeAllocatedRecord.ref { entries }.change(addValues = listOf(emptyMap<String, String>())),
                    LifetimeAllocatedRecord.ref { details { entries } }.change(addValues = listOf("fresh lifetime")),
                )))).statuses.single())
                val fresh = resumed.execute(LifetimeAllocatedRecord.get(added.key)).values.single().values
                assertEquals(mapOf(1u to emptyMap()), fresh { entries })
                assertEquals(mapOf(1u to "fresh lifetime"), fresh { details }?.get(AllocationDetails.ref { entries }))
            } finally {
                source.close()
                target.close()

            }
        }
    }
}

private class LifetimeAllocationBackup : DataStoreBackupWriter, DataStoreBackupReader {
    override lateinit var manifest: DataStoreBackupManifest
    private val chunks = mutableListOf<DataStoreBackupChunk>()
    override suspend fun begin(manifest: DataStoreBackupManifest) { this.manifest = manifest }
    override suspend fun write(chunk: DataStoreBackupChunk) { chunks += chunk }
    override suspend fun complete() = Unit
    override suspend fun read(consumer: suspend (DataStoreBackupChunk) -> Unit) { for (chunk in chunks) consumer(chunk) }
}
