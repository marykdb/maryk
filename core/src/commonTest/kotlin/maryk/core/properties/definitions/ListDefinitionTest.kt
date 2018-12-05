@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")

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
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCache
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
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
        final = true,
        required = false,
        minSize = 2,
        maxSize = 4,
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

        shouldThrow<RequiredException> {
            def.validateWithRef(newValue = null)
        }
    }

    @Test
    fun validateListSize() {
        def.validateWithRef(newValue = listOf("T", "T2"))
        def.validateWithRef(newValue = listOf("T", "T2", "T3"))
        def.validateWithRef(newValue = listOf("T", "T2", "T3", "T4"))

        shouldThrow<NotEnoughItemsException> {
            def.validateWithRef(newValue = listOf("T"))
        }

        shouldThrow<TooManyItemsException> {
            def.validateWithRef(newValue = listOf("T", "T2", "T3", "T4", "T5"))
        }
    }

    @Test
    fun validateListContent() {
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
    fun convertValuesToTransportBytesAndBack() {
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
    fun convertVarIntValuesToPackedTransportBytesAndBack() {
        val value = listOf(
            76523u,
            2423u,
            25423u,
            42u
        )
        val asHex = "1209ebd504f712cfc6012a"

        this.testPackedTransportConversion(defVarInt, value, asHex, 2)
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

        this.testPackedTransportConversion(def32Int, value, asHex, 4)
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
    fun convertValuesToJSONStringAndBack() {
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
        checkYamlConversion(this.defMaxDefined, ListDefinition.Model) shouldBe """
        indexed: true
        required: false
        final: true
        minSize: 2
        maxSize: 4
        valueDefinition: !String
          indexed: false
          required: true
          final: false
          unique: false
          regEx: T.*
        default: [Tic, Tac, Toe]

        """.trimIndent()
    }
}
