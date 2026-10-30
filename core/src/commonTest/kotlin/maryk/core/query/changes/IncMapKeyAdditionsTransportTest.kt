package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.models.RootDataModel
import maryk.core.models.migration.hasLegacyTransportStorage
import maryk.core.models.migration.readLegacyTransportStorage
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.types.numeric.UInt32
import maryk.core.protobuf.WriteCache
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.query.RequestContext
import maryk.core.query.responses.ChangeResponse
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.json.JsonReader
import maryk.lib.exceptions.ParseException
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

private object CollectionAppendModel : RootDataModel<CollectionAppendModel>() {
    val items by incrementingMap(1u, keyNumberDescriptor = UInt32, valueDefinition = ListDefinition(valueDefinition = StringDefinition()))
    val sets by incrementingMap(2u, keyNumberDescriptor = UInt32, valueDefinition = SetDefinition(valueDefinition = StringDefinition()))
    val maps by incrementingMap(3u, keyNumberDescriptor = UInt32, valueDefinition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = StringDefinition()))
}

private object NestedCollectionAppendModel : RootDataModel<NestedCollectionAppendModel>() {
    val nested by map(1u, keyDefinition = StringDefinition(), valueDefinition = EmbeddedValuesDefinition(dataModel = { CollectionAppendModel }))
}

private object ScalarAppendModel : RootDataModel<ScalarAppendModel>() {
    val items by incrementingMap(1u, keyNumberDescriptor = UInt32, valueDefinition = StringDefinition())
}

class IncMapKeyAdditionsTransportTest {
    private fun context(collectInputs: Boolean = false) = RequestContext(
        mapOf(TestMarykModel.Meta.name to DataModelReference(TestMarykModel)),
        dataModel = TestMarykModel,
    ).apply {
        if (collectInputs) {
            collectIncMapChange(IncMapChange(TestMarykModel.ref { incMap }.change(listOf("failed-earlier-input"))))
            collectIncMapChange(IncMapChange(TestMarykModel.ref { incMap }.change(listOf("first"))))
            collectIncMapChange(IncMapChange(TestMarykModel.ref { incMap }.change(listOf("second"))))
        }
    }

    @Test
    fun repeatedSameReferenceResponsesCarryTheirExactValuesWithoutRequestInputs() {
        val response = ChangeResponse(TestMarykModel, listOf(
            ChangeSuccess(10uL, listOf(IncMapAddition(
                IncMapKeyAdditions(TestMarykModel.ref { incMap }, listOf(1u), listOf("first")),
                IncMapKeyAdditions(TestMarykModel.ref { incMap }, listOf(2u), listOf("second")),
            ))),
        ))
        // Both a fresh reader and a mismatched/older request context must use the response payload.
        for (collect in listOf(false, true)) {
            checkProtoBufConversion(response, ChangeResponse, { context(collect) }, resetContextBeforeRead = true)
            checkJsonConversion(response, ChangeResponse, { context(collect) })
        }
    }

    @Test
    fun collectionValuedAppendsRetainEachElementBoundary() {
        val addition = IncMapAddition(IncMapKeyAdditions(
            CollectionAppendModel.ref { items }, listOf(1u, 2u), listOf(emptyList<String>(), listOf("a", "b")),
        ))
        fun context() = RequestContext(
            mapOf(CollectionAppendModel.Meta.name to DataModelReference(CollectionAppendModel)),
            dataModel = CollectionAppendModel,
        )
        checkProtoBufConversion(addition, IncMapAddition, ::context, resetContextBeforeRead = true)
        checkJsonConversion(addition, IncMapAddition, ::context)
    }

    @Test
    fun collectionValuedAppendCommandsPreserveCountAndOrder() {
        val request = IncMapChange(
            CollectionAppendModel.ref { items }.change(listOf(listOf("a", "b"), emptyList(), listOf("c"))),
            CollectionAppendModel.ref { sets }.change(listOf(setOf("a", "b"), emptySet(), setOf("c"))),
            CollectionAppendModel.ref { maps }.change(listOf(mapOf("a" to "b", "c" to "d"), emptyMap(), mapOf("e" to "f"))),
        )
        fun context() = RequestContext(
            mapOf(CollectionAppendModel.Meta.name to DataModelReference(CollectionAppendModel)),
            dataModel = CollectionAppendModel,
        )
        var decoded: IncMapChange? = null
        val checker = { converted: IncMapChange, original: IncMapChange ->
            assertEquals(original, converted)
            decoded = converted
        }
        checkProtoBufConversion(request, IncMapChange, ::context, checker, resetContextBeforeRead = true)
        checkJsonConversion(request, IncMapChange, ::context, checker)
        val original = CollectionAppendModel.create { items with emptyMap(); sets with emptyMap(); maps with emptyMap() }
        val changed = original.change(assertNotNull(decoded))
        assertEquals(listOf(listOf("a", "b"), emptyList(), listOf("c")), assertNotNull(changed { items }).values.toList())
        assertEquals(listOf(setOf("a", "b"), emptySet(), setOf("c")), assertNotNull(changed { sets }).values.toList())
        assertEquals(listOf(mapOf("a" to "b", "c" to "d"), emptyMap(), mapOf("e" to "f")), assertNotNull(changed { maps }).values.toList())
        assertEquals(listOf(1u, 2u, 3u), assertNotNull(changed { items }).keys.toList())
    }

