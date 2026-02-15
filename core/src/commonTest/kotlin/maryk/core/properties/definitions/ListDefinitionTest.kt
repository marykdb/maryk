package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.NotEnoughItemsException
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.exceptions.TooManyItemsException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
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

internal class ListDefinitionTest {
    private val subDef = StringDefinition(
        regEx = "T.*"
    )

    private val def = ListDefinition(
        minSize = 2u,
        maxSize = 4u,
        valueDefinition = subDef
    )

    private val defMaxDefined = ListDefinition(
        final = true,
        required = false,
        minSize = 2u,
        maxSize = 4u,
        valueDefinition = subDef,
        default = listOf("Tic", "Tac", "Toe")
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
    fun validateRequired() {
        defMaxDefined.validateWithRef(newValue = null)

        assertFailsWith<RequiredException> {
            def.validateWithRef(newValue = null)
        }
    }

    @Test
    fun validateListSize() {
        def.validateWithRef(newValue = listOf("T", "T2"))
        def.validateWithRef(newValue = listOf("T", "T2", "T3"))
        def.validateWithRef(newValue = listOf("T", "T2", "T3", "T4"))

        assertFailsWith<NotEnoughItemsException> {
            def.validateWithRef(newValue = listOf("T"))
        }

        assertFailsWith<TooManyItemsException> {
            def.validateWithRef(newValue = listOf("T", "T2", "T3", "T4", "T5"))
        }
    }

    @Test
    fun validateListContent() {
        val e = assertFailsWith<ValidationUmbrellaException> {
            def.validateWithRef(newValue = listOf("T", "WRONG", "WRONG2"))
        }
        expect(2) { e.exceptions.size }

        with(e.exceptions[0] as InvalidValueException) {
            expect("@1") { this.reference!!.completeName }
        }
        with(e.exceptions[1] as InvalidValueException) {
            expect("@2") { this.reference!!.completeName }
        }
    }

    @Test
    fun convertValuesToTransportBytesAndBack() {
        val bc = ByteCollector()

        val values = listOf("T", "T2", "T3", "T4")
        val asHex = "0a01540a0254320a0254330a025434"

        val cache = WriteCache()

        bc.reserve(
            def.calculateTransportByteLengthWithKey(1, values, cache)
        )
        def.writeTransportBytesWithKey(1, values, cache, bc::write)

        expect(asHex) { bc.bytes!!.toHexString() }

        fun readKey() {
            val key = ProtoBuf.readKey(bc::read)
            expect(LENGTH_DELIMITED) { key.wireType }
            expect(1u) { key.tag }
        }

        fun readValue(list: List<String>) = def.readTransportBytes(
            ProtoBuf.getLength(LENGTH_DELIMITED, bc::read),
            bc::read,
            null,
            list
        )

        val mutableList = mutableListOf<String>()

        for (value in values) {
            readKey()
            readValue(mutableList)
            expect(value) { mutableList.last() }
        }
    }

    @Test
    fun convertVarIntValuesToPackedTransportBytesAndBack() {
        val value = listOf(
            76523u,
            2423u,
            25423u,
            42u
        )
        val asHex = "1209ebd504f712cfc6012a"

        this.testPackedTransportConversion(defVarInt, value, asHex, 2u)
    }

    @Test
    fun convert32BitValuesToPackedTransportBytesAndBack() {
        val value = floatArrayOf(
            3.566F,
            58253.87652F,
            0.000222F,
            236453165416F
        ).asList()
        val asHex = "221058396440e08d6347acc86839d4365c52"

        this.testPackedTransportConversion(def32Int, value, asHex, 4u)
    }

    @Test
    fun convert64BitValuesToPackedTransportBytesAndBack() {
        val value = listOf(
            3.523874666,
            5825394671387643.87652,
            0.0002222222222,
            2364531654162343428.0
        )
        val asHex = "1a2026626d33e5300c40fc8310642ab23443a2ab845a8a202d3fb3417d814068c043"

        this.testPackedTransportConversion(def64Int, value, asHex, 3u)
    }

    private fun <T : Any> testPackedTransportConversion(
        def: ListDefinition<T, *>,
        list: List<T>,
        hex: String,
        index: UInt
    ) {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            def.calculateTransportByteLengthWithKey(index.toInt(), list, cache)
        )
        def.writeTransportBytesWithKey(index.toInt(), list, cache, bc::write)

        expect(hex) { bc.bytes!!.toHexString() }

        val key = ProtoBuf.readKey(bc::read)
        expect(LENGTH_DELIMITED) { key.wireType }
        expect(index) { key.tag }

        val readList = def.readTransportBytes(
            ProtoBuf.getLength(LENGTH_DELIMITED, bc::read),
            bc::read
        )

        expect(list) { readList }
    }

    @Test
    fun convertValuesToJSONStringAndBack() {
        val value = listOf("T", "T2", "T3", "T4")

        val totalString = buildString {
            def.writeJsonValue(value, JsonWriter { append(it) })
        }

        expect("""["T","T2","T3","T4"]""") { totalString }

        val iterator = totalString.iterator()
        val reader = JsonReader { iterator.nextChar() }
        reader.nextToken()
        val converted = def.readJson(reader)

        assertEquals(value, converted)
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, ListDefinition.Model)
        checkProtoBufConversion(this.defMaxDefined, ListDefinition.Model)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, ListDefinition.Model)
        checkJsonConversion(this.defMaxDefined, ListDefinition.Model)
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, ListDefinition.Model)

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
            default: [Tic, Tac, Toe]

            """.trimIndent()
        ) {
            checkYamlConversion(this.defMaxDefined, ListDefinition.Model)
        }
    }

    @Test
    fun isCompatible() {
        assertTrue {
            ListDefinition(valueDefinition = StringDefinition()).compatibleWith(
                ListDefinition(valueDefinition = StringDefinition(regEx = "[av]*"))
            )
        }

        assertFalse {
            ListDefinition(valueDefinition = NumberDefinition(type = UInt32)).compatibleWith(
                ListDefinition(valueDefinition = StringDefinition())
            )
        }

        assertFalse {
            ListDefinition(valueDefinition = StringDefinition(), maxSize = 4u).compatibleWith(
                ListDefinition(valueDefinition = StringDefinition(maxSize = 5u))
            )
        }
    }
}
