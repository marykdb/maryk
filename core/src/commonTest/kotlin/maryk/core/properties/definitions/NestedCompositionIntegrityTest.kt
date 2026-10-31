package maryk.core.properties.definitions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.DataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsTypedObjectDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.ObjectDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.TypedValuesDataModel
import maryk.core.models.migration.writeReleasedTransportForFingerprint
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextCollectionTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextInjectCollectionOnWriteDefinition
import maryk.core.properties.definitions.contextual.ContextualCollectionDefinition
import maryk.core.properties.definitions.contextual.ContextualEmbeddedObjectDefinition
import maryk.core.properties.definitions.contextual.ContextualEmbeddedValuesDefinition
import maryk.core.properties.definitions.contextual.ContextualMapDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyValueDefinition
import maryk.core.properties.definitions.contextual.ContextualSubDefinition
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.invoke
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.SInt32
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCache
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.core.values.Values
import maryk.core.values.ValuesImpl
import maryk.lib.exceptions.ParseException

private object CompositionLeaf : DataModel<CompositionLeaf>() {
    val first by string(1u, required = false)
    val second by number(2u, type = SInt32, required = false)
    val items by list(3u, valueDefinition = StringDefinition(), required = false)
    val floating by number(4u, type = Float64, required = false)
}

private val leafCase = MultiTypeEnum.invoke(1u, "Leaf", EmbeddedValuesDefinition(dataModel = { CompositionLeaf }))
private val leafTypes = MultiTypeEnumDefinition("CompositionLeafTypes", { listOf(leafCase) })
private val leafTypedDefinition = MultiTypeDefinition(typeEnum = leafTypes)
private val innerCase = MultiTypeEnum.invoke(1u, "Inner", leafTypedDefinition)
private val outerTypes = MultiTypeEnumDefinition("CompositionOuterTypes", { listOf(innerCase) })

private data class CompositionObject(val first: String, val second: Int) {
    companion object : ObjectDataModel<CompositionObject, Companion>(CompositionObject::class) {
        val first by string(1u, CompositionObject::first)
        val second by number(2u, CompositionObject::second, type = SInt32)
        var reads = 0
        override val Serializer = object : ObjectDataModelSerializer<CompositionObject, Companion, IsPropertyContext, IsPropertyContext>(this) {
            override fun readProtoBuf(length: Int, reader: () -> Byte, context: IsPropertyContext?): ObjectValues<CompositionObject, Companion> {
                reads++
                return super.readProtoBuf(length, reader, context)
            }
        }
        override fun invoke(values: ObjectValues<CompositionObject, Companion>) = CompositionObject(values(first.index), values(second.index))
    }
}

private object CompositionEnvelope : DataModel<CompositionEnvelope>() {
    val record by embed(1u, dataModel = { CompositionRecord })
}

private object CompositionRecord : RootDataModel<CompositionRecord>() {
    val embedded by embed(1u, dataModel = { CompositionLeaf }, required = false)
    val mapped by map(2u, keyDefinition = StringDefinition(), valueDefinition = EmbeddedValuesDefinition(dataModel = { CompositionLeaf }), required = false)
    val typed by multiType(3u, typeEnum = leafTypes, required = false)
    val mixed by map(4u, keyDefinition = StringDefinition(), valueDefinition = leafTypedDefinition, required = false)
    val nested by multiType(5u, typeEnum = outerTypes, required = false)
    @Suppress("UNCHECKED_CAST")
    val objectValue by embedObject(7u, dataModel = {
        CompositionObject as IsTypedObjectDataModel<CompositionObject, IsObjectDataModel<CompositionObject>, IsPropertyContext, IsPropertyContext>
    }, required = false)
    val sets by set(8u, valueDefinition = StringDefinition(), required = false)
    val items by list(6u, valueDefinition = StringDefinition(), required = false)
}