    @Test
    fun affectedLegacyCommandsAreRejectedWithoutGuessingBoundaries() {
        assertTrue(CollectionAppendModel.hasLegacyTransportStorage())
        assertTrue(NestedCollectionAppendModel.hasLegacyTransportStorage())
        assertFalse(ScalarAppendModel.hasLegacyTransportStorage())
        val reference = CollectionAppendModel.ref { items }
        val cache = WriteCache()
        val referenceBytes = ByteArray(reference.calculateTransportByteLength(cache))
        var offset = 0
        reference.writeTransportBytes(cache) { referenceBytes[offset++] = it }
        // Released bytes cannot distinguish [["a", "b"]] from [["a"], ["b"]].
        val legacy = buildList {
            ProtoBuf.writeKey(1u, LENGTH_DELIMITED, ::add)
            referenceBytes.size.writeVarBytes(::add)
            referenceBytes.forEach(::add)
            for (value in listOf("a", "b")) {
                ProtoBuf.writeKey(2u, LENGTH_DELIMITED, ::add)
                add(1)
                add(value.single().code.toByte())
            }
        }.toByteArray()
        val context = RequestContext(
            mapOf(CollectionAppendModel.Meta.name to DataModelReference(CollectionAppendModel)),
            dataModel = CollectionAppendModel,
        )
        offset = 0
        val error = assertFailsWith<ParseException> {
            context.readLegacyTransportStorage {
                IncMapValueChanges.Serializer.readProtoBuf(legacy.size, { legacy[offset++] }, context)
            }
        }
        assertTrue(error.message!!.contains("ambiguous element boundaries"))
        // The legacy scope is restored, and absence/empty append lists remain no-ops.
        for (change in listOf(reference.change(null), reference.change(emptyList()))) {
            val bytes = encodeCommand(change, context)
            offset = 0
            val restored = context.readLegacyTransportStorage {
                IncMapValueChanges.Serializer.readProtoBuf(bytes.size, { bytes[offset++] }, context).toDataObject()
            }
            assertEquals(change.addValues, restored.addValues)
        }
    }

    @Test
    fun scalarAppendBytesKeepTheReleasedRepresentation() {
        val reference = ScalarAppendModel.ref { items }
        val command = reference.change(listOf("a", "b"))
        val context = RequestContext(
            mapOf(ScalarAppendModel.Meta.name to DataModelReference(ScalarAppendModel)),
            dataModel = ScalarAppendModel,
        )
        val cache = WriteCache()
        val referenceBytes = ByteArray(reference.calculateTransportByteLength(cache))
        var offset = 0
        reference.writeTransportBytes(cache) { referenceBytes[offset++] = it }
        val expected = buildList {
            ProtoBuf.writeKey(1u, LENGTH_DELIMITED, ::add)
            referenceBytes.size.writeVarBytes(::add)
            referenceBytes.forEach(::add)
            for (value in listOf("a", "b")) {
                ProtoBuf.writeKey(2u, LENGTH_DELIMITED, ::add)
                add(1)
                add(value.single().code.toByte())
            }
        }.toByteArray()
        assertContentEquals(expected, encodeCommand(command, context))
        offset = 0
        assertEquals(command.addValues, context.readLegacyTransportStorage {
            IncMapValueChanges.Serializer.readProtoBuf(expected.size, { expected[offset++] }, context).toDataObject().addValues
        })
    }

    @Suppress("UNCHECKED_CAST")
    private fun encodeCommand(command: IncMapValueChanges<*, *>, context: RequestContext): ByteArray {
        val typed = command as IncMapValueChanges<out Comparable<Any>, out Any>
        val cache = WriteCache()
        val size = IncMapValueChanges.Serializer.calculateObjectProtoBufLength(typed, cache, context)
        val bytes = ByteArray(size)
        var offset = 0
        IncMapValueChanges.Serializer.writeObjectProtoBuf(typed, cache, { bytes[offset++] = it }, context)
        assertEquals(size, offset)
        return bytes
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun mismatchedAddedKeysAndValuesAreRejected() {
        val malformed = IncMapKeyAdditions(TestMarykModel.ref { incMap }, listOf(1u, 2u), listOf("one")) as
            IncMapKeyAdditions<out Comparable<Any>, out Any>
        assertFailsWith<ParseException> {
            IncMapKeyAdditions.Serializer.calculateObjectProtoBufLength(malformed, WriteCache(), context())
        }
        val json = """{"reference":"incMap","addedKeys":[1,2],"addedValues":["one"]}"""
        val reader = JsonReader(json)
        assertFailsWith<ParseException> { IncMapKeyAdditions.Serializer.readJson(reader, context()) }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun explicitEmptyAddedValuesDoNotUseLegacyRequestFallback() {
        val empty = IncMapKeyAdditions(TestMarykModel.ref { incMap }, emptyList<UInt>(), emptyList<String>()) as
            IncMapKeyAdditions<out Comparable<Any>, out Any>
        checkProtoBufConversion(empty, IncMapKeyAdditions, { context(true) }, resetContextBeforeRead = true)
        checkJsonConversion(empty, IncMapKeyAdditions, { context(true) })
        assertEquals(emptyList(), empty.addedValues)
    }
}
