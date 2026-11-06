package maryk.datastore.test

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.datastore.shared.StateBackupAllocations
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.embed
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.change
import maryk.core.query.responses.statuses.ChangeSuccess

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope

import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.enum.invoke
import maryk.core.properties.types.invoke
import maryk.core.values.Values
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.pairs.with
import maryk.core.query.requests.delete
import maryk.core.query.requests.add
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.datastore.shared.DataStoreStateBackupChunk
import maryk.datastore.shared.DataStoreStateBackupManifest
import maryk.datastore.shared.DataStoreStateBackupOptions
import maryk.datastore.shared.DataStoreStateBackupWriter
import maryk.datastore.shared.DataStoreStateBackupReader
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.StateBackupArchiveWriter
import maryk.datastore.shared.backupState
import maryk.datastore.shared.openStateBackupArchive
import maryk.datastore.shared.restoreState
import maryk.datastore.shared.encryption.AesGcmHmacSha256EncryptionProvider
import maryk.lib.extensions.compare.compareTo
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T3
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

val advancedStateBackupModels: Map<UInt, IsRootDataModel> = mapOf(
    1u to CollectionPresenceRecord,
    2u to ComplexModel,
    3u to UniqueModel,
    4u to StateAllocationRecord,
)

fun stateBackupTestEncryptionProvider(seed: Int) = AesGcmHmacSha256EncryptionProvider(
    ByteArray(32) { (it + seed).toByte() }, ByteArray(32) { (it + seed + 32).toByte() },
)


object StateAllocationDetails : DataModel<StateAllocationDetails>() {
    val entries by incrementingMap(1u, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition())
}

object StateAllocationRecord : RootDataModel<StateAllocationRecord>() {
    val entries by incrementingMap(1u, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition())
    val details by embed(2u, required = false, dataModel = { StateAllocationDetails })
    val label by string(3u, required = false, unique = true)
}

/** Removed map entries and an entirely absent parent must keep their record-lifetime counters. */
suspend fun assertDeletedStateAllocations(store: IsDataStore) {
    val records = store.execute(StateAllocationRecord.scan(allowTableScan = true)).values
    val key = records.single { it.values { label } == "lifetime" }.key
    val exhausted = records.single { it.values { label } == "exhausted" }
    assertStatusIs<ServerFail<StateAllocationRecord>>(store.execute(StateAllocationRecord.change(exhausted.key.change(IncMapChange(
        StateAllocationRecord.ref { entries }.change(addValues = listOf("must not wrap")),
    )))).statuses.single())
    assertEquals(exhausted, store.execute(StateAllocationRecord.get(exhausted.key)).values.single())
    assertStatusIs<ChangeSuccess<StateAllocationRecord>>(store.execute(StateAllocationRecord.change(key.change(Change(
        StateAllocationRecord.ref { details } with StateAllocationDetails.create { entries with emptyMap() },
    )))).statuses.single())
    assertStatusIs<ChangeSuccess<StateAllocationRecord>>(store.execute(StateAllocationRecord.change(key.change(IncMapChange(
        StateAllocationRecord.ref { entries }.change(addValues = listOf("next root")),
        StateAllocationRecord.ref { details { entries } }.change(addValues = listOf("next nested")),
    )))).statuses.single())
    val values = store.execute(StateAllocationRecord.get(key)).values.single().values
    assertEquals(mapOf(3u to "next root"), values { entries })
    assertEquals(mapOf(10u to "next nested"), values { details }!! { entries })
}