class NestedCompositionIntegrityTest {
    private val first = CompositionLeaf.create(setDefaults = false) {
        first with "retained"
        items with listOf("before")
        floating with Double.fromBits(0x7ff8000000000001L)
    }
    private val second = CompositionLeaf.create(setDefaults = false) {
        second with 42
        items with listOf("after")
    }
    private val expected = CompositionLeaf.create(setDefaults = false) {
        first with "retained"
        second with 42
        items with listOf("before", "after")
        floating with Double.fromBits(0x7ff8000000000001L)
    }

    @Test
    fun fragmentedEmbeddedAndTypedRecordsRetainAllFields() {
        val left = CompositionRecord.create(setDefaults = false) {
            embedded with first
            typed with leafCase(first)
            nested with innerCase(leafCase(first))
        }
        val right = CompositionRecord.create(setDefaults = false) {
            embedded with second
            typed with leafCase(second)
            nested with innerCase(leafCase(second))
        }
        val read = decode(encode(left) + encode(right))
        assertEquals(expected, read { embedded })
        assertEquals(leafCase(expected), read { typed })
        assertEquals(innerCase(leafCase(expected)), read { nested })
        val embedded = read { embedded }!!
        assertEquals(0x7ff8000000000001L, embedded { floating }!!.toRawBits())
    }

    @Test
    fun fragmentsWithinOneMapEntryMergeButSeparateEntriesReplace() {
        val firstBytes = leafBytes(first)
        val secondBytes = leafBytes(second)
        val key = field(1u, "key".encodeToByteArray())
        val entry = key + field(2u, firstBytes) + field(2u, secondBytes)
        assertEquals(mapOf("key" to expected), decode(field(2u, entry)).get { mapped })
        val mixedEntry = key + field(2u, leafTypedDefinition.toTransportByteArray(leafCase(first))) +
            field(2u, leafTypedDefinition.toTransportByteArray(leafCase(second)))
        assertEquals(mapOf("key" to leafCase(expected)), decode(field(4u, mixedEntry)).get { mixed })
        val separate = field(2u, key + field(2u, firstBytes)) + field(2u, key + field(2u, secondBytes))
        assertEquals(mapOf("key" to second), decode(separate).get { mapped })
    }

    @Test
    fun wrappedCollectionsRetainEmptyAndNonemptyReplacementBoundaries() {
        val list = ListDefinition(valueDefinition = StringDefinition())
        val wrapped = ContextualSubDefinition(contextualResolver = { _: IsPropertyContext? -> list })
        val replacement = ContextualPropertyValueDefinition(contextualResolver = { _: IsPropertyContext? -> wrapped })
        for (value in listOf(emptyList(), listOf(""), listOf("one", "two"))) {
            val bytes = replacement.toTransportByteArray(value)
            var offset = 0
            assertEquals(value, replacement.readTransportBytes(bytes.size, { bytes[offset++] }))
            assertEquals(bytes.size, offset)
        }
    }

    @Test
    fun conflictingEmptyMarkersAreRejectedInEitherOrder() {
        val marker = field(EMPTY_COLLECTION_TAG_OFFSET + 6u, byteArrayOf())
        val nonempty = encode(CompositionRecord.create(setDefaults = false) { items with listOf("one") })
        for (bytes in listOf(marker + nonempty, nonempty + marker)) {
            assertFailsWith<ParseException> { decode(bytes) }
        }
    }

    @Test
    fun prefixedScalarStorageCanBeDecodedWithoutRepeatingItsLength() {
        val definition = NumberDefinition(type = Float64)
        val bytes = definition.toStorageBytes(-0.0, 1, 2, 3)
        assertEquals((-0.0).toRawBits(), definition.fromStorageBytes(bytes, offset = 3).toRawBits())
    }

    @Test
    fun rawReplacementDoesNotRetainDeletedEmbeddedFields() {
        val original = CompositionRecord.create(setDefaults = false) { embedded with first }
        val replaced = original.copyWithValue(CompositionRecord.embedded.index, second)
        assertEquals(second, replaced { embedded })
        assertEquals(first, original { embedded })
        assertEquals(null, replaced.copyWithValue(CompositionRecord.embedded.index, Unit).get { embedded })
    }

