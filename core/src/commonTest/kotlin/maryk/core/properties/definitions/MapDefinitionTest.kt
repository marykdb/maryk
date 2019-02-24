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
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCache
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.shouldBe
import maryk.test.shouldThrow
import kotlin.test.Test

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

        shouldThrow<NotEnoughItemsException> {
            def.validateWithRef(
                newValue = mapOf(
                    1 to "#one"
                )
            )
        }

        shouldThrow<TooManyItemsException> {
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
        val e = shouldThrow<ValidationUmbrellaException> {
            def.validateWithRef(
                newValue = mapOf(
                    12 to "#twelve",
                    30 to "WRONG",
                    1001 to "#thousandone",
                    3000 to "#threethousand"
                )
            )
        }
        e.exceptions.size shouldBe 3

        with(e.exceptions[0] as InvalidValueException) {
            this.reference!!.completeName shouldBe "@30"
        }

        with(e.exceptions[1] as OutOfRangeException) {
            this.reference!!.completeName shouldBe "$1001"
        }

        with(e.exceptions[2] as OutOfRangeException) {
            this.reference!!.completeName shouldBe "$3000"
        }
    }

    @Test
    fun convertValuesToTransportBytesAndBack() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            def.calculateTransportByteLengthWithKey(4, value, cache)
        )
        def.writeTransportBytesWithKey(4, value, cache, bc::write)

        bc.bytes!!.toHex() shouldBe "220b08181207237477656c7665220b083c120723746869727479220d08c80112082368756e64726564220e08d00f12092374686f7573616e64"

        fun readKey() {
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe WireType.LENGTH_DELIMITED
            key.tag shouldBe 4
        }

        fun readValue(): Pair<Int, String> {
            ProtoBuf.getLength(WireType.LENGTH_DELIMITED, bc::read)
            return def.readMapTransportBytes(bc::read)
        }

        for (it in this.value) {
            readKey()
            val mapValue = readValue()
            mapValue.first shouldBe it.key
            mapValue.second shouldBe it.value
        }
    }

    @Test
    fun convertValuesToJSONStringAndBack() {
        var totalString = ""
        def.writeJsonValue(value, JsonWriter { totalString += it })

        totalString shouldBe "{\"12\":\"#twelve\",\"30\":\"#thirty\",\"100\":\"#hundred\",\"1000\":\"#thousand\"}"

        val iterator = totalString.iterator()
        val reader = JsonReader { iterator.nextChar() }
        reader.nextToken()
        val converted = def.readJson(reader)

        converted shouldBe this.value
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
        checkYamlConversion(this.defMaxDefined, MapDefinition.Model) shouldBe """
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
    }
}
