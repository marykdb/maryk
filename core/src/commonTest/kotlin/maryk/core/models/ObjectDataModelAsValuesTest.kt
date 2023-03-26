package maryk.core.models

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.WriteCache
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.EmbeddedMarykObject
import maryk.test.models.Option.V1
import maryk.test.models.SimpleMarykTypeEnumWithObject.S3
import maryk.test.models.TestMarykObject
import maryk.test.models.TestValueObject
import maryk.yaml.YamlWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

private val testExtendedObject = TestMarykObject.run {
    create(
        string with "hay",
        int with 4,
        uint with 32u,
        double with 3.555,
        dateTime with LocalDateTime(2017, 12, 4, 12, 13),
        bool with true,
        enum with V1,
        list with listOf(34, 2352, 3423, 766),
        set with setOf(
            LocalDate(2017, 12, 5),
            LocalDate(2016, 3, 2),
            LocalDate(1981, 12, 5)
        ),
        map with mapOf(
            LocalTime(12, 55) to "yes",
            LocalTime(10, 3) to "ahum"
        ),
        valueObject with TestValueObject(6, LocalDateTime(2017, 4, 1, 12, 55), true),
        embeddedObject with EmbeddedMarykObject.run {
            create(
                value with "test"
            )
        },
        multi with TypedValue(S3, EmbeddedMarykObject("subInMulti!")),
        listOfString with listOf("test1", "another test", "🤗")
    )
}

private const val JSON =
    """{"string":"hay","int":4,"uint":32,"double":"3.555","dateTime":"2017-12-04T12:13","bool":true,"enum":"V1(1)","list":[34,2352,3423,766],"set":["2017-12-05","2016-03-02","1981-12-05"],"map":{"12:55":"yes","10:03":"ahum"},"valueObject":{"int":6,"dateTime":"2017-04-01T12:55","bool":true},"embeddedObject":{"value":"test"},"multi":["S3(3)",{"value":"subInMulti!"}],"listOfString":["test1","another test","🤗"]}"""

// Test if unknown values will be skipped
private const val PRETTY_JSON_WITH_SKIP = """{
  "string": "hay",
  "int": 4,
  "uint": 32,
  "double": "3.555",
  "bool": true,
  "dateTime": "2017-12-04T12:13",
  "enum": "V1(1)",
  "list": [34, 2352, 3423, 766],
  "set": ["2017-12-05", "2016-03-02", "1981-12-05"],
  "map": {
    "12:55": "yes",
    "10:03": "ahum"
  },
    "skipUnknown": "should be skipped as possible future property",
  "valueObject": {
    "int": 6,
    "dateTime": "2017-04-01T12:55",
    "bool": true
  },
  "embeddedObject": {
    "value": "test"
  },
  "multi": ["S3(3)", {
    "value": "subInMulti!"
  }],
  "listOfString": ["test1", "another test", "🤗"]
}"""

internal class ObjectDataModelAsValuesTest {
    @Test
    fun writeIntoJSONObject() {
        val output = buildString {
            val writer = JsonWriter {
                append(it)
            }
            TestMarykObject.Model.writeJson(testExtendedObject, writer)
        }

        assertEquals(JSON, output)
    }

    @Test
    fun writeIntoPrettyJSONObject() {
        val output = buildString {
            val writer = JsonWriter(pretty = true) {
                append(it)
            }
            TestMarykObject.Model.writeJson(testExtendedObject, writer)
        }

        assertEquals(
            """
            {
              "string": "hay",
              "int": 4,
              "uint": 32,
              "double": "3.555",
              "dateTime": "2017-12-04T12:13",
              "bool": true,
              "enum": "V1(1)",
              "list": [34, 2352, 3423, 766],
              "set": ["2017-12-05", "2016-03-02", "1981-12-05"],
              "map": {
                "12:55": "yes",
                "10:03": "ahum"
              },
              "valueObject": {
                "int": 6,
                "dateTime": "2017-04-01T12:55",
                "bool": true
              },
              "embeddedObject": {
                "value": "test"
              },
              "multi": ["S3(3)", {
                "value": "subInMulti!"
              }],
              "listOfString": ["test1", "another test", "🤗"]
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

            TestMarykObject.Model.writeJson(testExtendedObject, writer)
        }

        assertEquals(
            """
            string: hay
            int: 4
            uint: 32
            double: 3.555
            dateTime: '2017-12-04T12:13'
            bool: true
            enum: V1(1)
            list: [34, 2352, 3423, 766]
            set: [2017-12-05, 2016-03-02, 1981-12-05]
            map:
              12:55: yes
              10:03: ahum
            valueObject:
              int: 6
              dateTime: '2017-04-01T12:55'
              bool: true
            embeddedObject:
              value: test
            multi: !S3(3)
              value: subInMulti!
            listOfString: [test1, another test, 🤗]

            """.trimIndent(),
            output
        )
    }

    @Test
    fun convertToProtoBufAndBack() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            TestMarykObject.Model.calculateProtoBufLength(testExtendedObject, cache)
        )

        TestMarykObject.Model.writeProtoBuf(testExtendedObject, cache, bc::write)

        expect("0a036861791008182021713d0ad7a3700c4028ccf794d10530013801420744e024be35fc0b4a08c29102bc87028844520908a4eb021203796573520a08d49a0212046168756d5a0e800000060180000058dfa324010162060a04746573746a0f1a0d0a0b737562496e4d756c7469217a0574657374317a0c616e6f7468657220746573747a04f09fa497") {
            bc.bytes!!.toHex()
        }

        expect(testExtendedObject) { TestMarykObject.Model.readProtoBuf(bc.size, bc::read) }
    }

    @Test
    fun convertJSONToDataObject() {
        var input = ""
        var index = 0
        val reader = { input[index++] }
        val jsonReader = { JsonReader(reader = reader) }

        listOf(
            JSON,
            PRETTY_JSON_WITH_SKIP
        ).forEach { jsonInput ->
            input = jsonInput
            index = 0
            expect(testExtendedObject) { TestMarykObject.Model.readJson(reader = jsonReader()) }
        }
    }

    @Test
    fun convertToJSONAndBack() {
        var output = ""
        val writer = { string: String -> output += string }

        listOf(
            JsonWriter(writer = writer),
            JsonWriter(pretty = true, writer = writer)
        ).forEach { generator ->
            TestMarykObject.Model.writeJson(testExtendedObject, generator)

            var index = 0
            val reader = { JsonReader(reader = { output[index++] }) }
            expect(testExtendedObject) { TestMarykObject.Model.readJson(reader = reader()) }

            output = ""
        }
    }
}