/** Real binary transport, nested/multi-type values, empty collections and records without fields. */
suspend fun assertAdvancedStateRoundTrip(source: IsDataStore, target: IsDataStore): Map<String, List<ValuesWithMetaData<IsRootDataModel>>> {
    source.execute(CollectionPresenceRecord.add(
        CollectionPresenceRecord.create {
            label with "empty collections"
            secret with "backup encryption transfer"
            items with emptyList()
            members with emptySet()
            mapping with emptyMap()
            details with CollectionPresenceDetails.create { items with emptyList(); members with emptySet(); mapping with emptyMap() }
            nestedList with mapOf("empty" to emptyList(), "filled" to listOf("a", "b"))
            nestedSet with mapOf("empty" to emptySet(), "filled" to setOf("a", "b"))
            nestedMap with mapOf("empty" to emptyMap(), "filled" to mapOf("a" to "b"))
            detailItems with listOf(CollectionPresenceDetails.create {}, CollectionPresenceDetails.create { items with listOf("nested") })
        },
        CollectionPresenceRecord.create { label with "absent optional fields" },
    )).statuses.forEach { assertStatusIs<AddSuccess<CollectionPresenceRecord>>(it) }
    source.execute(ComplexModel.add(
        ComplexModel.create {},
        ComplexModel.create {
            multi with T3 { value with "outer"; model with { value with "inner" } }
            mapStringString with mapOf("name" to "value")
            mapIntObject with mapOf(1u to EmbeddedMarykModel.create { value with "one" })
            mapIntMulti with mapOf(1u to T1("text"), 2u to T3 { value with "embedded" })
            mapWithList with mapOf("slot" to listOf("one", "two"))
            mapWithSet with mapOf("slot" to setOf("one", "two"))
            mapWithMap with mapOf("slot" to mapOf("nested" to "value"))
            incMap with mapOf(4u to EmbeddedMarykModel.create { value with "allocated" })
        },
    )).statuses.forEach { assertStatusIs<AddSuccess<ComplexModel>>(it) }
    val allocated = assertStatusIs<AddSuccess<StateAllocationRecord>>(source.execute(StateAllocationRecord.add(
        StateAllocationRecord.create {
            label with "lifetime"
            entries with mapOf(1u to "first", 2u to "last")
            details with StateAllocationDetails.create { entries with mapOf(9u to "deleted allocation") }
        },
    )).statuses.single())
    assertStatusIs<ChangeSuccess<StateAllocationRecord>>(source.execute(StateAllocationRecord.change(allocated.key.change(Change(
        StateAllocationRecord.ref { entries } with emptyMap<UInt, String>(),
        StateAllocationRecord.ref { details } with null,
    )))).statuses.single())
    val exhausted = assertStatusIs<AddSuccess<StateAllocationRecord>>(source.execute(StateAllocationRecord.add(
        StateAllocationRecord.create { label with "exhausted"; entries with mapOf(UInt.MAX_VALUE to "last possible ID") },
    )).statuses.single())
    assertStatusIs<ChangeSuccess<StateAllocationRecord>>(source.execute(StateAllocationRecord.change(exhausted.key.change(Change(
        StateAllocationRecord.ref { entries } with emptyMap<UInt, String>(),
    )))).statuses.single())
    val expected = source.dataModelsById.values.associate { model ->
        model.Meta.name to source.execute(model.scan(filterSoftDeleted = false, allowTableScan = true)).values
    }
    assertEquals(2, expected.getValue(ComplexModel.Meta.name).size, "The source must expose empty records")
    val frames = mutableListOf<ByteArray>()
    var published = false
    source.backupState(StateBackupArchiveWriter(source.dataModelsById, { frames += it }, { published = true }),
        DataStoreStateBackupOptions(batchSize = 2u))
    assertTrue(published)
    val bytes = frames.reduce { a, b -> a + b }
    var position = 0
    val reader = openStateBackupArchive(target.dataModelsById) { maximum ->
        val end = minOf(bytes.size, position + minOf(maximum, 7))
        bytes.copyOfRange(position, end).also { position = end }
    }
    assertEquals(6uL, target.restoreState(reader).records)
    assertAdvancedStateMatches(target, expected)
    return expected
}

suspend fun assertAdvancedStateMatches(store: IsDataStore, expected: Map<String, List<ValuesWithMetaData<IsRootDataModel>>>) {
    for (model in store.dataModelsById.values) {
        assertEquals(expected.getValue(model.Meta.name),
            store.execute(model.scan(filterSoftDeleted = false, allowTableScan = true)).values, model.Meta.name)
    }
}

