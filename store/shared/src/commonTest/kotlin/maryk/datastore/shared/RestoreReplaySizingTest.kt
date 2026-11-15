package maryk.datastore.shared

import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.Bytes
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private object RestoreSizingModel : RootDataModel<RestoreSizingModel>() {
    val entries by list(1u, valueDefinition = StringDefinition())
}

private object RestoreOtherSizingModel : RootDataModel<RestoreOtherSizingModel>() {
    val value by number(1u, type = SInt32)
}

class RestoreReplaySizingTest {
    @Test
    fun stagedStringBoundsCoverVersionSubsetsAndEnvelopeLengthBoundaries() {
        val name = RestoreSizingModel.Meta.name
        val codec = RestoreCodec(mapOf(name to RestoreSizingModel))
        for ((count, length) in listOf(1 to 0, 1 to 127, 1 to 128, 1 to 16384, 2 to 100, 127 to 1, 128 to 1)) {
            val staged = StagedBackupReader(codec, DataStoreRestoreOptions())
            repeat(count) { id ->
                val key = RestoreSizingModel.key(ByteArray(16).also {
                    it[14] = (id shr 8).toByte()
                    it[15] = id.toByte()
                })
                @Suppress("UNCHECKED_CAST")
                val record = DataObjectVersionedChange(key, sortingKey = Bytes(ByteArray(128)), changes = listOf(
                    VersionedChanges(127uL, listOf(ObjectCreate, Change(RestoreSizingModel.entries.ref() with listOf("é".repeat(length))))),
                    VersionedChanges(128uL, listOf(Change(RestoreSizingModel.entries.ref() with emptyList<String>()))),
                )) as DataObjectVersionedChange<IsRootDataModel>
                staged.add(name, record)
            }
            for (events in staged.orderedEvents().groupBy { it.version }.values) {
                val bound = codec.replayByteUpperBound(name, events)
                for (snapshot in listOf(129uL, ULong.MAX_VALUE - 2uL)) {
                    val exact = codec.replaySize(name, snapshot, events.map { it.record })
                    assertTrue(bound >= exact, "count=$count, length=$length, snapshot=$snapshot: $bound < $exact")
                }
            }
        }
    }

    @Test
    fun incrementingMapPayloadsRequireExactSizing() {
        val name = TestMarykModel.Meta.name
        val codec = RestoreCodec(mapOf(name to TestMarykModel))
        val reference = TestMarykModel.ref { incMap }
        val value = "context value".repeat(128)
        @Suppress("UNCHECKED_CAST")
        val record = DataObjectVersionedChange(TestMarykModel.key(ByteArray(TestMarykModel.Meta.keyByteSize)), changes = listOf(
            VersionedChanges(100uL, listOf(ObjectCreate,
                IncMapChange(reference.change(addValues = listOf(value))),
                IncMapAddition(IncMapKeyAdditions(reference, listOf(42u), listOf(value))))),
        )) as DataObjectVersionedChange<IsRootDataModel>
        val staged = StagedBackupReader(codec, DataStoreRestoreOptions())
        staged.add(name, record)
        val event = staged.orderedEvents().single()
        val addition = event.record.changes.single().changes.filterIsInstance<IncMapAddition>().single().additions.single()
        assertEquals(listOf(value), addition.addedValues)
        assertEquals(-1, event.serializedRecordBytes)
        assertFailsWith<IllegalArgumentException> { codec.replayByteUpperBound(name, listOf(event)) }
    }

    @Test
    fun otherPayloadShapesRequireExactSizing() {
        val name = RestoreOtherSizingModel.Meta.name
        val codec = RestoreCodec(mapOf(name to RestoreOtherSizingModel))
        val staged = StagedBackupReader(codec, DataStoreRestoreOptions())
        @Suppress("UNCHECKED_CAST")
        val record = DataObjectVersionedChange(RestoreOtherSizingModel.key(ByteArray(16)), changes = listOf(
            VersionedChanges(100uL, listOf(ObjectCreate, Change(RestoreOtherSizingModel.ref { value } with 3))),
        )) as DataObjectVersionedChange<IsRootDataModel>
        staged.add(name, record)
        val events = staged.orderedEvents()
        assertEquals(-1, events.single().serializedRecordBytes)
        assertFailsWith<IllegalArgumentException> { codec.replayByteUpperBound(name, events) }
    }
}
