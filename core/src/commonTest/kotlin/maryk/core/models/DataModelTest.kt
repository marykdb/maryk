package maryk.core.models

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper
import maryk.core.properties.enum.invoke
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.protobuf.WriteCache
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.initByteArrayByHex
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import maryk.test.models.Option
import maryk.test.models.SimpleMarykTypeEnum.S3
import maryk.test.models.TestMarykModel
import maryk.test.models.TestValueObject
import maryk.yaml.YamlWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.expect

val testMarykModelObject = TestMarykModel.create {
    string with "haas"
    int with 4
    uint with 53u
    double with 3.5555
    bool with true
    dateTime with LocalDateTime(2017, 12, 5, 12, 40)
}

val testExtendedMarykModelObject = TestMarykModel.create {
    string with "hay"
    int with 4
    double with 3.555
    dateTime with LocalDateTime(2017, 12, 4, 12, 13)
    uint with 32u
    bool with true
    list with listOf(34, 2352, 3423, 766)
    set with setOf(
        LocalDate(2017, 12, 5),
        LocalDate(2016, 3, 2),
        LocalDate(1981, 12, 5)
    )
    map with mapOf(
        LocalTime(12, 55) to "yes",
        LocalTime(10, 3) to "ahum"
    )
    valueObject with TestValueObject(6, LocalDateTime(2017, 4, 1, 12, 55), true)
    embeddedValues with { value with "test" }
    multi with S3 { value with "subInMulti!" }
    listOfString with listOf("test1", "another test", "🤗")
}

private const val JSON =
    """{"string":"hay","int":4,"uint":32,"double":"3.555","dateTime":"2017-12-04T12:13","bool":true,"enum":"V1(1)","list":[34,2352,3423,766],"set":["2017-12-05","2016-03-02","1981-12-05"],"map":{"12:55":"yes","10:03":"ahum"},"valueObject":{"int":6,"dateTime":"2017-04-01T12:55","bool":true},"embeddedValues":{"value":"test"},"multi":["S3(3)",{"value":"subInMulti!"}],"listOfString":["test1","another test","🤗"]}"""

