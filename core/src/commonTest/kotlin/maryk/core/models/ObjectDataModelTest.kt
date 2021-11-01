package maryk.core.models

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.WriteCache
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.initByteArrayByHex
import maryk.lib.extensions.toHex
import maryk.lib.time.Time
import maryk.test.ByteCollector
import maryk.test.models.EmbeddedMarykObject
import maryk.test.models.SimpleMarykTypeEnumWithObject.S3
import maryk.test.models.TestMarykObject
import maryk.test.models.TestValueObject
import maryk.yaml.YamlWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.expect

private val testObject = TestMarykObject(
    string = "haas",
    int = 4,
    uint = 53u,
    double = 3.5555,
    bool = true,
    dateTime = LocalDateTime(2017, 12, 5, 12, 40)
)

private val testExtendedObject = TestMarykObject(
    string = "hay",
    int = 4,
    double = 3.555,
    dateTime = LocalDateTime(2017, 12, 4, 12, 13),
    uint = 32u,
    bool = true,
    list = listOf(34, 2352, 3423, 766),
    set = setOf(
        LocalDate(2017, 12, 5),
        LocalDate(2016, 3, 2),
        LocalDate(1981, 12, 5)
    ),
    map = mapOf(
        Time(12, 55) to "yes",
        Time(10, 3) to "ahum"
    ),
    valueObject = TestValueObject(6, LocalDateTime(2017, 4, 1, 12, 55), true),
    embeddedObject = EmbeddedMarykObject("test"),
    multi = TypedValue(S3, EmbeddedMarykObject("subInMulti!")),
    listOfString = listOf("test1", "another test", "🤗")
)

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
  "multi": ["T3(3)", {
    "value": "subInMulti!"
  }],
  "listOfString": ["test1", "another test", "🤗"]
}"""

internal class ObjectDataModelTest {
    @Test
    fun constructByMap() {
        expect(testObject) {
            TestMarykObject.values {
                mapNonNulls(
                    string with testObject.string,
                    int with testObject.int,
                    uint with testObject.uint,
                    double with testObject.double,
                    dateTime with testObject.dateTime,
                    bool with testObject.bool,
                    enum with testObject.enum
                )
            }.toDataObject()
        }
    }

    @Test
    fun validate() {
        TestMarykObject.validate(testObject)
    }

    @Test
    fun failValidationWithIncorrectValues() {
        assertFailsWith<ValidationUmbrellaException> {
            TestMarykObject.validate(testObject.copy(int = 9))
        }
    }

    @Test
    fun getPropertyDefinitionByName() {
        expect(TestMarykObject.Properties.string) {
            TestMarykObject.properties["string"] as FlexBytesDefinitionWrapper<*, *, *, *, *>
        }
        expect(TestMarykObject.Properties.int) {
            TestMarykObject.properties["int"] as FixedBytesDefinitionWrapper<*, *, *, *, *>
        }
        expect(TestMarykObject.Properties.dateTime) {
            TestMarykObject.properties["dateTime"] as FixedBytesDefinitionWrapper<*, *, *, *, *>
        }
        expect(TestMarykObject.Properties.bool) {
            TestMarykObject.properties["bool"] as FixedBytesDefinitionWrapper<*, *, *, *, *>
        }
    }

    @Test
    fun getPropertyDefinitionByIndex() {
        expect(TestMarykObject.Properties.string) {
            TestMarykObject.properties[1u] as FlexBytesDefinitionWrapper<*, *, *, *, *>
        }
        expect(TestMarykObject.Properties.int) {
            TestMarykObject.properties[2u] as FixedBytesDefinitionWrapper<*, *, *, *, *>
        }
        expect(TestMarykObject.Properties.uint) {
            TestMarykObject.properties[3u] as FixedBytesDefinitionWrapper<*, *, *, *, *>
        }
        expect(TestMarykObject.Properties.double) {
            TestMarykObject.properties[4u] as FixedBytesDefinitionWrapper<*, *, *, *, *>
        }
        expect(TestMarykObject.Properties.dateTime) {
            TestMarykObject.properties[5u] as FixedBytesDefinitionWrapper<*, *, *, *, *>
        }
        expect(TestMarykObject.Properties.bool) {
            TestMarykObject.properties[6u] as FixedBytesDefinitionWrapper<*, *, *, *, *>
        }
    }

    @Test
    fun getPropertiesByName() {
        expect("hay") {
            TestMarykObject.properties.getPropertyGetter("string")!!.invoke(testExtendedObject)
        }
        expect(4) {
            TestMarykObject.properties.getPropertyGetter("int")!!.invoke(testExtendedObject)
        }
        expect(LocalDateTime(2017, 12, 4, 12, 13)) {
            TestMarykObject.properties.getPropertyGetter("dateTime")!!.invoke(testExtendedObject)
        }
        expect(true) {
            TestMarykObject.properties.getPropertyGetter("bool")!!.invoke(testExtendedObject)
        }
    }

    @Test
    fun getPropertiesByIndex() {
        expect("hay") {
            TestMarykObject.properties.getPropertyGetter(1u)!!.invoke(testExtendedObject)
        }
        expect(4) {
            TestMarykObject.properties.getPropertyGetter(2u)!!.invoke(testExtendedObject)
        }
        expect(32u) {
            TestMarykObject.properties.getPropertyGetter(3u)!!.invoke(testExtendedObject)
        }
        expect(3.555) {
            TestMarykObject.properties.getPropertyGetter(4u)!!.invoke(testExtendedObject)
        }
        expect(LocalDateTime(2017, 12, 4, 12, 13)) {
            TestMarykObject.properties.getPropertyGetter(5u)!!.invoke(testExtendedObject)
        }
        expect(true) {
            TestMarykObject.properties.getPropertyGetter(6u)!!.invoke(testExtendedObject)
        }
    }

    @Test
    fun writeIntoJSONObject() {
        val output = buildString {
            val writer = JsonWriter {
                append(it)
            }

            TestMarykObject.writeJson(testExtendedObject, writer)
        }

        assertEquals(JSON, output)
    }

    @Test
    fun writeIntoPrettyJSONObject() {
        val output = buildString {
            val writer = JsonWriter(pretty = true) {
                append(it)
            }
            TestMarykObject.writeJson(testExtendedObject, writer)
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

            TestMarykObject.writeJson(testExtendedObject, writer)
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
            TestMarykObject.calculateProtoBufLength(testExtendedObject, cache)
        )

        TestMarykObject.writeProtoBuf(testExtendedObject, cache, bc::write)

        expect("0a036861791008182021713d0ad7a3700c4028ccf794d10530013801420744e024be35fc0b4a08c29102bc87028844520908a4eb021203796573520a08d49a0212046168756d5a0e800000060180000058dfa324010162060a04746573746a0f1a0d0a0b737562496e4d756c7469217a0574657374317a0c616e6f7468657220746573747a04f09fa497") {
            bc.bytes!!.toHex()
        }

        expect(testExtendedObject) { TestMarykObject.readProtoBuf(bc.size, bc::read).toDataObject() }
    }

    @Test
    fun skipReadingUnknownFields() {
        val bytes =
            initByteArrayByHex("930408161205ffffffffff9404a20603686179a80608b00620b906400c70a3d70a3d72c80601d006028a07020105")
        var index = 0

        val map = TestMarykObject.readProtoBuf(bytes.size, {
            bytes[index++]
        })

        expect(0) { map.size }
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
            expect(testExtendedObject) { TestMarykObject.readJson(reader = jsonReader()).toDataObject() }
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
            TestMarykObject.writeJson(testExtendedObject, generator)

            var index = 0
            val reader = { JsonReader(reader = { output[index++] }) }
            expect(testExtendedObject) { TestMarykObject.readJson(reader = reader()).toDataObject() }

            output = ""
        }
    }
}
