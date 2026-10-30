package maryk.datastore.rocksdb

import maryk.createTestDBFolder
import maryk.deleteFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.string
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.MultiTypeChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.getUpdates
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.datastore.shared.IsDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private object CommittedReplayRecord : RootDataModel<CommittedReplayRecord>(indexes = { listOf(CommittedReplayRecord.label.ref()) }) {
    val label by string(1u)
    val items by incrementingMap(2u, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition(), required = false)
}

class ReplicationCommittedChangeTest {
    @Test
    fun replaysEmittedIndexesAndResolvedIncrementingKeysInSourceOrder() = runTest {
        val sourceFolder = createTestDBFolder("committed-replay-source")
        val targetFolder = createTestDBFolder("committed-replay-target")
        val source = RocksDBDataStore.open(relativePath = sourceFolder, dataModelsById = mapOf(1u to CommittedReplayRecord), keepAllVersions = true)
        val target = RocksDBDataStore.open(relativePath = targetFolder, dataModelsById = mapOf(1u to CommittedReplayRecord), keepAllVersions = true)
        try { checkReplicationReplay(source, target) } finally {
            source.close(); target.close(); deleteFolder(sourceFolder); deleteFolder(targetFolder)
        }
    }
}

private suspend fun CoroutineScope.checkReplicationReplay(source: IsDataStore, target: IsDataStore) {
    val values = CommittedReplayRecord.create { label with "before" }
    val added = assertIs<AddSuccess<CommittedReplayRecord>>(source.execute(CommittedReplayRecord.add(values)).statuses.single())
    target.processUpdate(UpdateResponse(CommittedReplayRecord, AdditionUpdate(added.key, added.version, added.version, 0, false, values)))
    val next = async(start = UNDISPATCHED) {
        source.executeFlow(CommittedReplayRecord.getUpdates(added.key, fromVersion = added.version + 1u))
            .filterIsInstance<ChangeUpdate<CommittedReplayRecord>>().first()
    }
    source.execute(CommittedReplayRecord.change(added.key.change(Change(CommittedReplayRecord.ref { label } with "after"))))
    val emitted = next.await()
    assertTrue(emitted.changes.any { it is IndexChange })
    val replayed = target.processUpdate(UpdateResponse(CommittedReplayRecord, emitted))
    assertIs<ChangeSuccess<CommittedReplayRecord>>(assertIs<ChangeResponse<CommittedReplayRecord>>(replayed.result).statuses.single())
    assertEquals("after", target.execute(CommittedReplayRecord.get(added.key)).values.single().values { label })

    val reference = CommittedReplayRecord.ref { items }
    val resolved = listOf(
        Check(CommittedReplayRecord.ref { label } with "a source-only precondition"),
        IncMapChange(reference.change(addValues = listOf("allocated"))),
        Change(reference.propertyDefinition.valueRef(42u, reference) with "explicit-after"),
        IndexChange(emptyList()), MultiTypeChange(),
        IncMapAddition(IncMapKeyAdditions(reference, listOf(42u), null)),
    )
    val resolvedReplay = target.processUpdate(UpdateResponse(CommittedReplayRecord, ChangeUpdate(added.key, emitted.version + 1u, 0, resolved)))
    assertIs<ChangeSuccess<CommittedReplayRecord>>(assertIs<ChangeResponse<CommittedReplayRecord>>(resolvedReplay.result).statuses.single())
    assertEquals(mapOf(42u to "explicit-after"), target.execute(CommittedReplayRecord.get(added.key)).values.single().values { items })
    val standalone = target.processUpdate(UpdateResponse(CommittedReplayRecord, ChangeUpdate(added.key, emitted.version + 2u, 0,
        listOf(IncMapAddition(IncMapKeyAdditions(reference, listOf(43u), listOf("standalone")))))))
    assertIs<ChangeSuccess<CommittedReplayRecord>>(assertIs<ChangeResponse<CommittedReplayRecord>>(standalone.result).statuses.single())
    assertEquals(mapOf(42u to "explicit-after", 43u to "standalone"),
        target.execute(CommittedReplayRecord.get(added.key)).values.single().values { items })

    val created = CommittedReplayRecord.key(CommittedReplayRecord.create { label with "created" })
    target.processUpdate(UpdateResponse(CommittedReplayRecord, InitialChangesUpdate(21uL, listOf(
        DataObjectVersionedChange(key = created, changes = listOf(
            VersionedChanges(10uL, listOf(ObjectCreate, Change(CommittedReplayRecord.ref { label } with "created"),
                IncMapChange(reference.change(addValues = listOf("created-source"))),
                IncMapAddition(IncMapKeyAdditions(reference, listOf(41u), null)))),
            VersionedChanges(20uL, listOf(IncMapChange(reference.change(addValues = listOf("initial-source"))),
                Change(reference.propertyDefinition.valueRef(40u, reference) with "initial-explicit"),
                IncMapAddition(IncMapKeyAdditions(reference, listOf(40u), null)))),
        ))
    ))))
    assertEquals(mapOf(41u to "created-source", 40u to "initial-explicit"),
        target.execute(CommittedReplayRecord.get(created)).values.single().values { items })
}