private const val ALT_JSON =
    """{"str":"hay","int":4,"uint":32,"double":"3.555","dateTime":"2017-12-04T12:13","bool":true,"enum":"V1(1)","list":[34,2352,3423,766],"set":["2017-12-05","2016-03-02","1981-12-05"],"map":{"12:55":"yes","10:03":"ahum"},"valueObject":{"int":6,"dateTime":"2017-04-01T12:55","bool":true},"embeddedValues":{"value":"test"},"multi":["S3(3)",{"value":"subInMulti!"}],"listOfString":["test1","another test","🤗"]}"""

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
  "embeddedValues": {
    "value": "test"
  },
  "multi": ["S3(3)", {
    "value": "subInMulti!"
  }],
  "listOfString": ["test1", "another test", "🤗"]
}"""

internal class DataModelTest {
    @Test
    fun constructByMap() {
        expect(testMarykModelObject) {
            TestMarykModel.create {
                string with testMarykModelObject { string }
                int with testMarykModelObject { int }
                uint with testMarykModelObject { uint }
                double with testMarykModelObject { double }
                dateTime with testMarykModelObject { dateTime }
                bool with testMarykModelObject { bool }
                enum with testMarykModelObject { enum }
            }
        }
    }

    @Test
    fun validate() {
        TestMarykModel.validate(testMarykModelObject)
    }

    @Test
    fun failValidationWithIncorrectValuesInDataObject() {
        assertFailsWith<ValidationUmbrellaException> {
            TestMarykModel.validate(
                TestMarykModel.create {
                    string with "haas"
                    int with 9
                    uint with 53u
                    double with 3.5555
                    bool with true
                    dateTime with LocalDateTime(2017, 12, 5, 12, 40)
                }
            )
        }
    }

    @Test
    fun failValidationWithIncorrectValuesInMap() {
        val e = assertFailsWith<ValidationUmbrellaException> {
            TestMarykModel.validate(
                TestMarykModel.create {
                    string with "wrong"
                    int with 999
                    uint with 53u
                },
            )
        }

        expect(5) { e.exceptions.size }

        assertIs<InvalidValueException>(e.exceptions[0])
        assertIs<OutOfRangeException>(e.exceptions[1])
        assertIs<RequiredException>(e.exceptions[2])
        assertIs<RequiredException>(e.exceptions[3])
        assertIs<RequiredException>(e.exceptions[4])
    }

    @Test
    fun failValidationWithIncorrectValuesInMapWithoutRequired() {
        val e = assertFailsWith<ValidationUmbrellaException> {
            TestMarykModel.validate(
                TestMarykModel.create {
                    string with "wrong"
                    int with 999
                    uint with 53u
                },
                failOnMissingRequiredValues = false,
            )
        }

        expect(2) { e.exceptions.size }

        assertIs<InvalidValueException>(e.exceptions[0])
        assertIs<OutOfRangeException>(e.exceptions[1])
    }

    @Test
    fun getPropertyDefinitionByName() {
        expect(TestMarykModel.string) {
            TestMarykModel["string"] as FlexBytesDefinitionWrapper<*, *, *, *, *>
        }
        expect(TestMarykModel.int) {
            TestMarykModel["int"] as FixedBytesDefinitionWrapper<*, *, *, *, *>
        }
        expect(TestMarykModel.dateTime) {
            TestMarykModel["dateTime"] as FixedBytesDefinitionWrapper<*, *, *, *, *>
        }
        expect(TestMarykModel.bool) {
            TestMarykModel["bool"] as FixedBytesDefinitionWrapper<*, *, *, *, *>
        }
    }

    @Test
    fun getPropertyDefinitionByIndex() {
        expect(TestMarykModel.string) {
            TestMarykModel[1u] as FlexBytesDefinitionWrapper<*, *, *, *, *>
        }
        expect(TestMarykModel.int) {
            TestMarykModel[2u] as FixedBytesDefinitionWrapper<*, *, *, *, *>
        }
        expect(TestMarykModel.uint) {
            TestMarykModel[3u] as FixedBytesDefinitionWrapper<*, *, *, *, *>
        }
        expect(TestMarykModel.double) {
            TestMarykModel[4u] as FixedBytesDefinitionWrapper<*, *, *, *, *>
        }
        expect(TestMarykModel.dateTime) {
            TestMarykModel[5u] as FixedBytesDefinitionWrapper<*, *, *, *, *>
        }
        expect(TestMarykModel.bool) {
            TestMarykModel[6u] as FixedBytesDefinitionWrapper<*, *, *, *, *>
        }
    }

    @Test
    fun writeIntoJSONObject() {
        val output = buildString {
            val writer = JsonWriter {
                append(it)
            }
            TestMarykModel.Serializer.writeJson(testExtendedMarykModelObject, writer)
        }

        assertEquals(JSON, output)
    }

    @Test
    fun writeIntoPrettyJSONObject() {
        val output = buildString {
            val writer = JsonWriter(pretty = true) {
                append(it)
            }
            TestMarykModel.Serializer.writeJson(testExtendedMarykModelObject, writer)
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
              "embeddedValues": {
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

            TestMarykModel.Serializer.writeJson(testExtendedMarykModelObject, writer)
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
            embeddedValues:
              value: test
            multi: !S3(3)
              value: subInMulti!
            listOfString: [test1, another test, 🤗]

            """.trimIndent(),
            output
        )
    }

    @Test
    fun writeToProtoBufBytes() {
        val bc = ByteCollector()
        val cache = WriteCache()

        val map = TestMarykModel.create {
            string with "hay"
            int with 4
            uint with 32u
            double with 3.555
            dateTime with LocalDateTime(2017, 12, 4, 12, 13)
            bool with true
            enum with Option.V3
            reference with TestMarykModel.key(byteArrayOf(1, 5, 1, 5, 1, 5, 1))
        }

        bc.reserve(
            TestMarykModel.Serializer.calculateProtoBufLength(map, cache)
        )

        TestMarykModel.Serializer.writeProtoBuf(map, cache, bc::write)

        expect("0a036861791008182021713d0ad7a3700c4028ccf794d10530013803720701050105010501") { bc.bytes!!.toHex() }
    }

    @Test
    fun convertToProtoBufAndBack() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            TestMarykModel.Serializer.calculateProtoBufLength(testExtendedMarykModelObject, cache)
        )

        TestMarykModel.Serializer.writeProtoBuf(testExtendedMarykModelObject, cache, bc::write)

        expect("0a036861791008182021713d0ad7a3700c4028ccf794d10530013801420744e024be35fc0b4a08c29102bc87028844520908a4eb021203796573520a08d49a0212046168756d5a0e800000060180000058dfa324010162060a04746573746a0f1a0d0a0b737562496e4d756c7469217a0574657374317a0c616e6f7468657220746573747a04f09fa497") { bc.bytes!!.toHex() }

        expect(testExtendedMarykModelObject) { TestMarykModel.Serializer.readProtoBuf(bc.size, bc::read) }
    }

    @Test
    fun skipReadingUnknownFields() {
        val bytes =
            initByteArrayByHex("930408161205ffffffffff9404a20603686179a80608b00620b906400c70a3d70a3d72c80601d006028a07020105")
        var index = 0

        val map = TestMarykModel.Serializer.readProtoBuf(bytes.size, {
            bytes[index++]
        })

        expect(0) { map.size }
    }

    @Test
    fun convertFromJSON() {
        var input = ""
        var index = 0
        val reader = { input[index++] }
        val jsonReader = { JsonReader(reader = reader) }

        listOf(
            JSON,
            ALT_JSON,
            PRETTY_JSON_WITH_SKIP
        ).forEach { jsonInput ->
            input = jsonInput
            index = 0
            expect(testExtendedMarykModelObject) { TestMarykModel.Serializer.readJson(reader = jsonReader()) }
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
            TestMarykModel.Serializer.writeJson(testExtendedMarykModelObject, generator)

            var index = 0
            val reader = { JsonReader(reader = { output[index++] }) }
            expect(testExtendedMarykModelObject) {
                TestMarykModel.Serializer.readJson(reader = reader())
            }

            output = ""
        }
    }
}
