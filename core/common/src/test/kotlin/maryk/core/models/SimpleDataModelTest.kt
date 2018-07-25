package maryk.core.models

import maryk.SimpleMarykModel
import maryk.core.properties.ByteCollector
import maryk.core.protobuf.WriteCache
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.initByteArrayByHex
import maryk.lib.extensions.toHex
import maryk.test.shouldBe
import maryk.yaml.YamlWriter
import kotlin.test.Test

private val testValues = SimpleMarykModel(
    value = "haas"
)

internal class SimpleDataModelTest {
    @Test
    fun construct_by_map() {
        SimpleMarykModel.map {
            mapNonNulls(
                value with testValues { value }
            )
        } shouldBe testValues
    }

    @Test
    fun validate() {
        SimpleMarykModel.validate(testValues)
    }

    @Test
    fun write_into_a_JSON_object() {
        var output = ""
        val writer = JsonWriter {
            output += it
        }

        SimpleMarykModel.writeJson(testValues, writer)

        output shouldBe """{"value":"haas"}""".trimIndent()
    }

    @Test
    fun write_into_a_pretty_JSON_object() {
        var output = ""
        val writer = JsonWriter(pretty = true) {
            output += it
        }

        SimpleMarykModel.writeJson(testValues, writer)

        output shouldBe """
        {
        	"value": "haas"
        }
        """.trimIndent()
    }

    @Test
    fun write_into_a_YAML_object() {
        var output = ""
        val writer = YamlWriter {
            output += it
        }

        SimpleMarykModel.writeJson(testValues, writer)

        output shouldBe """
        value: haas

        """.trimIndent()
    }

    @Test
    fun write_to_ProtoBuf_bytes() {
        val bc = ByteCollector()
        val cache = WriteCache()

        val map = SimpleMarykModel(
            "hay"
        )

        bc.reserve(
            SimpleMarykModel.calculateProtoBufLength(map, cache)
        )

        SimpleMarykModel.writeProtoBuf(map, cache, bc::write)

        bc.bytes!!.toHex() shouldBe "0a03686179"
    }

    @Test
    fun convert_ProtoBuf_bytes_to_map() {
        val bytes = initByteArrayByHex("0a036861790008102019400c70a3d70a3d7220ccf794d105280130026a09010501050105010501")
        var index = 0

        val map = SimpleMarykModel.readProtoBuf(bytes.size, {
            bytes[index++]
        })

        map.size shouldBe 1
        map { value } shouldBe "hay"
    }

    @Test
    fun convert_to_ProtoBuf_and_back() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            SimpleMarykModel.calculateProtoBufLength(testValues, cache)
        )

        SimpleMarykModel.writeProtoBuf(testValues, cache, bc::write)

        bc.bytes!!.toHex() shouldBe "0a0468616173"

        SimpleMarykModel.readProtoBuf(bc.size, bc::read) shouldBe testValues
    }

    @Test
    fun convert_map_to_JSON_and_back_to_map() {
        var output = ""
        val writer = { string: String -> output += string }

        listOf(
            JsonWriter(writer = writer),
            JsonWriter(pretty = true, writer = writer)
        ).forEach { generator ->
            SimpleMarykModel.writeJson(testValues, generator)

            var index = 0
            val reader = { JsonReader(reader = { output[index++] }) }
            SimpleMarykModel.readJson(reader = reader()) shouldBe testValues

            output = ""
        }
    }
}
