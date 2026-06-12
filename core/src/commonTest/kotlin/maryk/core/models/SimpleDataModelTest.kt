package maryk.core.models

import maryk.core.protobuf.WriteCache
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.exceptions.ParseException
import maryk.test.ByteCollector
import maryk.test.models.SimpleMarykModel
import maryk.yaml.YamlWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.expect

private val testValues = SimpleMarykModel.create {
    value with "haas"
}

internal class SimpleDataModelTest {
    @Test
    fun constructByMap() {
        expect(testValues) {
            SimpleMarykModel.create {
                value with testValues { value }
            }
        }
    }

    @Test
    fun validate() {
        SimpleMarykModel.validate(testValues)
    }

    @Test
    fun writeIntoJSONObject() {
        val output = buildString {
            val writer = JsonWriter {
                append(it)
            }

            SimpleMarykModel.Serializer.writeJson(testValues, writer)
        }

        assertEquals("""{"value":"haas"}""".trimIndent(), output)
    }

    @Test
    fun writeIntoPrettyJSONObject() {
        val output = buildString {
            val writer = JsonWriter(pretty = true) {
                append(it)
            }
            SimpleMarykModel.Serializer.writeJson(testValues, writer)
        }

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
        val output = buildString {
            val writer = YamlWriter {
                append(it)
            }

            SimpleMarykModel.Serializer.writeJson(testValues, writer)
        }

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

        val map = SimpleMarykModel.create {
            value with "hay"
        }

        bc.reserve(
            SimpleMarykModel.Serializer.calculateProtoBufLength(map, cache)
        )

        SimpleMarykModel.Serializer.writeProtoBuf(map, cache, bc::write)

        expect("0a03686179") { bc.bytes!!.toHexString() }
    }

    @Test
    fun convertProtoBufBytesToMap() {
        val bytes = ("0a036861790008102019400c70a3d70a3d7220ccf794d105280130026a09010501050105010501").hexToByteArray()
        var index = 0

        val map = SimpleMarykModel.Serializer.readProtoBuf(bytes.size, {
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
            SimpleMarykModel.Serializer.calculateProtoBufLength(testValues, cache)
        )

        SimpleMarykModel.Serializer.writeProtoBuf(testValues, cache, bc::write)

        expect("0a0468616173") { bc.bytes!!.toHexString() }

        expect(testValues) { SimpleMarykModel.Serializer.readProtoBuf(bc.size, bc::read) }
    }

    @Test
    fun rejectsMalformedProtoBufFieldLength() {
        val knownField = ByteCollector()
        knownField.reserve(2)
        ProtoBuf.writeKey(1u, LENGTH_DELIMITED, knownField::write)
        knownField.write(7)

        assertFailsWith<ParseException> {
            SimpleMarykModel.Serializer.readProtoBuf(knownField.size, knownField::read)
        }

        val unknownField = ByteCollector()
        unknownField.reserve(3)
        ProtoBuf.writeKey(99u, LENGTH_DELIMITED, unknownField::write)
        unknownField.write(7)

        assertFailsWith<ParseException> {
            SimpleMarykModel.Serializer.readProtoBuf(unknownField.size, unknownField::read)
        }
    }

    @Test
    fun convertMapToJSONAndBackToMap() {
        var output = ""
        val writer = { string: String -> output += string }

        listOf(
            JsonWriter(writer = writer),
            JsonWriter(pretty = true, writer = writer)
        ).forEach { generator ->
            SimpleMarykModel.Serializer.writeJson(testValues, generator)

            var index = 0
            val reader = { JsonReader(reader = { output.getOrNull(index++) }) }
            expect(testValues) { SimpleMarykModel.Serializer.readJson(reader = reader()) }

            output = ""
        }
    }

    @Test
    fun readJsonStringRejectsTrailingContent() {
        assertFailsWith<Throwable> {
            SimpleMarykModel.Serializer.readJson("""{"value":"haas"}{}""")
        }
        assertFailsWith<Throwable> {
            SimpleMarykModel.Serializer.readJson("""{"value":"haas"} trailing""")
        }
    }

    @Test
    fun readJsonStringAllowsTrailingWhitespace() {
        expect(testValues) {
            SimpleMarykModel.Serializer.readJson("{\"value\":\"haas\"} \n")
        }
    }
}
