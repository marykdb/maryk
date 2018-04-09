package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.exceptions.TooLittleItemsException
import maryk.core.properties.exceptions.TooMuchItemsException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

internal class ListDefinitionTest {
    private val subDef = StringDefinition(
        regEx = "T.*"
    )

    private val def = ListDefinition(
        minSize = 2,
        maxSize = 4,
        valueDefinition = subDef
    )

    private val defMaxDefined = ListDefinition(
        indexed = true,
        searchable = false,
        final = true,
        required = false,
        minSize = 2,
        maxSize = 4,
        valueDefinition = subDef
    )

    private val defVarInt = ListDefinition(
        valueDefinition = NumberDefinition(type = UInt32)
    )

    private val def64Int = ListDefinition(
        valueDefinition = NumberDefinition(type = Float64)
    )

    private val def32Int = ListDefinition(
        valueDefinition = NumberDefinition(type = Float32)
    )

    @Test
    fun validate_required() {
        defMaxDefined.validateWithRef(newValue = null)

        shouldThrow<RequiredException> {
            def.validateWithRef(newValue = null)
        }
    }

    @Test
    fun validate_list_size() {
        def.validateWithRef(newValue = listOf("T", "T2"))
        def.validateWithRef(newValue = listOf("T", "T2", "T3"))
        def.validateWithRef(newValue = listOf("T", "T2", "T3", "T4"))

        shouldThrow<TooLittleItemsException> {
            def.validateWithRef(newValue = listOf("T"))
        }

        shouldThrow<TooMuchItemsException> {
            def.validateWithRef(newValue = listOf("T", "T2", "T3", "T4", "T5"))
        }
    }

    @Test
    fun validate_list_content() {
        val e = shouldThrow<ValidationUmbrellaException> {
            def.validateWithRef(newValue = listOf("T", "WRONG", "WRONG2"))
        }
        e.exceptions.size shouldBe 2

        with(e.exceptions[0] as InvalidValueException) {
            this.reference!!.completeName shouldBe "@1"
        }
        with(e.exceptions[1] as InvalidValueException) {
            this.reference!!.completeName shouldBe "@2"
        }
    }

    @Test
    fun convert_values_to_transport_bytes_and_back() {
        val bc = ByteCollector()

        val value = listOf("T", "T2", "T3", "T4")
        val asHex = "0a01540a0254320a0254330a025434"

        val cache = WriteCache()

        bc.reserve(
            def.calculateTransportByteLengthWithKey(1, value, cache)
        )
        def.writeTransportBytesWithKey(1, value, cache, bc::write)

        bc.bytes!!.toHex() shouldBe asHex

        fun readKey() {
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe WireType.LENGTH_DELIMITED
            key.tag shouldBe 1
        }

        fun readValue() = def.readCollectionTransportBytes(
            ProtoBuf.getLength(WireType.LENGTH_DELIMITED, bc::read),
            bc::read,
            null
        )

        for (it in value) {
            readKey()
            readValue() shouldBe it
        }
    }

    @Test
    fun convert_varInt_values_to_packed_transport_bytes_and_back() {
        val value = listOf(
            76523.toUInt32(),
            2423.toUInt32(),
            25423.toUInt32(),
            42.toUInt32()
        )
        val asHex = "1209ebd504f712cfc6012a"

        this.testPackedTransportConversion(defVarInt, value, asHex, 2)
    }

    @Test
    fun convert_32_bit_values_to_packed_transport_bytes_and_back() {
        val value = floatArrayOf(
            3.566F,
            58253.87652F,
            0.000222F,
            236453165416F
        ).asList()
        val asHex = "22104064395947638de13968c8ad525c36d5"

        this.testPackedTransportConversion(def32Int, value, asHex, 4)
    }

    @Test
    fun convert_64_bit_values_to_packed_transport_bytes_and_back() {
        val value = listOf(
            3.523874666,
            5825394671387643.87652,
            0.0002222222222,
            2364531654162343428.0
        )
        val asHex = "1a20400c30e5336d62274334b22a641083fd3f2d208a5a84aba343c06840817d41b4"

        this.testPackedTransportConversion(def64Int, value, asHex, 3)
    }

    private fun <T: Any> testPackedTransportConversion(def: ListDefinition<T, *>, list: List<T>, hex: String, index: Int) {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            def.calculateTransportByteLengthWithKey(index, list, cache)
        )
        def.writeTransportBytesWithKey(index, list, cache, bc::write)

        bc.bytes!!.toHex() shouldBe hex

        val key = ProtoBuf.readKey(bc::read)
        key.wireType shouldBe WireType.LENGTH_DELIMITED
        key.tag shouldBe index

        val readList = def.readPackedCollectionTransportBytes(
            ProtoBuf.getLength(WireType.LENGTH_DELIMITED, bc::read),
            bc::read,
            null
        )

        readList shouldBe list
    }

    @Test
    fun convert_values_values_to_JSON_String_and_back() {
        val value = listOf("T", "T2", "T3", "T4")

        var totalString = ""
        def.writeJsonValue(value, JsonWriter { totalString += it })

        totalString shouldBe "[\"T\",\"T2\",\"T3\",\"T4\"]"

        val iterator = totalString.iterator()
        val reader = JsonReader { iterator.nextChar() }
        reader.nextToken()
        val converted = def.readJson(reader)

        converted shouldBe value
    }

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.def, ListDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, ListDefinition.Model)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(this.def, ListDefinition.Model)
        checkJsonConversion(this.defMaxDefined, ListDefinition.Model)
    }
}
