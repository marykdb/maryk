package maryk.core.models

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.protobuf.WriteCache
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.CompleteMarykModel
import maryk.test.models.Option
import maryk.yaml.YamlWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

val testCompleteModel = CompleteMarykModel.run {
    create(
        string with "str",
        number with 5u,
        boolean with true,
        enum with Option.V3,
        date with LocalDate(2018, 11, 4),
        dateTime with LocalDateTime(2018, 11, 4, 12, 22, 33),
        time with LocalTime(12, 22, 33),
        fixedBytes with Bytes(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)),
        flexBytes with Bytes(byteArrayOf(0x01, 0x02, 0x03, 0x04)),
        reference with Key("AAECAQAAECAQAAECAQAAEA"),
        set with setOf(1, 2, 5),
    )
}

internal class CompleteDataModelMapTest {
    @Test
    fun writeToJSONAndBack() {
        val output = buildString {
            val writer = JsonWriter(pretty = true) {
                append(it)
            }
            CompleteMarykModel.Serializer.writeJson(testCompleteModel, writer)
        }

        assertEquals(
            """
            {
              "string": "str",
              "number": 5,
              "boolean": true,
              "enum": "V3(3)",
              "date": "2018-11-04",
              "dateTime": "2018-11-04T12:22:33",
              "time": "12:22:33",
              "fixedBytes": "AQIDBAU",
              "flexBytes": "AQIDBA",
              "reference": "AAECAQAAECAQAAECAQAAEA",
              "subModel": {
                "value": "a default"
              },
              "valueModel": {
                "int": 10,
                "date": "2010-10-10"
              },
              "list": ["ha1", "ha2", "ha3"],
              "set": [1, 2, 3],
              "map": {
                "2010-11-12": 1,
                "2011-12-13": 1
              },
              "multi": ["T1(1)", "a value"],
              "mapWithEnum": {
                "E1(1)": "value"
              },
              "mapWithList": {
                "a": ["b", "c"]
              },
              "mapWithSet": {
                "a": ["b", "c"]
              },
              "mapWithMap": {
                "a": {
                  "b": "c"
                }
              },
              "location": "52.0906448,5.1212607"
            }
            """.trimIndent(),
            output
        )

        var index = 0
        val reader = { JsonReader(reader = { output[index++] }) }
        expect(testCompleteModel) { CompleteMarykModel.Serializer.readJson(reader = reader()) }
    }

    @Test
    fun writeIntoYAMLObject() {
        val output = buildString {
            val writer = YamlWriter {
                append(it)
            }

            CompleteMarykModel.Serializer.writeJson(testCompleteModel, writer)
        }

        assertEquals(
            """
            string: str
            number: 5
            boolean: true
            enum: V3(3)
            date: 2018-11-04
            dateTime: '2018-11-04T12:22:33'
            time: '12:22:33'
            fixedBytes: AQIDBAU
            flexBytes: AQIDBA
            reference: AAECAQAAECAQAAECAQAAEA
            subModel:
              value: a default
            valueModel:
              int: 10
              date: 2010-10-10
            list: [ha1, ha2, ha3]
            set: [1, 2, 3]
            map:
              2010-11-12: 1
              2011-12-13: 1
            multi: !T1(1) a value
            mapWithEnum:
              E1(1): value
            mapWithList:
              a: [b, c]
            mapWithSet:
              a: [b, c]
            mapWithMap:
              a:
                b: c
            location: 52.0906448,5.1212607

            """.trimIndent(),
            output
        )
    }

    @Test
    fun convertToProtoBufAndBack() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            CompleteMarykModel.Serializer.calculateProtoBufLength(testCompleteModel, cache)
        )

        CompleteMarykModel.Serializer.writeProtoBuf(testCompleteModel, cache, bc::write)

        expect("0a0373747210051801200328de960230a8eeb2f5ed2c38a8a69f15420501020304054a04010203045210000102010000102010000102010000105a0b0a09612064656661756c7462098000000a0180003a2c6a036861316a036861326a03686133720302040a7a06089ae90110027a0608b2ef0110028201090a07612076616c7565aa01090801120576616c7565b201090a0161120162120163ba01090a0161120162120163c2010b0a016112060a0162120163d1013f710d03d0660c1f") {
            bc.bytes!!.toHex()
        }

        expect(testCompleteModel) { CompleteMarykModel.Serializer.readProtoBuf(bc.size, bc::read) }
    }
}
