package maryk.datastore.memory

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.map
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

private object ReplicationNormalizedModel : RootDataModel<ReplicationNormalizedModel>(indexes = { listOf(ReplicationNormalizedModel.name.ref()) }) {
    val name by string(1u)
    val entries by incrementingMap(2u, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition())
}

class ReplicationNormalizationTest {
    @Test
    fun initialCreationReconstructsFlattenedEmbeddedAndMapValues() = runTest {
        val target = InMemoryDataStore.open(dataModelsById = mapOf(1u to EmbeddedCreationReplicationModel))
        try {
            val child = CreationReplicationDetails.create {
                name with "retained child"
                labels with mapOf("nested" to "map value")
            }
            val values = EmbeddedCreationReplicationModel.create {
                details with child
                children with mapOf("fixed" to child)
            }
            val key = Key<EmbeddedCreationReplicationModel>(ByteArray(EmbeddedCreationReplicationModel.Meta.keyByteSize) { 13 })
            target.processUpdate(UpdateResponse(EmbeddedCreationReplicationModel, InitialChangesUpdate(30uL,
                listOf(DataObjectVersionedChange(key, changes = listOf(VersionedChanges(30uL,
                    listOf(ObjectCreate) + values.toChanges().toList(),
                )))),
            )))
            assertEquals(values, target.execute(EmbeddedCreationReplicationModel.get(key)).values.single().values)
        } finally { target.close() }
    }

    @Test
    fun creationReplayInitializesMissingIncrementingMap() = runTest {
        val target = InMemoryDataStore.open(dataModelsById = mapOf(1u to ReplicationNormalizedModel))
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
        suspend fun open() = InMemoryDataStore.open(dataModelsById = mapOf(1u to ReplicationNormalizedModel))
        val source = open()
        val target = open()
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
        suspend fun open() = InMemoryDataStore.open(dataModelsById = mapOf(1u to ReplicationNormalizedModel))
        for (initialReplay in listOf(false, true)) {
            val target = open()
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

private object CreationReplicationDetails : DataModel<CreationReplicationDetails>() {
    val name by string(1u)
    val labels by map(2u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
}

private object EmbeddedCreationReplicationModel : RootDataModel<EmbeddedCreationReplicationModel>() {
    val details by embed(1u, dataModel = { CreationReplicationDetails })
    val children by map(2u, keyDefinition = StringDefinition(),
        valueDefinition = EmbeddedValuesDefinition(dataModel = { CreationReplicationDetails }))
}
