package maryk.core.models.migration

import kotlinx.datetime.LocalDateTime
import maryk.core.models.RootDataModel
import maryk.core.models.ValueDataModel
import maryk.core.models.key
import maryk.core.models.graph
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.ValueObjectDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.valueObject
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.graph.get
import maryk.core.properties.types.TimePrecision.NANOS
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.invoke
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.UInt32
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.change
import maryk.core.query.requests.TransactionRequest
import maryk.core.query.requests.add
import maryk.core.values.ObjectValues
import maryk.test.models.MarykTypeEnum
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

private class ReceiptPackedFloat(val number: Double) : ValueDataObject(toBytes(number)) {
    companion object : ValueDataModel<ReceiptPackedFloat, Companion>(ReceiptPackedFloat::class) {
        val number by number(1u, ReceiptPackedFloat::number, type = Float64)
        override fun invoke(values: ObjectValues<ReceiptPackedFloat, Companion>) = ReceiptPackedFloat(values(1u))
    }
}

private object ReceiptFloatModel : RootDataModel<ReceiptFloatModel>() {
    val packed by valueObject(1u, dataModel = ReceiptPackedFloat)
}

private object ReceiptReferenceModel : RootDataModel<ReceiptReferenceModel>() {
    val timestamps by map(1u, keyDefinition = DateTimeDefinition(precision = NANOS), valueDefinition = StringDefinition())
    val objects by map(2u, keyDefinition = ValueObjectDefinition(dataModel = ReceiptPackedFloat), valueDefinition = StringDefinition())
    val objectSet by set(3u, valueDefinition = ValueObjectDefinition(dataModel = ReceiptPackedFloat))
    val timeSet by set(4u, valueDefinition = DateTimeDefinition(precision = NANOS))
    val appends by incrementingMap(5u, keyNumberDescriptor = UInt32, valueDefinition = ListDefinition(valueDefinition = StringDefinition()))
}

class ReleasedTransportFingerprintTest {
    private val first = LocalDateTime(1970, 1, 1, 0, 0, 8, 1)
    private val second = LocalDateTime(1970, 1, 1, 0, 0, 9, 2)
    private val oldFloat = byteArrayOf(0xc0.toByte(), 0x10, 0, 0, 0, 0, 0, 0)

    private fun context() = RequestContext(DefinitionsContext(dataModels = mutableMapOf(
        ReceiptFloatModel.Meta.name to DataModelReference(ReceiptFloatModel),
        ReceiptReferenceModel.Meta.name to DataModelReference(ReceiptReferenceModel),
    )))

    private fun <T : Any, CX : IsPropertyContext> encode(
        definition: IsSerializablePropertyDefinition<T, CX>, value: T, context: CX,
    ): ByteArray {
        val cache = WriteCache()
        val bytes = ByteArray(definition.calculateTransportByteLengthWithKey(1, value, cache, context))
        var offset = 0
        definition.writeTransportBytesWithKey(1, value, cache, { bytes[offset++] = it }, context)
        assertEquals(bytes.size, offset)
        return bytes
    }

    @Test
    fun completeRequestReproducesIndependentReleasedValueObjectBytes() {
        val request = TransactionRequest("receipt-compat", listOf(ReceiptFloatModel.add(
            ReceiptFloatModel.key(ByteArray(16)) to ReceiptFloatModel.create { packed with ReceiptPackedFloat(-1.0) },
        )), commit = true)
        val context = context()
        fun encodeRequest(): ByteArray {
            val cache = WriteCache()
            val bytes = ByteArray(TransactionRequest.Serializer.calculateObjectProtoBufLength(request, cache, context))
            var offset = 0
            TransactionRequest.Serializer.writeObjectProtoBuf(request, cache, { bytes[offset++] = it }, context)
            assertEquals(bytes.size, offset)
            return bytes
        }
        val add = field(1, "ReceiptFloatModel".encodeToByteArray()) + field(2, field(1, oldFloat)) + field(3, ByteArray(16))
        val expected = field(1, "receipt-compat".encodeToByteArray()) + field(2, field(1, add)) +
            byteArrayOf(0x28, 1) + field(32768 + 6, byteArrayOf()) + byteArrayOf(0x38, 0, 0x40, 0)
        val current = encodeRequest()
        assertContentEquals(expected, context.writeReleasedTransportForFingerprint(::encodeRequest))
        assertFalse(current.contentEquals(expected))
        assertContentEquals(current, encodeRequest())
    }

    @Test
    fun nanosScalarPackedCollectionsAndMapEntriesReproduceReleasedFraming() {
        val definition = DateTimeDefinition(precision = NANOS)
        val context = context()
        context.writeReleasedTransportForFingerprint {
            assertContentEquals(byteArrayOf(8, 8, 1), encode(definition, first, context))
            assertContentEquals(field(1, byteArrayOf(8, 1, 9, 2)), encode(ListDefinition(valueDefinition = definition), listOf(first, second), context))
            assertContentEquals(field(1, byteArrayOf(8, 1, 9, 2)), encode(SetDefinition(valueDefinition = definition), linkedSetOf(first, second), context))
            assertContentEquals(field(1, byteArrayOf(8, 8, 1, 16, 9, 2)), encode(MapDefinition(keyDefinition = definition, valueDefinition = definition), mapOf(first to second), context))
            assertContentEquals(field(1, byteArrayOf()), encode(ListDefinition(valueDefinition = definition), emptyList(), context))
        }
        assertContentEquals(field(1, byteArrayOf(8, 8, 16, 1)), encode(definition, first, context))
    }

