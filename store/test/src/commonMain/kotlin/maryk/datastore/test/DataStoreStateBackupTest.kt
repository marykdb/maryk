package maryk.datastore.test

import maryk.datastore.shared.StateBackupAllocations
import maryk.core.properties.types.Key

import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.shared.DataStoreStateBackupChunk
import maryk.datastore.shared.DataStoreStateBackupManifest
import maryk.datastore.shared.DataStoreStateBackupOptions
import maryk.datastore.shared.DataStoreStateBackupReader
import maryk.datastore.shared.DataStoreStateBackupWriter
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.backupState
import maryk.datastore.shared.restoreState
import maryk.test.models.SimpleMarykModel
import maryk.test.models.Log
import maryk.test.models.Severity.ERROR
import maryk.test.models.Severity.INFO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val stateBackupModels: Map<UInt, IsRootDataModel> = mapOf(1u to SimpleMarykModel, 2u to UniqueModel, 3u to Log)

/** A portable contract shared by persistent and in-memory engines, with real protobuf transport. */
class DataStoreStateBackupTest(private val source: IsDataStore, private val target: IsDataStore) {
    suspend fun restoresStateWithoutHistory() {
        val changed = assertStatusIs<AddSuccess<SimpleMarykModel>>(source.execute(SimpleMarykModel.add(
            SimpleMarykModel.create { value with "ha state before" },
        )).statuses.single())
        source.execute(SimpleMarykModel.change(changed.key.change(Change(SimpleMarykModel.ref { value } with "ha state after"))))
        val deleted = assertStatusIs<AddSuccess<SimpleMarykModel>>(source.execute(SimpleMarykModel.add(
            SimpleMarykModel.create { value with "ha state deleted" },
        )).statuses.single())
        source.execute(SimpleMarykModel.delete(deleted.key))
        source.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "ha state retained" }))
        val expected = source.execute(SimpleMarykModel.scan(filterSoftDeleted = false, allowTableScan = true)).values
        source.execute(UniqueModel.add(UniqueModel.create { email with "state-active@example.com" }))
        val deletedUnique = assertStatusIs<AddSuccess<UniqueModel>>(source.execute(UniqueModel.add(
            UniqueModel.create { email with "state-reused@example.com" },
        )).statuses.single())
        source.execute(UniqueModel.delete(deletedUnique.key))
        source.execute(UniqueModel.add(UniqueModel.create { email with "state-reused@example.com" }))
        source.execute(Log.add(Log("state info", INFO), Log("state error", ERROR)))
        val expectedUnique = source.execute(UniqueModel.scan(filterSoftDeleted = false, allowTableScan = true)).values
        val indexed = Log.scan(order = Log.severity.ref().ascending())
        val expectedIndexed = source.execute(indexed).values
        val archive = SerializedStateBackup(source)
        source.backupState(archive, DataStoreStateBackupOptions(batchSize = 1u))
        assertTrue(archive.completed)
        assertEquals(8, archive.chunks.size)
        source.execute(SimpleMarykModel.delete(changed.key, hardDelete = true))
        assertEquals(8uL, target.restoreState(archive).records)
        val actual = target.execute(SimpleMarykModel.scan(filterSoftDeleted = false, allowTableScan = true)).values
        assertEquals(expected, actual)
        assertEquals(expectedUnique, target.execute(UniqueModel.scan(filterSoftDeleted = false, allowTableScan = true)).values)
        assertEquals(expectedIndexed, target.execute(indexed).values)
        assertStatusIs<ValidationFail<UniqueModel>>(target.execute(UniqueModel.add(
            UniqueModel.create { email with "state-active@example.com" },
        )).statuses.single())
        assertEquals("ha state after", target.execute(SimpleMarykModel.get(changed.key)).values.single().values { value })
        assertTrue(target.execute(SimpleMarykModel.get(deleted.key)).values.isEmpty())
        assertTrue(target.execute(SimpleMarykModel.get(deleted.key, filterSoftDeleted = false)).values.single().isDeleted)
        val added = assertStatusIs<AddSuccess<SimpleMarykModel>>(target.execute(SimpleMarykModel.add(
            SimpleMarykModel.create { value with "ha state new" },
        )).statuses.single())
        assertTrue(added.version > expected.maxOf { it.lastVersion })
    }
}

/** Serialized archive fixture detaches writer/reader buffers and exercises contextual model decoding. */
class SerializedStateBackup(store: IsDataStore) : DataStoreStateBackupWriter, DataStoreStateBackupReader {
    private val models = store.dataModelsById.values.associateBy { it.Meta.name }
    private fun context(name: String) = RequestContext(
        DefinitionsContext(models.mapValues { DataModelReference(it.value) }.toMutableMap()), models.getValue(name),
    )
    override lateinit var manifest: DataStoreStateBackupManifest
    val chunks = mutableListOf<Pair<String, List<ByteArray>>>()
    private val allocations = mutableListOf<Map<Key<IsRootDataModel>, StateBackupAllocations>>()
    var completed = false
    override suspend fun begin(manifest: DataStoreStateBackupManifest) {
        this.manifest = manifest
    }
    override suspend fun write(chunk: DataStoreStateBackupChunk) {
        allocations += chunk.allocations.mapValues { it.value.toMap() }
        chunks += chunk.modelName to chunk.records.map { record ->
            val context = context(chunk.modelName)
            val cache = WriteCache()
            val size = ValuesWithMetaData.Serializer.calculateObjectProtoBufLength(record, cache, context)
            var index = 0
            ByteArray(size).also { bytes -> ValuesWithMetaData.Serializer.writeObjectProtoBuf(record, cache, { bytes[index++] = it }, context) }
        }
    }
    override suspend fun complete() { completed = true }
    @Suppress("UNCHECKED_CAST")
    override suspend fun read(consumer: suspend (DataStoreStateBackupChunk) -> Unit) {
        check(completed)
        for ((chunkIndex, chunk) in chunks.withIndex()) {
            val (name, records) = chunk
            consumer(DataStoreStateBackupChunk(name, records.map { bytes ->
                var index = 0
                ValuesWithMetaData.Serializer.readProtoBuf(bytes.size, { bytes[index++] }, context(name)).toDataObject() as ValuesWithMetaData<IsRootDataModel>
            }, allocations[chunkIndex]))
        }
    }
}
