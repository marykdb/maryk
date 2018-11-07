@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.models

import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.WriteCache
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.toHex
import maryk.lib.time.Date
import maryk.lib.time.DateTime
import maryk.lib.time.Time
import maryk.test.ByteCollector
import maryk.test.models.EmbeddedMarykObject
import maryk.test.models.Option.V1
import maryk.test.models.Option.V3
import maryk.test.models.TestMarykObject
import maryk.test.models.TestValueObject
import maryk.test.shouldBe
import maryk.yaml.YamlWriter
import kotlin.test.Test

private val testExtendedObject = TestMarykObject.map {
    mapNonNulls(
        string with "hay",
        int with 4,
        uint with 32u,
        double with 3.555,
        dateTime with DateTime(year = 2017, month = 12, day = 4, hour = 12, minute = 13),
        bool with true,
        enum with V1,
        list with listOf(34, 2352, 3423, 766),
        set with setOf(
            Date(2017, 12, 5),
            Date(2016, 3, 2),
            Date(1981, 12, 5)
        ),
        map with mapOf(
            Time(12,55) to "yes",
            Time(10, 3) to "ahum"
        ),
        valueObject with TestValueObject(6, DateTime(2017, 4, 1, 12, 55), true),
        embeddedObject with EmbeddedMarykObject.map {
            mapNonNulls(
                value with "test"
            )
        },
        multi with TypedValue(V3, EmbeddedMarykObject("subInMulti!")),
        listOfString with listOf("test1", "another test", "🤗")
    )
}

private const val JSON = "{\"string\":\"hay\",\"int\":4,\"uint\":32,\"double\":\"3.555\",\"dateTime\":\"2017-12-04T12:13\",\"bool\":true,\"enum\":\"V1\",\"list\":[34,2352,3423,766],\"set\":[\"2017-12-05\",\"2016-03-02\",\"1981-12-05\"],\"map\":{\"12:55\":\"yes\",\"10:03\":\"ahum\"},\"valueObject\":{\"int\":6,\"dateTime\":\"2017-04-01T12:55\",\"bool\":true},\"embeddedObject\":{\"value\":\"test\"},\"multi\":[\"V3\",{\"value\":\"subInMulti!\"}],\"listOfString\":[\"test1\",\"another test\",\"\uD83E\uDD17\"]}"

// Test if unknown values will be skipped
private const val PRETTY_JSON_WITH_SKIP = """{
	"string": "hay",
	"int": 4,
	"uint": 32,
	"double": "3.555",
	"bool": true,
	"dateTime": "2017-12-04T12:13",
	"enum": "V1",
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
	"multi": ["V3", {
		"value": "subInMulti!"
	}],
	"listOfString": ["test1", "another test", "🤗"]
}"""

internal class ObjectDataModelAsValuesTest {
    @Test
    fun write_into_a_JSON_object() {
        var output = ""
        val writer = JsonWriter {
            output += it
        }

        TestMarykObject.writeJson(testExtendedObject, writer)

        output shouldBe JSON
    }

    @Test
    fun write_into_a_pretty_JSON_object() {
        var output = ""
        val writer = JsonWriter(pretty = true) {
            output += it
        }

        TestMarykObject.writeJson(testExtendedObject, writer)

        output shouldBe """
        {
        	"string": "hay",
        	"int": 4,
        	"uint": 32,
        	"double": "3.555",
        	"dateTime": "2017-12-04T12:13",
        	"bool": true,
        	"enum": "V1",
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
        	"multi": ["V3", {
        		"value": "subInMulti!"
        	}],
        	"listOfString": ["test1", "another test", "🤗"]
        }
        """.trimIndent()
    }

    @Test
    fun write_into_a_YAML_object() {
        var output = ""
        val writer = YamlWriter {
            output += it
        }

        TestMarykObject.writeJson(testExtendedObject, writer)

        output shouldBe """
        string: hay
        int: 4
        uint: 32
        double: 3.555
        dateTime: '2017-12-04T12:13'
        bool: true
        enum: V1
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
        multi: !V3
          value: subInMulti!
        listOfString: [test1, another test, 🤗]

        """.trimIndent()
    }

    @Test
    fun convert_to_ProtoBuf_and_back() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            TestMarykObject.calculateProtoBufLength(testExtendedObject, cache)
        )

        TestMarykObject.writeProtoBuf(testExtendedObject, cache, bc::write)

        bc.bytes!!.toHex() shouldBe "0a036861791008182021713d0ad7a3700c4028ccf794d10530013801420744e024be35fc0b4a08c29102bc87028844520908a4eb021203796573520a08d49a0212046168756d5a0e800000060180000058dfa324010162060a04746573746a0f1a0d0a0b737562496e4d756c7469217a0574657374317a0c616e6f7468657220746573747a04f09fa497"

        TestMarykObject.readProtoBuf(bc.size, bc::read) shouldBe testExtendedObject
    }

    @Test
    fun convert_JSON_to_DataObject() {
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
            TestMarykObject.readJson(reader = jsonReader()) shouldBe testExtendedObject
        }
    }

    @Test
    fun convert_to_JSON_and_back() {
        var output = ""
        val writer = { string: String -> output += string }

        listOf(
            JsonWriter(writer = writer),
            JsonWriter(pretty = true, writer = writer)
        ).forEach { generator ->
            TestMarykObject.writeJson(testExtendedObject, generator)

            var index = 0
            val reader = { JsonReader(reader = { output[index++] }) }
            TestMarykObject.readJson(reader = reader()) shouldBe testExtendedObject

            output = ""
        }
    }
}
