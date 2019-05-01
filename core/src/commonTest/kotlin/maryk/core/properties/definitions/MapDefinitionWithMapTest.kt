package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.numeric.SInt32
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCache
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import maryk.test.shouldThrow
import kotlin.test.Test

internal class MapDefinitionWithMapTest {
    private val intDef = NumberDefinition(
        type = SInt32,
        maxValue = 1000
    )

    private val mapSubDef = MapDefinition(
        keyDefinition = intDef,
        valueDefinition = StringDefinition(
            regEx = "#.*"
        )
    )

    private val def = MapDefinition(
        minSize = 2u,
        maxSize = 4u,
        keyDefinition = intDef,
        valueDefinition = mapSubDef
    )

    private val value = mapOf(
        12 to mapOf(1 to "#twelve"),
        30 to mapOf(2 to "#thirty"),
        100 to mapOf(3 to "#hundred"),
        1000 to mapOf(4 to "#thousand")
    )

    @Test
    fun validateMapContent() {
        val e = shouldThrow<ValidationUmbrellaException> {
            def.validateWithRef(
                newValue = mapOf(
                    12 to mapOf(
                        12 to "#twelve",
                        40 to "WRONG",
                        1323 to "#thousandone",
                        2938 to "#threethousand"
                    ),
                    13 to mapOf(
                        14 to "#twelve",
                        41 to "WRONG"
                    )
                )
            )
        }
        e.exceptions.size shouldBe 2

        shouldBeOfType<ValidationUmbrellaException>(e.exceptions[0]).let { se ->
            shouldBeOfType<InvalidValueException>(se.exceptions[0]).reference!!.completeName shouldBe "@12.@40"
            shouldBeOfType<OutOfRangeException>(se.exceptions[1]).reference!!.completeName shouldBe "@12.$1323"
            shouldBeOfType<OutOfRangeException>(se.exceptions[2]).reference!!.completeName shouldBe "@12.$2938"
        }

        shouldBeOfType<ValidationUmbrellaException>(e.exceptions[1]).let { se2 ->
            shouldBeOfType<InvalidValueException>(se2.exceptions[0]).reference!!.completeName shouldBe "@13.@41"
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

        bc.bytes!!.toHex() shouldBe "220f0818120b08021207237477656c7665220f083c120b0804120723746869727479221108c801120c080612082368756e64726564221208d00f120d080812092374686f7573616e64"

        fun readKey() {
            val key = ProtoBuf.readKey(bc::read)
            key.wireType shouldBe LENGTH_DELIMITED
            key.tag shouldBe 4u
        }

        fun readValue(map: Map<Int, Map<Int, String>>) {
            val length = ProtoBuf.getLength(LENGTH_DELIMITED, bc::read)
            def.readTransportBytes(length, bc::read, null, map)
        }

        val mutableMap = mutableMapOf<Int, Map<Int, String>>()

        for (it in this.value) {
            readKey()
            readValue(mutableMap)
            mutableMap[it.key] shouldBe it.value
        }
    }

    @Test
    fun convertValuesToJSONStringAndBack() {
        var totalString = ""
        def.writeJsonValue(value, JsonWriter { totalString += it })

        totalString shouldBe """{"12":{"1":"#twelve"},"30":{"2":"#thirty"},"100":{"3":"#hundred"},"1000":{"4":"#thousand"}}"""

        val iterator = totalString.iterator()
        val reader = JsonReader { iterator.nextChar() }
        reader.nextToken()
        val converted = def.readJson(reader)

        converted shouldBe this.value
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, MapDefinition.Model)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, MapDefinition.Model)
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, MapDefinition.Model) shouldBe """
        required: true
        final: false
        minSize: 2
        maxSize: 4
        keyDefinition: !Number
          required: true
          final: false
          unique: false
          type: SInt32
          maxValue: 1000
          random: false
        valueDefinition: !Map
          required: true
          final: false
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

        """.trimIndent()
    }
}
