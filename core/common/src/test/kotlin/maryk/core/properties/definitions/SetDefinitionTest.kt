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
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCache
import maryk.lib.extensions.toHex
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test
import kotlin.test.assertTrue

internal class SetDefinitionTest {
    private val subDef = StringDefinition(
        regEx = "T.*"
    )

    private val def = SetDefinition(
        minSize = 2,
        maxSize = 4,
        valueDefinition = subDef
    )

    private val defMaxDefined = SetDefinition(
        indexed = true,
        searchable = false,
        final = true,
        required = false,
        minSize = 2,
        maxSize = 4,
        valueDefinition = subDef
    )

    @Test
    fun validate_required() {
        defMaxDefined.validateWithRef(newValue = null)

        shouldThrow<RequiredException> {
            def.validateWithRef(newValue = null)
        }
    }

    @Test
    fun validate_set_size() {
        def.validateWithRef(newValue = setOf("T", "T2"))
        def.validateWithRef(newValue = setOf("T", "T2", "T3"))
        def.validateWithRef(newValue = setOf("T", "T2", "T3", "T4"))

        shouldThrow<TooLittleItemsException> {
            def.validateWithRef(newValue = setOf("T"))
        }

        shouldThrow<TooMuchItemsException> {
            def.validateWithRef(newValue = setOf("T", "T2", "T3", "T4", "T5"))
        }
    }

    @Test
    fun validate_set_content() {
        val e = shouldThrow<ValidationUmbrellaException> {
            def.validateWithRef(newValue = setOf("T", "WRONG", "WRONG2"))
        }
        e.exceptions.size shouldBe 2

        assertTrue(e.exceptions[0] is InvalidValueException)
        assertTrue(e.exceptions[1] is InvalidValueException)
    }

    @Test
    fun convert_values_to_transport_bytes_and_back() {
        val bc = ByteCollector()
        val cache = WriteCache()

        val value = setOf("T", "T2", "T3", "T4")
        val asHex = "220154220254322202543322025434"

        bc.reserve(
            def.calculateTransportByteLengthWithKey(4, value, cache)
        )
        def.writeTransportBytesWithKey(4, value, cache, bc::write)

        bc.bytes!!.toHex() shouldBe asHex

        fun readKey() {
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe WireType.LENGTH_DELIMITED
            key.tag shouldBe 4
        }

        fun readValue() = def.readCollectionTransportBytes(
            ProtoBuf.getLength(WireType.LENGTH_DELIMITED, bc::read),
            bc::read,
            null
        )

        value.forEach {
            readKey()
            readValue() shouldBe it
        }
    }

    @Test
    fun convert_values_values_to_JSON_String_and_back() {
        val value = setOf("T", "T2", "T3", "T4")

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
        checkProtoBufConversion(this.def, SetDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, SetDefinition.Model)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(this.def, SetDefinition.Model)
        checkJsonConversion(this.defMaxDefined, SetDefinition.Model)
    }
}
