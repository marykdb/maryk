package maryk.datastore.indexeddb

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.getUpdates
import maryk.core.query.requests.scan
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.OrderedKeysUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.random.Random

private object ReplicationNormalizedModel : RootDataModel<ReplicationNormalizedModel>(indexes = { listOf(ReplicationNormalizedModel.name.ref()) }) {
    val name by string(1u)
    val entries by incrementingMap(2u, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition())
}

class ReplicationNormalizationTest {
    @Test
    fun creationReplayInitializesMissingIncrementingMap() = runTest {
        installIndexedDbForTests()
        val target = IndexedDbDataStore.open("maryk-normalized-create-${Random.nextInt()}", mapOf(1u to ReplicationNormalizedModel))
        try {
            val reference = ReplicationNormalizedModel.ref { entries }
            for (initialReplay in listOf(false, true)) {
                val key = Key<ReplicationNormalizedModel>(ByteArray(ReplicationNormalizedModel.Meta.keyByteSize) { if (initialReplay) 11 else 12 })
                val changes = listOf(
                    ObjectCreate,
                    Change(ReplicationNormalizedModel.ref { name } with "created"),
                    IncMapChange(reference.change(addValues = listOf("first"))),
                    Change(reference.propertyDefinition.valueRef(42u, reference) with "later"),
                    IncMapAddition(IncMapKeyAdditions(reference, listOf(42u), null)),
                )
                if (initialReplay) target.processUpdate(UpdateResponse(ReplicationNormalizedModel, InitialChangesUpdate(30uL,
                    listOf(DataObjectVersionedChange(key, changes = listOf(VersionedChanges(30uL, changes)))))))
                else target.processUpdate(UpdateResponse(ReplicationNormalizedModel, ChangeUpdate(key, 30uL, 0, changes)))
                assertEquals(mapOf(42u to "later"), target.execute(ReplicationNormalizedModel.get(key)).values.single().values { entries })
            }
        } finally { target.close() }
    }

    @Test
    fun emittedIndexedChangeReplaysWithoutReapplyingDerivedIndexMetadata() = runTest {
        installIndexedDbForTests()
        suspend fun open(label: String) = IndexedDbDataStore.open("maryk-normalized-$label-${Random.nextInt()}", mapOf(1u to ReplicationNormalizedModel))
        val source = open("source")
        val target = open("target")
        val updates = Channel<Any>(Channel.UNLIMITED)
        try {
            val values = ReplicationNormalizedModel.create { name with "before"; entries with emptyMap() }
            val added = assertIs<AddSuccess<ReplicationNormalizedModel>>(source.execute(ReplicationNormalizedModel.add(values)).statuses.single())
            target.processUpdate(UpdateResponse(ReplicationNormalizedModel, AdditionUpdate(added.key, added.version, added.version, 0, false, values)))
            val listener = launch { source.executeFlow(ReplicationNormalizedModel.getUpdates(added.key)).collect(updates::send) }
            try {
                assertIs<OrderedKeysUpdate<*>>(updates.receive())
                assertIs<AdditionUpdate<*>>(updates.receive())
                source.execute(ReplicationNormalizedModel.change(added.key.change(Change(ReplicationNormalizedModel.ref { name } with "after"))))
                val emitted = assertIs<ChangeUpdate<ReplicationNormalizedModel>>(updates.receive())
                assertTrue(emitted.changes.any { it is IndexChange })
                target.processUpdate(UpdateResponse(ReplicationNormalizedModel, emitted))
                assertEquals("after", target.execute(ReplicationNormalizedModel.get(added.key)).values.single().values { name })
                assertEquals(listOf(added.key), target.execute(ReplicationNormalizedModel.scan(order = ReplicationNormalizedModel.ref { name }.ascending())).values.map { it.key })
            } finally { listener.cancelAndJoin() }
        } finally { source.close(); target.close() }
    }

    @Test
    fun replayUsesSourceIncrementingMapKeysAtTheirOriginalOperationPosition() = runTest {
        installIndexedDbForTests()
        suspend fun open(label: String) = IndexedDbDataStore.open("maryk-normalized-$label-${Random.nextInt()}", mapOf(1u to ReplicationNormalizedModel))
        for (initialReplay in listOf(false, true)) {
            val target = open("target")
            try {
                val key = Key<ReplicationNormalizedModel>(ByteArray(ReplicationNormalizedModel.Meta.keyByteSize) { 7 })
                val values = ReplicationNormalizedModel.create { name with "target"; entries with mapOf(2u to "retained") }
                target.processUpdate(UpdateResponse(ReplicationNormalizedModel, AdditionUpdate(key, 10uL, 10uL, 0, false, values)))
                val reference = ReplicationNormalizedModel.ref { entries }
                val changes = listOf(
                    Check(ReplicationNormalizedModel.ref { name } with "source-only precondition"),
                    IncMapChange(reference.change(addValues = listOf("first"))),
                    Change(reference.propertyDefinition.valueRef(42u, reference) with "later"),
                    IndexChange(emptyList()),
                    IncMapAddition(IncMapKeyAdditions(reference, listOf(42u), null)),
                )
                if (initialReplay) target.processUpdate(UpdateResponse(ReplicationNormalizedModel, InitialChangesUpdate(30uL,
                    listOf(DataObjectVersionedChange(key, changes = listOf(VersionedChanges(30uL, changes)))))))
                else target.processUpdate(UpdateResponse(ReplicationNormalizedModel, ChangeUpdate(key, 30uL, 0, changes)))
                assertEquals(mapOf(2u to "retained", 42u to "later"), target.execute(ReplicationNormalizedModel.get(key)).values.single().values { entries })
            } finally { target.close() }
        }
    }
}