/** A later duplicate unique claim must roll back all records, including differing source versions. */
@Suppress("UNCHECKED_CAST")
suspend fun assertStateBatchRollback(target: IsDataStore) {
    val first = UniqueModel.create { email with "batch-duplicate@example.com" }
    val second = UniqueModel.create { email with "batch-other@example.com" }
    val records = listOf(
        ValuesWithMetaData<IsRootDataModel>(UniqueModel.key(first), first as Values<IsRootDataModel>, 10uL, 20uL, false),
        ValuesWithMetaData<IsRootDataModel>(UniqueModel.key(second), first as Values<IsRootDataModel>, 30uL, 40uL, false),
    ).sortedWith { a, b -> a.key.bytes compareTo b.key.bytes }
    val reader = object : DataStoreStateBackupReader {
        override val manifest = DataStoreStateBackupManifest(listOf(UniqueModel.Meta.name), mapOf(UniqueModel.Meta.name to UniqueModel.Meta.version.major))
        override suspend fun read(consumer: suspend (DataStoreStateBackupChunk) -> Unit) {
            consumer(DataStoreStateBackupChunk(UniqueModel.Meta.name, records))
        }
    }
    assertFails { target.restoreState(reader) }
    assertTrue(target.execute(UniqueModel.scan(allowTableScan = true)).values.isEmpty())
    // The failed batch must not leave an orphaned unique claim either.
    val added = assertStatusIs<AddSuccess<UniqueModel>>(target.execute(UniqueModel.add(first)).statuses.single())
    target.execute(UniqueModel.delete(added.key, hardDelete = true))
    val allocationFirst = StateAllocationRecord.create { label with "rollback-first"; entries with emptyMap() }
    val allocationSecond = StateAllocationRecord.create { label with "rollback-second"; entries with emptyMap() }
    val firstKey = Key<IsRootDataModel>(StateAllocationRecord.key(allocationFirst).bytes)
    val secondKey = Key<IsRootDataModel>(StateAllocationRecord.key(allocationSecond).bytes)
    val allocationRecords = listOf(
        ValuesWithMetaData(firstKey, allocationFirst as Values<IsRootDataModel>, 10uL, 20uL, false),
        ValuesWithMetaData(secondKey, allocationFirst as Values<IsRootDataModel>, 30uL, 40uL, false),
    ).sortedWith { a, b -> a.key.bytes compareTo b.key.bytes }
    val allocationMetadata: StateBackupAllocations = mapOf(Bytes(StateAllocationRecord.ref { entries }.toStorageByteArray()) to Bytes(byteArrayOf(0, 0, 0, 100)))
    val allocationReader = object : DataStoreStateBackupReader {
        override val manifest = DataStoreStateBackupManifest(listOf(StateAllocationRecord.Meta.name), mapOf(StateAllocationRecord.Meta.name to StateAllocationRecord.Meta.version.major))
        override suspend fun read(consumer: suspend (DataStoreStateBackupChunk) -> Unit) {
            consumer(DataStoreStateBackupChunk(StateAllocationRecord.Meta.name, allocationRecords,
                mapOf(firstKey to allocationMetadata, secondKey to allocationMetadata)))
        }
    }
    assertFails { target.restoreState(allocationReader) }
    assertTrue(target.execute(StateAllocationRecord.scan(allowTableScan = true)).values.isEmpty())
    val validRecords = allocationRecords.map { if (it.key == secondKey) it.copy(values = allocationSecond as Values<IsRootDataModel>) else it }
    val validReader = object : DataStoreStateBackupReader by allocationReader {
        override suspend fun read(consumer: suspend (DataStoreStateBackupChunk) -> Unit) {
            consumer(DataStoreStateBackupChunk(StateAllocationRecord.Meta.name, validRecords))
        }
    }
    target.restoreState(validReader)
    for (record in validRecords) {
        assertStatusIs<ChangeSuccess<StateAllocationRecord>>(target.execute(StateAllocationRecord.change(Key<StateAllocationRecord>(record.key.bytes).change(IncMapChange(
            StateAllocationRecord.ref { entries }.change(addValues = listOf("fresh allocation")),
        )))).statuses.single())
        assertEquals(mapOf(1u to "fresh allocation"), target.execute(StateAllocationRecord.get(Key(record.key.bytes))).values.single().values { entries })
        target.execute(StateAllocationRecord.delete(Key(record.key.bytes), hardDelete = true))
    }
}

/** Closing must cancel a suspended destination and release the capture's native resources. */
suspend fun assertStateCaptureClosesCleanly(source: IsDataStore) = coroutineScope {
    val entered = CompletableDeferred<Unit>()
    var published = false
    val capture = async {
        runCatching {
            source.backupState(object : DataStoreStateBackupWriter {
                override suspend fun begin(manifest: DataStoreStateBackupManifest) {
                    entered.complete(Unit)
                    awaitCancellation()
                }
                override suspend fun write(chunk: DataStoreStateBackupChunk) = Unit
                override suspend fun complete() { published = true }
            })
        }
    }
    entered.await()
    source.close()
    assertTrue(capture.await().isFailure)
    assertTrue(!published)
}