    @Test
    fun fixedMapAndSetReferenceKeysUseReleasedTransportCodecs() {
        val context = context()
        val definition = ContextualPropertyReferenceDefinition<RequestContext>(contextualResolver = { ReceiptReferenceModel })
        context.writeReleasedTransportForFingerprint {
            assertContentEquals(field(1, byteArrayOf(1, 0, 8, 1)), encode(definition, ReceiptReferenceModel.ref { timestamps at first }, context))
            assertContentEquals(field(1, byteArrayOf(2, 2, 8) + oldFloat), encode(definition, ReceiptReferenceModel.ref { objects at ReceiptPackedFloat(-1.0) }, context))
            assertContentEquals(field(1, byteArrayOf(2, 10, 8) + oldFloat), encode(definition, ReceiptReferenceModel.ref { objects key ReceiptPackedFloat(-1.0) }, context))
            assertContentEquals(field(1, byteArrayOf(3, 2, 8) + oldFloat), encode(definition, ReceiptReferenceModel.ref { objectSet item ReceiptPackedFloat(-1.0) }, context))
            assertContentEquals(field(1, byteArrayOf(4, 0, 8, 1)), encode(definition, ReceiptReferenceModel.ref { timeSet item first }, context))
        }
    }

    @Test
    fun valueObjectMapKeysAndValuesChangeWhileOrdinaryFloatingTransportDoesNot() {
        val context = context()
        val packed = ValueObjectDefinition(dataModel = ReceiptPackedFloat)
        val value = ReceiptPackedFloat(-1.0)
        val number = NumberDefinition(type = Float64)
        val currentNumber = encode(number, -1.0, context)
        context.writeReleasedTransportForFingerprint {
            assertContentEquals(field(1, field(1, oldFloat) + field(2, oldFloat)),
                encode(MapDefinition(keyDefinition = packed, valueDefinition = packed), mapOf(value to value), context))
            assertContentEquals(currentNumber, encode(number, -1.0, context))
        }
    }

    @Test
    fun selectionGraphsForwardReleasedModeThroughTheirOwnContexts() {
        val context = context().apply { dataModel = ReceiptReferenceModel }
        val graph: RootPropRefGraph<*> = ReceiptReferenceModel.graph {
            listOf(timestamps[first], objects[ReceiptPackedFloat(-1.0)])
        }
        val timeItem = field(1, byteArrayOf(1)) + byteArrayOf(16, 8, 1) + field(32768 + 3, byteArrayOf())
        val packedItem = field(1, byteArrayOf(2)) + field(2, oldFloat) + field(32768 + 3, byteArrayOf())
        val expected = field(1, field(1, field(3, timeItem)) + field(1, field(3, packedItem)))
        val definition = EmbeddedObjectDefinition(dataModel = { RootPropRefGraph })
        assertContentEquals(expected, context.writeReleasedTransportForFingerprint { encode(definition, graph, context) })
        assertFalse(expected.contentEquals(encode(definition, graph, context)))
    }

    @Test
    fun injectiveNestedEmptyMapValuesOmitNewPresenceMarkers() {
        val definition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = ListDefinition(valueDefinition = StringDefinition()))
        val context = context()
        val value = mapOf("k" to emptyList<String>())
        val expected = field(1, field(1, "k".encodeToByteArray()))
        assertContentEquals(expected, context.writeReleasedTransportForFingerprint { encode(definition, value, context) })
        assertFalse(expected.contentEquals(encode(definition, value, context)))
    }

    @Test
    fun ambiguousTypedEmptyAndFlattenedAppendsFailExplicitlyAndRestoreScope() {
        val context = context().apply { dataModel = ReceiptReferenceModel }
        val typed = MultiTypeDefinition(typeEnum = MarykTypeEnum)
        assertFailsWith<ReleasedTransportFingerprintException> {
            context.writeReleasedTransportForFingerprint { encode(typed, MarykTypeEnum.T4(emptyList()), context) }
        }
        val request = IncMapChange(ReceiptReferenceModel.ref { appends }.change(listOf(listOf("a", "b"))))
        assertFailsWith<ReleasedTransportFingerprintException> {
            context.writeReleasedTransportForFingerprint { IncMapChange.Serializer.calculateObjectProtoBufLength(request, WriteCache(), context) }
        }
        for (values in listOf(null, emptyList<List<String>>())) {
            val noOp = IncMapChange(ReceiptReferenceModel.ref { appends }.change(values))
            context.writeReleasedTransportForFingerprint {
                val cache = WriteCache()
                val bytes = ByteArray(IncMapChange.Serializer.calculateObjectProtoBufLength(noOp, cache, context))
                var offset = 0
                IncMapChange.Serializer.writeObjectProtoBuf(noOp, cache, { bytes[offset++] = it }, context)
                assertEquals(bytes.size, offset)
            }
        }
        // The current writer remains available after either unsupported released encoding.
        val cache = WriteCache()
        val size = IncMapChange.Serializer.calculateObjectProtoBufLength(request, cache, context)
        val bytes = ByteArray(size)
        var offset = 0
        IncMapChange.Serializer.writeObjectProtoBuf(request, cache, { bytes[offset++] = it }, context)
        assertEquals(size, offset)
    }

    private fun field(tag: Int, payload: ByteArray): ByteArray =
        varint((tag shl 3) or 2) + varint(payload.size) + payload

    private fun varint(value: Int): ByteArray = buildList {
        var remaining = value
        while (remaining > 127) {
            add(((remaining and 127) or 128).toByte())
            remaining = remaining ushr 7
        }
        add(remaining.toByte())
    }.toByteArray()
}