    @Test
    fun partialObjectsAreConstructedAfterTheirFragmentsAndKeepReadHooks() {
        CompositionObject.reads = 0
        val fragments = field(7u, field(1u, "required".encodeToByteArray())) +
            field(7u, byteArrayOf(16, 84))
        assertEquals(CompositionObject("required", 42), decode(fragments).get { objectValue })
        assertEquals(2, CompositionObject.reads)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun contextualEmbeddedDefinitionsContinueWithoutChangingEarlierValues() {
        val definition = ContextualEmbeddedValuesDefinition<IsPropertyContext> {
            CompositionLeaf as TypedValuesDataModel<IsValuesDataModel>
        }
        val bytes = leafBytes(second)
        var offset = 0
        assertEquals(expected as Any, definition.readTransportBytes(bytes.size, { bytes[offset++] }, earlierValue = (first as Any) as ValuesImpl))
        assertEquals(listOf("before"), first.get { items })
        val malformed = bytes + byteArrayOf(10, 5, 1)
        offset = 0
        assertFails { definition.readTransportBytes(malformed.size, { malformed[offset++] }, earlierValue = (first as Any) as ValuesImpl) }
        assertEquals(listOf("before"), first.get { items })

        val objects = ContextualEmbeddedObjectDefinition<IsPropertyContext> {
            CompositionObject as ObjectDataModel<Any, *>
        }
        val objectBytes = byteArrayOf(16, 84)
        offset = 0
        assertEquals(CompositionObject("retained", 42), objects.readTransportBytes(objectBytes.size,
            { objectBytes[offset++] }, earlierValue = CompositionObject("retained", 1)))
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun embeddedSchemaFragmentsReplayCapturedNumericContext() {
        val definition = EmbeddedObjectDefinition(dataModel = { NumberDefinition.Model })
        val number = NumberDefinition(type = SInt32, minValue = -42, maxValue = 42)
        val cache = WriteCache()
        val context = definition.dataModel.Serializer.transformContext(null)
        val bytes = ByteArray(definition.dataModel.Serializer.calculateObjectProtoBufLength(number, cache, context))
        var offset = 0
        definition.dataModel.Serializer.writeObjectProtoBuf(number, cache, { bytes[offset++] = it }, context)
        val fields = mutableListOf<ByteArray>()
        offset = 0
        while (offset < bytes.size) {
            val start = offset
            val key = ProtoBuf.readKey { bytes[offset++] }
            ProtoBuf.skipField(key) { bytes[offset++] }
            fields += bytes.copyOfRange(start, offset)
        }
        val typeBytes = fields.first { var i = 0; ProtoBuf.readKey { it[i++] }.tag == 4u }
        val rest = fields.filterNot { it === typeBytes }.fold(byteArrayOf()) { acc, next -> acc + next }
        offset = 0
        val earlier = definition.readTransportBytesToValues(typeBytes.size, { typeBytes[offset++] }, null)
        offset = 0
        val read = definition.readTransportBytesToValues(rest.size, { rest[offset++] }, null, earlier).toDataObject()
        assertEquals(number, read)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun wrappedListSetAndMapCompositionsPreserveCompleteValueBoundaries() {
        fun <T : Any> check(definition: IsSerializablePropertyDefinition<T, IsPropertyContext>, values: List<T>) {
            val value = ContextualPropertyValueDefinition(contextualResolver = { _: IsPropertyContext? -> definition })
            val twice = ContextualPropertyValueDefinition(contextualResolver = { _: IsPropertyContext? -> value })
            for (codec in listOf(value, twice)) for (item in values) {
                val bytes = codec.toTransportByteArray(item)
                var offset = 0
                assertEquals(item, codec.readTransportBytes(bytes.size, { bytes[offset++] }, earlierValue = values.last()))
                assertEquals(bytes.size, offset)
            }
        }
        val strings = ListDefinition(valueDefinition = StringDefinition())
        val transformed = ContextCollectionTransformerDefinition<String, List<String>, IsPropertyContext, IsPropertyContext>(strings) { it }
        check(ContextualSubDefinition(contextualResolver = { _: IsPropertyContext? -> strings }), listOf(emptyList(), listOf(""), listOf("a", "b")))
        check(transformed, listOf(emptyList(), listOf("a", "b")))
        check(CompositionRecord.items, listOf(emptyList(), listOf(""), listOf("a", "b")))
        check(ContextualCollectionDefinition<IsPropertyContext>(contextualResolver = { strings as IsSerializablePropertyDefinition<Collection<Any>, IsPropertyContext> }), listOf(emptyList(), listOf("a", "b")))
        check(ContextualSubDefinition(contextualResolver = { _: IsPropertyContext? -> SetDefinition(valueDefinition = StringDefinition()) }), listOf(emptySet(), setOf(""), setOf("a", "b")))
        val maps = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = BooleanDefinition())
        check(ContextualMapDefinition<String, Boolean, IsPropertyContext>(contextualResolver = { maps }),
            listOf(emptyMap(), mapOf("one" to true, "two" to false)))
    }

    @Test
    fun emptyMarkersRejectDuplicatesAndConflictsForEveryCollectionShape() {
        val entries = listOf(
            6u to encode(CompositionRecord.create(setDefaults = false) { items with listOf("one") }),
            8u to encode(CompositionRecord.create(setDefaults = false) { sets with setOf("one") }),
            2u to encode(CompositionRecord.create(setDefaults = false) { mapped with mapOf("one" to first) }),
        )
        for ((index, value) in entries) {
            val marker = field(EMPTY_COLLECTION_TAG_OFFSET + index, byteArrayOf())
            for (bytes in listOf(marker + value, value + marker, marker + marker)) {
                assertFailsWith<ParseException>(index.toString()) { decode(bytes) }
                assertFailsWith<ParseException>(index.toString()) {
                    val nested = field(1u, bytes)
                    var offset = 0
                    CompositionEnvelope.Serializer.readProtoBuf(nested.size, { nested[offset++] })
                }
            }
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun contextualContinuationRejectsValuesFromAnotherResolvedModel() {
        val definition = ContextualEmbeddedValuesDefinition<IsPropertyContext> {
            CompositionRecord as TypedValuesDataModel<IsValuesDataModel>
        }
        assertFailsWith<ParseException> {
            definition.readTransportBytes(0, { error("No bytes expected") }, earlierValue = (first as Any) as ValuesImpl)
        }
    }

    @Test
    fun emptyEmbeddedFragmentsDoNotEraseCollectionsOrDependOnFragmentOrder() {
        val definition = EmbeddedValuesDefinition(dataModel = { CompositionRecord })
        val nonempty = CompositionRecord.create(setDefaults = false) { items with listOf("one") }
        val empty = CompositionRecord.create(setDefaults = false) { items with emptyList() }
        for ((previous, incoming) in listOf(nonempty to empty, empty to nonempty)) {
            val bytes = encode(incoming)
            var offset = 0
            assertEquals(nonempty, definition.readTransportBytes(bytes.size, { bytes[offset++] }, earlierValue = previous))
        }
    }

    @Test
    fun requiredObjectFieldsMayBeSplitWithinMapEntriesAndTypedPayloads() {
        val embedded = EmbeddedObjectDefinition(dataModel = { CompositionObject })
        val firstBytes = field(1u, "required".encodeToByteArray())
        val secondBytes = byteArrayOf(16, 84)
        val expected = CompositionObject("required", 42)
        val map = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = embedded)
        val entry = field(2u, firstBytes) + field(2u, secondBytes) + field(1u, "key".encodeToByteArray())
        var offset = 0
        assertEquals(mapOf("key" to expected), map.readTransportBytes(entry.size, { entry[offset++] }))
        val case = MultiTypeEnum.invoke(1u, "Object", embedded)
        val codec = MultiTypeDefinition(typeEnum = MultiTypeEnumDefinition("CompositionObjectTypes", { listOf(case) }))
        val typed = field(1u, firstBytes) + field(1u, secondBytes)
        offset = 0
        assertEquals(case(expected), codec.readTransportBytes(typed.size, { typed[offset++] }))
        val kept = InternalMultiTypeDefinition<MultiTypeEnum<Any>, Any, ContainsDefinitionsContext>(
            typeEnum = IndexedEnumDefinition(name = "CompositionKeptObjects", optionalCases = { listOf(case) }),
            definitionMap = mapOf(case to embedded), keepAsValues = true,
        )
        offset = 0
        val raw = kept.readTransportBytes(typed.size, { typed[offset++] })
        assertEquals(expected, (raw.value as ObjectValues<*, *>).toDataObject())
        val replacement = embedded.toTransportByteArray(CompositionObject("new", 7))
        val nextEntry = field(1u, "key".encodeToByteArray()) + field(2u, replacement)
        offset = 0
        assertEquals(mapOf("key" to CompositionObject("new", 7)), map.readTransportBytes(nextEntry.size,
            { nextEntry[offset++] }, earlierValue = mutableMapOf("key" to expected)))
        val list = ListDefinition(valueDefinition = embedded)
        val collection = ContextualPropertyValueDefinition(contextualResolver = { _: IsPropertyContext? -> list })
        val items = listOf(expected, CompositionObject("new", 7))
        val encoded = collection.toTransportByteArray(items)
        offset = 0
        assertEquals(items, collection.readTransportBytes(encoded.size, { encoded[offset++] }))
    }

    @Test
    fun completeWrappedNumericCollectionsRequireTheirEnvelopeWireType() {
        val list = ListDefinition(valueDefinition = NumberDefinition(type = SInt32))
        val wrapped = ContextualSubDefinition(contextualResolver = { _: IsPropertyContext? -> list })
        val complete = ContextualPropertyValueDefinition(contextualResolver = { _: IsPropertyContext? -> wrapped })
        assertFalse(complete.acceptsProtoBufWireType(VAR_INT, null))
        assertTrue(complete.acceptsProtoBufWireType(LENGTH_DELIMITED, null))
        for (items in listOf(emptyList(), listOf(0), listOf(Int.MIN_VALUE, Int.MAX_VALUE))) {
            val bytes = complete.toTransportByteArray(items)
            var offset = 0
            assertEquals(items, complete.readTransportBytes(bytes.size, { bytes[offset++] }))
        }
    }

    @Test
    fun emptyTypedCollectionFragmentsContributeNoElementsInEitherOrder() {
        val case = MultiTypeEnum.invoke(1u, "Items", ListDefinition(valueDefinition = StringDefinition()))
        val codec = MultiTypeDefinition(typeEnum = MultiTypeEnumDefinition("CompositionListTypes", { listOf(case) }))
        val empty = codec.toTransportByteArray(case(emptyList<String>()))
        val full = codec.toTransportByteArray(case(listOf("retained")))
        for ((previous, incoming) in listOf(empty to full, full to empty)) {
            var offset = 0
            val earlier = codec.readTransportBytes(previous.size, { previous[offset++] })
            offset = 0
            assertEquals(case(listOf("retained")), codec.readTransportBytes(incoming.size, { incoming[offset++] }, earlierValue = earlier))
        }
        for (bytes in listOf(empty + full, full + empty, empty + empty)) {
            var offset = 0
            assertFailsWith<ParseException> { codec.readTransportBytes(bytes.size, { bytes[offset++] }) }
        }
    }

    @Test
    fun transformedCollectionContextsDetermineEmptyPresenceAndInjectedValuesKeepTheirBoundaries() {
        val list = ListDefinition(valueDefinition = DateTimeDefinition(precision = TimePrecision.NANOS))
        val legacy = RequestContext(DefinitionsContext())
        val toLegacy = ContextCollectionTransformerDefinition<LocalDateTime, List<LocalDateTime>, IsPropertyContext, IsPropertyContext>(list) { legacy }
        val toCurrent = ContextCollectionTransformerDefinition<LocalDateTime, List<LocalDateTime>, IsPropertyContext, IsPropertyContext>(list) { null }
        legacy.writeReleasedTransportForFingerprint {
            assertFalse(toLegacy.needsEmptyCollectionMarker(emptyList<LocalDateTime>(), null))
            assertTrue(toCurrent.needsEmptyCollectionMarker(emptyList<LocalDateTime>(), legacy))
        }
        val strings = ListDefinition(valueDefinition = StringDefinition())
        for ((placeholder, injected) in listOf(emptyList<String>() to listOf("injected"), listOf("ignored") to emptyList())) {
            val injector = ContextInjectCollectionOnWriteDefinition<String, List<String>, IsPropertyContext>(strings) { injected }
            assertFalse(injector.needsEmptyCollectionMarker(placeholder, null))
            val complete = ContextualPropertyValueDefinition(contextualResolver = { _: IsPropertyContext? -> injector })
            val bytes = complete.toTransportByteArray(placeholder)
            var offset = 0
            assertEquals(injected, complete.readTransportBytes(bytes.size, { bytes[offset++] }))
        }
    }

    @Test
    fun everyLeafFieldPartitionHasTheSameEmbeddedTypedAndMapMeaning() {
        val bytes = leafBytes(expected)
        val fields = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < bytes.size) {
            val start = offset
            val key = ProtoBuf.readKey { bytes[offset++] }
            ProtoBuf.skipField(key) { bytes[offset++] }
            fields += bytes.copyOfRange(start, offset)
        }
        for (mask in 0 until (1 shl (fields.size - 1))) {
            val fragments = mutableListOf<ByteArray>()
            var fragment = fields.first()
            for (index in 1 until fields.size) {
                if (mask and (1 shl (index - 1)) != 0) {
                    fragments += fragment
                    fragment = fields[index]
                } else fragment += fields[index]
            }
            fragments += fragment
            fun joined(transform: (ByteArray) -> ByteArray) = fragments.fold(byteArrayOf()) { all, next -> all + transform(next) }
            assertEquals(expected, decode(joined { field(1u, it) }).get { embedded }, "embedded partition $mask")
            assertEquals(leafCase(expected), decode(joined { field(3u, field(1u, it)) }).get { typed }, "typed partition $mask")
            assertEquals(innerCase(leafCase(expected)), decode(joined { field(5u, field(1u, field(1u, it))) }).get { nested }, "nested partition $mask")
            val entry = field(1u, "key".encodeToByteArray()) + joined { field(2u, it) }
            assertEquals(mapOf("key" to expected), decode(field(2u, entry)).get { mapped }, "map partition $mask")
        }
    }

    private fun field(index: UInt, value: ByteArray): ByteArray = buildList {
        ProtoBuf.writeKey(index, LENGTH_DELIMITED, ::add)
        value.size.writeVarBytes(::add)
        value.forEach(::add)
    }.toByteArray()

    private fun leafBytes(values: Values<CompositionLeaf>): ByteArray {
        val cache = WriteCache()
        val bytes = ByteArray(CompositionLeaf.Serializer.calculateProtoBufLength(values, cache))
        var offset = 0
        CompositionLeaf.Serializer.writeProtoBuf(values, cache, { bytes[offset++] = it })
        return bytes
    }

    private fun encode(values: Values<CompositionRecord>): ByteArray {
        val cache = WriteCache()
        val bytes = ByteArray(CompositionRecord.Serializer.calculateProtoBufLength(values, cache))
        var offset = 0
        CompositionRecord.Serializer.writeProtoBuf(values, cache, { bytes[offset++] = it })
        return bytes
    }

    private fun decode(bytes: ByteArray): Values<CompositionRecord> {
        var offset = 0
        return CompositionRecord.Serializer.readProtoBuf(bytes.size, { bytes[offset++] }).also { assertEquals(bytes.size, offset) }
    }
}
