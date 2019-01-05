@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.models

import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.WriteCache
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.ComplexMapModel
import maryk.test.models.Option.V3
import maryk.test.models.SimpleMarykModel
import maryk.test.shouldBe
import maryk.yaml.YamlWriter
import kotlin.test.Test

val testComplexMap = ComplexMapModel(
    stringString = mapOf("v1" to "a", "v2" to "b"),
    intObject = mapOf(1 to SimpleMarykModel("t1"), 2 to SimpleMarykModel("t2")),
    intMulti = mapOf(2 to TypedValue(V3, SimpleMarykModel("m3")))
)

internal class ComplexDataModelMapTest {
    @Test
    fun writeToJSONAndBack() {
        var output = ""
        val writer = JsonWriter(pretty = true) {
            output += it
        }

        ComplexMapModel.writeJson(testComplexMap, writer)

        output shouldBe """
        {
        	"stringString": {
        		"v1": "a",
        		"v2": "b"
        	},
        	"intObject": {
        		"1": {
        			"value": "t1"
        		},
        		"2": {
        			"value": "t2"
        		}
        	},
        	"intMulti": {
        		"2": ["V3", {
        			"value": "m3"
        		}]
        	}
        }
        """.trimIndent()

        var index = 0
        val reader = { JsonReader(reader = { output[index++] }) }
        ComplexMapModel.readJson(reader = reader()) shouldBe testComplexMap
    }

    @Test
    fun writeIntoYAMLObject() {
        var output = ""
        val writer = YamlWriter {
            output += it
        }

        ComplexMapModel.writeJson(testComplexMap, writer)

        output shouldBe """
        stringString:
          v1: a
          v2: b
        intObject:
          1:
            value: t1
          2:
            value: t2
        intMulti:
          2: !V3
            value: m3

        """.trimIndent()
    }

    @Test
    fun convertToProtoBufAndBack() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            ComplexMapModel.calculateProtoBufLength(testComplexMap, cache)
        )

        ComplexMapModel.writeProtoBuf(testComplexMap, cache, bc::write)

        bc.bytes!!.toHex() shouldBe "0a070a0276311201610a070a0276321201621208080212040a0274311208080412040a0274321a0a080412061a040a026d33"

        ComplexMapModel.readProtoBuf(bc.size, bc::read) shouldBe testComplexMap
    }
}
