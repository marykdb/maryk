package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.NotEnoughItemsException
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.exceptions.TooManyItemsException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCache
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test
import kotlin.test.assertTrue

internal class SetDefinitionTest {
    private val subDef = StringDefinition(
        regEx = "T.*"
    )

    private val def = SetDefinition(
        minSize = 2u,
        maxSize = 4u,
        valueDefinition = subDef
    )

    private val defMaxDefined = SetDefinition(
        final = true,
        required = false,
        minSize = 2u,
        maxSize = 4u,
        valueDefinition = subDef,
        default = setOf("T1", "T2", "T3")
    )

    @Test
    fun validateRequired() {
        defMaxDefined.validateWithRef(newValue = null)

        shouldThrow<RequiredException> {
            def.validateWithRef(newValue = null)
        }
    }

    @Test
    fun validateSetSize() {
        def.validateWithRef(newValue = setOf("T", "T2"))
        def.validateWithRef(newValue = setOf("T", "T2", "T3"))
        def.validateWithRef(newValue = setOf("T", "T2", "T3", "T4"))

        shouldThrow<NotEnoughItemsException> {
            def.validateWithRef(newValue = setOf("T"))
        }

        shouldThrow<TooManyItemsException> {
            def.validateWithRef(newValue = setOf("T", "T2", "T3", "T4", "T5"))
        }
    }

    @Test
    fun validateSetContent() {
        val e = shouldThrow<ValidationUmbrellaException> {
            def.validateWithRef(newValue = setOf("T", "WRONG", "WRONG2"))
        }
        e.exceptions.size shouldBe 2

        assertTrue(e.exceptions[0] is InvalidValueException)
        assertTrue(e.exceptions[1] is InvalidValueException)
    }

    @Test
    fun convertValuesToTransportBytesAndBack() {
        val bc = ByteCollector()
        val cache = WriteCache()

        val value = setOf("T", "T2", "T3", "T4")
        val asHex = "220154220254322202543322025434"

        bc.reserve(
            def.calculateTransportByteLengthWithKey(4u, value, cache)
        )
        def.writeTransportBytesWithKey(4u, value, cache, bc::write)

        bc.bytes!!.toHex() shouldBe asHex

        fun readKey() {
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe LENGTH_DELIMITED
            key.tag shouldBe 4u
        }

        fun readValue(set: Set<String>) = def.readTransportBytes(
            ProtoBuf.getLength(LENGTH_DELIMITED, bc::read),
            bc::read,
            null,
            set
        )

        val mutableSet = mutableSetOf<String>()

        value.forEach {
            readKey()
            readValue(mutableSet)
            mutableSet.contains(it) shouldBe true
        }
    }

    @Test
    fun convertValuesToJSONStringAndBack() {
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
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, SetDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, SetDefinition.Model)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, SetDefinition.Model)
        checkJsonConversion(this.defMaxDefined, SetDefinition.Model)
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, SetDefinition.Model)
        checkYamlConversion(this.defMaxDefined, SetDefinition.Model) shouldBe """
        required: false
        final: true
        minSize: 2
        maxSize: 4
        valueDefinition: !String
          required: true
          final: false
          unique: false
          regEx: T.*
        default: [T1, T2, T3]

        """.trimIndent()
    }
}
