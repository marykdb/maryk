package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.NotEnoughItemsException
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.exceptions.TooManyItemsException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.numeric.UInt32
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCache
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.expect

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

        assertFailsWith<RequiredException> {
            def.validateWithRef(newValue = null)
        }
    }

    @Test
    fun validateSetSize() {
        def.validateWithRef(newValue = setOf("T", "T2"))
        def.validateWithRef(newValue = setOf("T", "T2", "T3"))
        def.validateWithRef(newValue = setOf("T", "T2", "T3", "T4"))

        assertFailsWith<NotEnoughItemsException> {
            def.validateWithRef(newValue = setOf("T"))
        }

        assertFailsWith<TooManyItemsException> {
            def.validateWithRef(newValue = setOf("T", "T2", "T3", "T4", "T5"))
        }
    }

    @Test
    fun validateSetContent() {
        val e = assertFailsWith<ValidationUmbrellaException> {
            def.validateWithRef(newValue = setOf("T", "WRONG", "WRONG2"))
        }
        expect(2) { e.exceptions.size }

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
            def.calculateTransportByteLengthWithKey(4, value, cache)
        )
        def.writeTransportBytesWithKey(4, value, cache, bc::write)

        expect(asHex) { bc.bytes!!.toHexString() }

        fun readKey() {
            val key = ProtoBuf.readKey(bc::read)
            expect(LENGTH_DELIMITED) { key.wireType }
            expect(4u) { key.tag }
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
            assertTrue { mutableSet.contains(it) }
        }
    }

    @Test
    fun convertValuesToJSONStringAndBack() {
        val value = setOf("T", "T2", "T3", "T4")

        var totalString = ""
        def.writeJsonValue(value, JsonWriter { totalString += it })

        expect("""["T","T2","T3","T4"]""") { totalString }

        val iterator = totalString.iterator()
        val reader = JsonReader { iterator.nextChar() }
        reader.nextToken()
        val converted = def.readJson(reader)

        assertEquals(value, converted)
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
        expect(
            """
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
        ) {
            checkYamlConversion(this.defMaxDefined, SetDefinition.Model)
        }
    }

    @Test
    fun isCompatible() {
        assertTrue {
            SetDefinition(valueDefinition = StringDefinition()).compatibleWith(
                SetDefinition(valueDefinition = StringDefinition(regEx = "[av]*"))
            )
        }

        assertFalse {
            SetDefinition(valueDefinition = NumberDefinition(type = UInt32)).compatibleWith(
                SetDefinition(valueDefinition = StringDefinition())
            )
        }

        assertFalse {
            SetDefinition(valueDefinition = StringDefinition(), maxSize = 4u).compatibleWith(
                SetDefinition(valueDefinition = StringDefinition(maxSize = 5u))
            )
        }
    }
}
