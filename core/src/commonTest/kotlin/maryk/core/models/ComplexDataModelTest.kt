package maryk.core.models

import maryk.core.properties.enum.invoke
import maryk.core.protobuf.WriteCache
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MarykTypeEnum.T3
import maryk.yaml.YamlWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

val testComplexMap = ComplexModel.create {
    multi with T3 { value with "u3"; model with { value with "ue3" } }
    mapStringString with mapOf("v1" to "a", "v2" to "b")
    mapIntObject with mapOf(1u to EmbeddedMarykModel.create { value with "t1" }, 2u to EmbeddedMarykModel.create { value with "t2" })
    mapIntMulti with mapOf(2u to T3 { value with "m3" })
}

internal class ComplexDataModelMapTest {
    @Test
    fun writeToJSONAndBack() {
        val output = buildString {
            val writer = JsonWriter(pretty = true) {
                append(it)
            }
            ComplexModel.Serializer.writeJson(testComplexMap, writer)
        }

        assertEquals(
            """
            {
              "multi": ["T3(3)", {
                "value": "u3",
                "model": {
                  "value": "ue3"
                }
              }],
              "mapStringString": {
                "v1": "a",
                "v2": "b"
              },
              "mapIntObject": {
                "1": {
                  "value": "t1"
                },
                "2": {
                  "value": "t2"
                }
              },
              "mapIntMulti": {
                "2": ["T3(3)", {
                  "value": "m3"
                }]
              }
            }
            """.trimIndent(),
            output
        )

        var index = 0
        val reader = { JsonReader(reader = { output[index++] }) }
        expect(testComplexMap) { ComplexModel.Serializer.readJson(reader = reader()) }
    }

    @Test
    fun writeIntoYAMLObject() {
        val output = buildString {
            val writer = YamlWriter {
                append(it)
            }

            ComplexModel.Serializer.writeJson(testComplexMap, writer)
        }

        assertEquals(
            """
            multi: !T3(3)
              value: u3
              model:
                value: ue3
            mapStringString:
              v1: a
              v2: b
            mapIntObject:
              1:
                value: t1
              2:
                value: t2
            mapIntMulti:
              2: !T3(3)
                value: m3

            """.trimIndent(),
            output
        )
    }

    @Test
    fun convertToProtoBufAndBack() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            ComplexModel.Serializer.calculateProtoBufLength(testComplexMap, cache)
        )

        ComplexModel.Serializer.writeProtoBuf(testComplexMap, cache, bc::write)

        expect("0a0d1a0b0a02753312050a0375653312070a02763112016112070a0276321201621a08080112040a0274311a08080212040a027432220a080212061a040a026d33") {
            bc.bytes!!.toHex()
        }

        expect(testComplexMap) { ComplexModel.Serializer.readProtoBuf(bc.size, bc::read) }
    }
}
