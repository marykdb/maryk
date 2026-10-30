package maryk.core.properties.definitions

import kotlinx.datetime.LocalDateTime
import maryk.checkProtoBufConversion
import maryk.core.models.RootDataModel
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.properties.types.TimePrecision.NANOS
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.invoke
import maryk.core.properties.types.numeric.UInt32
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
import maryk.lib.exceptions.ParseException
import maryk.test.models.MarykTypeEnum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private object NestedEmptyCollectionsModel : RootDataModel<NestedEmptyCollectionsModel>() {
    val lists by map(1u, keyDefinition = StringDefinition(), valueDefinition = ListDefinition(valueDefinition = StringDefinition()))
    val sets by map(2u, keyDefinition = StringDefinition(), valueDefinition = SetDefinition(valueDefinition = StringDefinition()))
    val maps by map(3u, keyDefinition = StringDefinition(), valueDefinition = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = StringDefinition()))
    val nested by map(4u, keyDefinition = StringDefinition(), valueDefinition = MapDefinition(
        keyDefinition = StringDefinition(), valueDefinition = ListDefinition(valueDefinition = StringDefinition()),
    ))
    val typed by multiType(5u, typeEnum = MarykTypeEnum)
    val tail by number(6u, type = UInt32)
}

class NestedEmptyCollectionsTransportTest {
    @Test
    fun nestedMapValuesAndMultiTypeCasesRetainEmptyValues() {
        for (typed in listOf<TypedValue<MarykTypeEnum<out Any>, Any>>(
            MarykTypeEnum.T4(emptyList()), MarykTypeEnum.T5(emptySet()), MarykTypeEnum.T6(emptyMap()),
        )) {
            val values = NestedEmptyCollectionsModel.create {
                lists with mapOf("empty" to emptyList(), "item" to listOf(""))
                sets with mapOf("empty" to emptySet(), "item" to setOf(""))
                maps with mapOf("empty" to emptyMap(), "item" to mapOf("" to ""))
                nested with mapOf("outer" to mapOf("empty" to emptyList(), "item" to listOf("")))
                this.typed with typed
                tail with 42u
            }
            val cache = WriteCache()
            val size = NestedEmptyCollectionsModel.Serializer.calculateProtoBufLength(values, cache)
            val bytes = ByteArray(size)
            var offset = 0
            NestedEmptyCollectionsModel.Serializer.writeProtoBuf(values, cache, { bytes[offset++] = it })
            assertEquals(size, offset)
            offset = 0
            assertEquals(values, NestedEmptyCollectionsModel.Serializer.readProtoBuf(size, { bytes[offset++] }))
            assertEquals(size, offset)
        }
    }

    @Test
    fun emptySchemaDefaultsSurviveContextualSerialization() {
        checkProtoBufConversion(ListDefinition(valueDefinition = StringDefinition(), default = emptyList()), ListDefinition.Model)
        checkProtoBufConversion(SetDefinition(valueDefinition = StringDefinition(), default = emptySet()), SetDefinition.Model)
        checkProtoBufConversion(MapDefinition(keyDefinition = StringDefinition(), valueDefinition = StringDefinition(), default = emptyMap()), MapDefinition.Model)
        checkProtoBufConversion(ListDefinition(valueDefinition = DateTimeDefinition(precision = NANOS), default = emptyList()), ListDefinition.Model)
        checkProtoBufConversion(ListDefinition(valueDefinition = NumberDefinition(type = UInt32), default = emptyList()), ListDefinition.Model)
        checkProtoBufConversion(ListDefinition(valueDefinition = DateTimeDefinition(precision = NANOS),
            default = listOf(LocalDateTime(1970, 1, 1, 0, 0, 8, 1))), ListDefinition.Model)
    }

    @Test
    fun maximumMultiTypeCaseDoesNotCollideWithEmptyPresenceMarkers() {
        val case = MultiTypeEnum.invoke(65535u, "Last", ListDefinition(valueDefinition = StringDefinition()))
        val definition = MultiTypeDefinition(typeEnum = MultiTypeEnumDefinition("LastCase", { listOf(case) }))
        checkProtoBufConversion(value = case(emptyList<String>()), def = definition)
        checkProtoBufConversion(value = case(listOf("")), def = definition)
    }

    @Test
    fun malformedNestedEmptyMarkersAreRejected() {
        val map = MapDefinition(keyDefinition = StringDefinition(), valueDefinition = ListDefinition(valueDefinition = StringDefinition()))
        for ((wire, payload) in listOf(VAR_INT to byteArrayOf(0), LENGTH_DELIMITED to byteArrayOf(1, 42))) {
            val bytes = buildList {
                ProtoBuf.writeKey(1u, LENGTH_DELIMITED, ::add)
                add(1)
                add('k'.code.toByte())
                ProtoBuf.writeKey(EMPTY_COLLECTION_TAG_OFFSET + 2u, wire, ::add)
                payload.forEach(::add)
            }.toByteArray()
            var offset = 0
            assertFailsWith<ParseException> { map.readTransportBytes(bytes.size, { bytes[offset++] }, null) }
        }
        val multi = MultiTypeDefinition(typeEnum = MarykTypeEnum)
        val bytes = buildList {
            ProtoBuf.writeKey(EMPTY_MULTI_TYPE_COLLECTION_TAG_OFFSET + MarykTypeEnum.T4.index, LENGTH_DELIMITED, ::add)
            add(1)
            add(42)
        }.toByteArray()
        var offset = 0
        assertFailsWith<ParseException> { multi.readTransportBytes(bytes.size, { bytes[offset++] }, DefinitionsConversionContext()) }
    }
}
