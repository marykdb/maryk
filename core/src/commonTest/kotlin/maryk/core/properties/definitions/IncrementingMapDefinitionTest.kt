package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.NotEnoughItemsException
import maryk.core.properties.exceptions.TooManyItemsException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCache
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.expect

internal class IncrementingMapDefinitionTest {
    private val stringDef = StringDefinition(
        regEx = "#.*"
    )

    private val def = IncrementingMapDefinition(
        minSize = 2u,
        maxSize = 4u,
        keyNumberDescriptor = UInt64,
        valueDefinition = stringDef
    )

    private val defMaxDefined = IncrementingMapDefinition(
        final = true,
        required = false,
        minSize = 2u,
        maxSize = 4u,
        keyNumberDescriptor = UInt64,
        valueDefinition = stringDef
    )

    private val value = mapOf(
        12uL to "#twelve",
        30uL to "#thirty",
        100uL to "#hundred",
        1000uL to "#thousand"
    )

    @Test
    fun validateMapSize() {
        def.validateWithRef(
            newValue = mapOf(
                12uL to "#twelve",
                30uL to "#thirty"
            )
        )
        def.validateWithRef(
            newValue = mapOf(
                12uL to "#twelve",
                30uL to "#thirty",
                100uL to "#hundred",
                1000uL to "#thousand"
            )
        )

        assertFailsWith<NotEnoughItemsException> {
            def.validateWithRef(
                newValue = mapOf(
                    1uL to "#one"
                )
            )
        }

        assertFailsWith<TooManyItemsException> {
            def.validateWithRef(
                newValue = mapOf(
                    12uL to "#twelve",
                    30uL to "#thirty",
                    100uL to "#hundred",
                    1000uL to "#thousand",
                    0uL to "#zero"
                )
            )
        }
    }

    @Test
    fun validateMapContent() {
        val e = assertFailsWith<ValidationUmbrellaException> {
            def.validateWithRef(
                newValue = mapOf(
                    12uL to "#twelve",
                    30uL to "WRONG"
                )
            )
        }
        expect(1) { e.exceptions.size }

        assertIs<InvalidValueException>(e.exceptions[0]).apply {
            expect("@30") { this.reference!!.completeName }
        }
    }

    @Test
    fun convertValuesToTransportBytesAndBack() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            def.calculateTransportByteLengthWithKey(4u, value, cache)
        )
        def.writeTransportBytesWithKey(4u, value, cache, bc::write)

        expect("220b080c1207237477656c7665220b081e120723746869727479220c086412082368756e64726564220e08e80712092374686f7573616e64") { bc.bytes!!.toHex() }

        fun readKey() {
            val key = ProtoBuf.readKey(bc::read)
            expect(LENGTH_DELIMITED) { key.wireType }
            expect(4u) { key.tag }
        }

        fun readValue(map: Map<ULong, String>) {
            val length = ProtoBuf.getLength(LENGTH_DELIMITED, bc::read)
            def.readTransportBytes(length, bc::read, null, map)
        }

        val mutableMap = mutableMapOf<ULong, String>()

        for (it in this.value) {
            readKey()
            readValue(mutableMap)
            expect(it.value) { mutableMap[it.key] }
        }
    }

    @Test
    fun convertValuesToJSONStringAndBack() {
        var totalString = ""
        def.writeJsonValue(value, JsonWriter { totalString += it })

        assertEquals(
            """{"12":"#twelve","30":"#thirty","100":"#hundred","1000":"#thousand"}""",
            totalString
        )

        val iterator = totalString.iterator()
        val reader = JsonReader { iterator.nextChar() }
        reader.nextToken()
        val converted = def.readJson(reader)

        assertEquals(this.value, converted)
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, IncrementingMapDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, IncrementingMapDefinition.Model)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, IncrementingMapDefinition.Model)
        checkJsonConversion(this.defMaxDefined, IncrementingMapDefinition.Model)
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, IncrementingMapDefinition.Model)

        expect(
            """
            required: false
            final: true
            minSize: 2
            maxSize: 4
            keyNumberDescriptor: UInt64
            valueDefinition: !String
              required: true
              final: false
              unique: false
              regEx: '#.*'

            """.trimIndent()
        ) {
            checkYamlConversion(this.defMaxDefined, IncrementingMapDefinition.Model)
        }
    }

    @Test
    fun isCompatible() {
        assertTrue {
            IncrementingMapDefinition(
                keyNumberDescriptor = UInt32,
                valueDefinition = StringDefinition()
            ).compatibleWith(
                IncrementingMapDefinition(
                    keyNumberDescriptor = UInt32,
                    valueDefinition = StringDefinition(regEx = "[av]*")
                )
            )
        }

        assertFalse {
            IncrementingMapDefinition(
                keyNumberDescriptor = Float32,
                valueDefinition = StringDefinition()
            ).compatibleWith(
                IncrementingMapDefinition(
                    keyNumberDescriptor = UInt32,
                    valueDefinition = StringDefinition(regEx = "[av]*")
                )
            )
        }

        assertFalse {
            IncrementingMapDefinition(
                keyNumberDescriptor = UInt32,
                valueDefinition = NumberDefinition(type = UInt32)
            ).compatibleWith(
                IncrementingMapDefinition(
                    keyNumberDescriptor = UInt32,
                    valueDefinition = StringDefinition(regEx = "[av]*")
                )
            )
        }

        assertFalse {
            IncrementingMapDefinition(
                keyNumberDescriptor = UInt32,
                valueDefinition = StringDefinition(),
                maxSize = 4u
            ).compatibleWith(
                IncrementingMapDefinition(
                    keyNumberDescriptor = UInt32,
                    valueDefinition = StringDefinition(),
                    maxSize = 5u
                )
            )
        }
    }
}
