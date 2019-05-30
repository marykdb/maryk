package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.NotEnoughItemsException
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.exceptions.TooManyItemsException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.numeric.SInt32
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCache
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.assertType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.expect

internal class MapDefinitionTest {
    private val intDef = NumberDefinition(
        type = SInt32,
        maxValue = 1000
    )

    private val stringDef = StringDefinition(
        regEx = "#.*"
    )

    private val def = MapDefinition(
        minSize = 2u,
        maxSize = 4u,
        keyDefinition = intDef,
        valueDefinition = stringDef
    )

    private val defMaxDefined = MapDefinition(
        final = true,
        required = false,
        minSize = 2u,
        maxSize = 4u,
        keyDefinition = intDef,
        valueDefinition = stringDef,
        default = mapOf(
            4 to "four",
            5 to "five"
        )
    )

    private val value = mapOf(
        12 to "#twelve",
        30 to "#thirty",
        100 to "#hundred",
        1000 to "#thousand"
    )

    @Test
    fun validateMapSize() {
        def.validateWithRef(
            newValue = mapOf(
                12 to "#twelve",
                30 to "#thirty"
            )
        )
        def.validateWithRef(
            newValue = mapOf(
                12 to "#twelve",
                30 to "#thirty",
                100 to "#hundred",
                1000 to "#thousand"
            )
        )

        assertFailsWith<NotEnoughItemsException> {
            def.validateWithRef(
                newValue = mapOf(
                    1 to "#one"
                )
            )
        }

        assertFailsWith<TooManyItemsException> {
            def.validateWithRef(
                newValue = mapOf(
                    12 to "#twelve",
                    30 to "#thirty",
                    100 to "#hundred",
                    1000 to "#thousand",
                    0 to "#zero"
                )
            )
        }
    }

    @Test
    fun validateMapContent() {
        val e = assertFailsWith<ValidationUmbrellaException> {
            def.validateWithRef(
                newValue = mapOf(
                    12 to "#twelve",
                    30 to "WRONG",
                    1001 to "#thousandone",
                    3000 to "#threethousand"
                )
            )
        }
        expect(3) { e.exceptions.size }

        assertType<InvalidValueException>(e.exceptions[0]).apply {
            expect("@30") { this.reference!!.completeName }
        }

        assertType<OutOfRangeException>(e.exceptions[1]).apply {
            expect("#1001") { this.reference!!.completeName }
        }

        assertType<OutOfRangeException>(e.exceptions[2]).apply {
            expect("#3000") { this.reference!!.completeName }
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

        expect("220b08181207237477656c7665220b083c120723746869727479220d08c80112082368756e64726564220e08d00f12092374686f7573616e64") { bc.bytes!!.toHex() }

        fun readKey() {
            val key = ProtoBuf.readKey(bc::read)
            expect(LENGTH_DELIMITED) { key.wireType }
            expect(4u) { key.tag }
        }

        fun readValue(map: Map<Int, String>) {
            val length = ProtoBuf.getLength(LENGTH_DELIMITED, bc::read)
            def.readTransportBytes(length, bc::read, null, map)
        }

        val mutableMap = mutableMapOf<Int, String>()

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
        checkProtoBufConversion(this.def, MapDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, MapDefinition.Model)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, MapDefinition.Model)
        checkJsonConversion(this.defMaxDefined, MapDefinition.Model)
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, MapDefinition.Model)

        expect(
            """
            required: false
            final: true
            minSize: 2
            maxSize: 4
            keyDefinition: !Number
              required: true
              final: false
              unique: false
              type: SInt32
              maxValue: 1000
              random: false
            valueDefinition: !String
              required: true
              final: false
              unique: false
              regEx: '#.*'
            default:
              4: four
              5: five

            """.trimIndent()
        ) {
            checkYamlConversion(this.defMaxDefined, MapDefinition.Model)
        }
    }
}
