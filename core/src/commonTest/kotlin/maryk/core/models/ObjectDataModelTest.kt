@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.models

import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.WriteCache
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.initByteArrayByHex
import maryk.lib.extensions.toHex
import maryk.lib.time.Date
import maryk.lib.time.DateTime
import maryk.lib.time.Time
import maryk.test.ByteCollector
import maryk.test.models.EmbeddedMarykObject
import maryk.test.models.Option.V3
import maryk.test.models.TestMarykObject
import maryk.test.models.TestValueObject
import maryk.test.shouldBe
import maryk.test.shouldThrow
import maryk.yaml.YamlWriter
import kotlin.test.Test

private val testObject = TestMarykObject(
    string = "haas",
    int = 4,
    uint = 53u,
    double = 3.5555,
    bool = true,
    dateTime = DateTime(year = 2017, month = 12, day = 5, hour = 12, minute = 40)
)

private val testExtendedObject = TestMarykObject(
    string = "hay",
    int = 4,
    double = 3.555,
    dateTime = DateTime(year = 2017, month = 12, day = 4, hour = 12, minute = 13),
    uint = 32u,
    bool = true,
    list = listOf(34, 2352, 3423, 766),
    set = setOf(
        Date(2017, 12, 5),
        Date(2016, 3, 2),
        Date(1981, 12, 5)
    ),
    map = mapOf(
        Time(12,55) to "yes",
        Time(10, 3) to "ahum"
    ),
    valueObject = TestValueObject(6, DateTime(2017, 4, 1, 12, 55), true),
    embeddedObject = EmbeddedMarykObject("test"),
    multi = TypedValue(V3, EmbeddedMarykObject("subInMulti!")),
    listOfString = listOf("test1", "another test", "🤗")
)

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

internal class ObjectDataModelTest {
    @Test
    fun constructByMap() {
        TestMarykObject.map {
            mapNonNulls(
                string with testObject.string,
                int with testObject.int,
                uint with testObject.uint,
                double with testObject.double,
                dateTime with testObject.dateTime,
                bool with testObject.bool,
                enum with testObject.enum
            )
        }.toDataObject() shouldBe testObject
    }

    @Test
    fun validate() {
        TestMarykObject.validate(testObject)
    }

    @Test
    fun failValidationWithIncorrectValues() {
        shouldThrow<ValidationUmbrellaException> {
            TestMarykObject.validate(testObject.copy(int = 9))
        }
    }

    @Test
    fun getPropertyDefinitionByName() {
        TestMarykObject.properties["string"] shouldBe TestMarykObject.Properties.string
        TestMarykObject.properties["int"] shouldBe TestMarykObject.Properties.int
        TestMarykObject.properties["dateTime"] shouldBe TestMarykObject.Properties.dateTime
        TestMarykObject.properties["bool"] shouldBe TestMarykObject.Properties.bool
    }

    @Test
    fun getPropertyDefinitionByIndex() {
        TestMarykObject.properties[1] shouldBe TestMarykObject.Properties.string
        TestMarykObject.properties[2] shouldBe TestMarykObject.Properties.int
        TestMarykObject.properties[3] shouldBe TestMarykObject.Properties.uint
        TestMarykObject.properties[4] shouldBe TestMarykObject.Properties.double
        TestMarykObject.properties[5] shouldBe TestMarykObject.Properties.dateTime
        TestMarykObject.properties[6] shouldBe TestMarykObject.Properties.bool
    }

    @Test
    fun getPropertiesByName() {
        TestMarykObject.properties.getPropertyGetter("string")!!.invoke(testExtendedObject) shouldBe "hay"
        TestMarykObject.properties.getPropertyGetter("int")!!.invoke(testExtendedObject) shouldBe 4
        TestMarykObject.properties.getPropertyGetter("dateTime")!!.invoke(testExtendedObject) shouldBe DateTime(year = 2017, month = 12, day = 4, hour = 12, minute = 13)
        TestMarykObject.properties.getPropertyGetter("bool")!!.invoke(testExtendedObject) shouldBe true
    }

    @Test
    fun getPropertiesByIndex() {
        TestMarykObject.properties.getPropertyGetter(1)!!.invoke(testExtendedObject) shouldBe "hay"
        TestMarykObject.properties.getPropertyGetter(2)!!.invoke(testExtendedObject) shouldBe 4
        TestMarykObject.properties.getPropertyGetter(3)!!.invoke(testExtendedObject) shouldBe 32u
        TestMarykObject.properties.getPropertyGetter(4)!!.invoke(testExtendedObject) shouldBe 3.555
        TestMarykObject.properties.getPropertyGetter(5)!!.invoke(testExtendedObject) shouldBe DateTime(year = 2017, month = 12, day = 4, hour = 12, minute = 13)
        TestMarykObject.properties.getPropertyGetter(6)!!.invoke(testExtendedObject) shouldBe true
    }

    @Test
    fun writeIntoJSONObject() {
        var output = ""
        val writer = JsonWriter {
            output += it
        }

        TestMarykObject.writeJson(testExtendedObject, writer)

        output shouldBe JSON
    }

    @Test
    fun writeIntoPrettyJSONObject() {
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
    fun writeIntoYAMLObject() {
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
    fun convertToProtoBufAndBack() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            TestMarykObject.calculateProtoBufLength(testExtendedObject, cache)
        )

        TestMarykObject.writeProtoBuf(testExtendedObject, cache, bc::write)

        bc.bytes!!.toHex() shouldBe "0a036861791008182021713d0ad7a3700c4028ccf794d10530013801420744e024be35fc0b4a08c29102bc87028844520908a4eb021203796573520a08d49a0212046168756d5a0e800000060180000058dfa324010162060a04746573746a0f1a0d0a0b737562496e4d756c7469217a0574657374317a0c616e6f7468657220746573747a04f09fa497"

        TestMarykObject.readProtoBuf(bc.size, bc::read).toDataObject() shouldBe testExtendedObject
    }

    @Test
    fun skipReadingUnknownFields() {
        val bytes = initByteArrayByHex("930408161205ffffffffff9404a20603686179a80608b00620b906400c70a3d70a3d72c80601d006028a07020105")
        var index = 0

        val map = TestMarykObject.readProtoBuf(bytes.size, {
            bytes[index++]
        })

        map.size shouldBe 0
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
            TestMarykObject.readJson(reader = jsonReader()).toDataObject() shouldBe testExtendedObject
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
            TestMarykObject.readJson(reader = reader()).toDataObject() shouldBe testExtendedObject

            output = ""
        }
    }
}
