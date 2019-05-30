package maryk.core.models

import maryk.core.protobuf.WriteCache
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.initByteArrayByHex
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.SimpleMarykModel
import maryk.yaml.YamlWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

private val testValues = SimpleMarykModel(
    value = "haas"
)

internal class SimpleDataModelTest {
    @Test
    fun constructByMap() {
        expect(testValues) {
            SimpleMarykModel.values {
                mapNonNulls(
                    value with testValues { value }
                )
            }
        }
    }

    @Test
    fun validate() {
        SimpleMarykModel.validate(testValues)
    }

    @Test
    fun writeIntoJSONObject() {
        var output = ""
        val writer = JsonWriter {
            output += it
        }

        SimpleMarykModel.writeJson(testValues, writer)

        assertEquals("""{"value":"haas"}""".trimIndent(), output)
    }

    @Test
    fun writeIntoPrettyJSONObject() {
        var output = ""
        val writer = JsonWriter(pretty = true) {
            output += it
        }

        SimpleMarykModel.writeJson(testValues, writer)

        assertEquals(
            """
            {
              "value": "haas"
            }
            """.trimIndent(),
            output
        )
    }

    @Test
    fun writeIntoYAMLObject() {
        var output = ""
        val writer = YamlWriter {
            output += it
        }

        SimpleMarykModel.writeJson(testValues, writer)

        assertEquals(
            """
            value: haas

            """.trimIndent(),
            output
        )
    }

    @Test
    fun writeToProtoBufBytes() {
        val bc = ByteCollector()
        val cache = WriteCache()

        val map = SimpleMarykModel(
            "hay"
        )

        bc.reserve(
            SimpleMarykModel.calculateProtoBufLength(map, cache)
        )

        SimpleMarykModel.writeProtoBuf(map, cache, bc::write)

        expect("0a03686179") { bc.bytes!!.toHex() }
    }

    @Test
    fun convertProtoBufBytesToMap() {
        val bytes = initByteArrayByHex("0a036861790008102019400c70a3d70a3d7220ccf794d105280130026a09010501050105010501")
        var index = 0

        val map = SimpleMarykModel.readProtoBuf(bytes.size, {
            bytes[index++]
        })

        expect(1) { map.size }
        expect("hay") { map { value } }
    }

    @Test
    fun convertToProtoBufAndBack() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            SimpleMarykModel.calculateProtoBufLength(testValues, cache)
        )

        SimpleMarykModel.writeProtoBuf(testValues, cache, bc::write)

        expect("0a0468616173") { bc.bytes!!.toHex() }

        expect(testValues) { SimpleMarykModel.readProtoBuf(bc.size, bc::read) }
    }

    @Test
    fun convertMapToJSONAndBackToMap() {
        var output = ""
        val writer = { string: String -> output += string }

        listOf(
            JsonWriter(writer = writer),
            JsonWriter(pretty = true, writer = writer)
        ).forEach { generator ->
            SimpleMarykModel.writeJson(testValues, generator)

            var index = 0
            val reader = { JsonReader(reader = { output[index++] }) }
            expect(testValues) { SimpleMarykModel.readJson(reader = reader()) }

            output = ""
        }
    }
}
