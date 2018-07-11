package maryk.core.models

import maryk.EmbeddedMarykObject
import maryk.Option
import maryk.TestMarykObject
import maryk.TestValueObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.ByteCollector
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.protobuf.WriteCache
import maryk.core.query.DataModelContext
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.extensions.initByteArrayByHex
import maryk.lib.extensions.toHex
import maryk.lib.time.Date
import maryk.lib.time.DateTime
import maryk.lib.time.Time
import maryk.test.shouldBe
import maryk.test.shouldThrow
import maryk.yaml.YamlWriter
import kotlin.test.Test

private val testObject = TestMarykObject(
    string = "haas",
    int = 4,
    uint = 53.toUInt32(),
    double = 3.5555,
    bool = true,
    dateTime = DateTime(year = 2017, month = 12, day = 5, hour = 12, minute = 40)
)

private val testExtendedObject = TestMarykObject(
    string = "hay",
    int = 4,
    double = 3.555,
    dateTime = DateTime(year = 2017, month = 12, day = 4, hour = 12, minute = 13),
    uint = 32.toUInt32(),
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
    multi = TypedValue(Option.V2, EmbeddedMarykObject("subInMulti!")),
    listOfString = listOf("test1", "another test", "🤗")
)
private val testMap = TestMarykObject.map {
    mapOf(
        string with "hay",
        int with 4,
        uint with 32.toUInt32(),
        double with 3.555,
        dateTime with DateTime(year = 2017, month = 12, day = 4, hour = 12, minute = 13),
        bool with true,
        enum with Option.V0,
        list with listOf(34, 2352, 3423, 766),
        set with setOf(
            Date(2017, 12, 5),
            Date(2016, 3, 2),
            Date(1981, 12, 5)
        ),
        map with mapOf(
            Time(12, 55) to "yes",
            Time(10, 3) to "ahum"
        ),
        valueObject with TestValueObject(6, DateTime(2017, 4, 1, 12, 55), true),
        embeddedObject with EmbeddedMarykObject("test"),
        multi with TypedValue(Option.V2, EmbeddedMarykObject("subInMulti!")),
        listOfString with listOf("test1", "another test", "🤗")
    )
}

private const val JSON = "{\"string\":\"hay\",\"int\":4,\"uint\":32,\"double\":\"3.555\",\"dateTime\":\"2017-12-04T12:13\",\"bool\":true,\"enum\":\"V0\",\"list\":[34,2352,3423,766],\"set\":[\"2017-12-05\",\"2016-03-02\",\"1981-12-05\"],\"map\":{\"12:55\":\"yes\",\"10:03\":\"ahum\"},\"valueObject\":{\"int\":6,\"dateTime\":\"2017-04-01T12:55\",\"bool\":true},\"embeddedObject\":{\"value\":\"test\"},\"multi\":[\"V2\",{\"value\":\"subInMulti!\"}],\"listOfString\":[\"test1\",\"another test\",\"\uD83E\uDD17\"]}"

// Test if unknown values will be skipped
private const val PRETTY_JSON_WITH_SKIP = """{
	"string": "hay",
	"int": 4,
	"uint": 32,
	"double": "3.555",
	"bool": true,
	"dateTime": "2017-12-04T12:13",
	"enum": "V0",
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
	"multi": ["V2", {
		"value": "subInMulti!"
	}],
	"listOfString": ["test1", "another test", "🤗"]
}"""

internal class ObjectDataModelTest {
    @Test
    fun construct_by_map() {
        TestMarykObject.map {
            mapOf(
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
    fun validate_by_DataObject() {
        TestMarykObject.validate(testObject)
    }

    @Test
    fun validate_by_Map() {
        TestMarykObject.validate(testMap)
    }

    @Test
    fun fail_validation_with_incorrect_values_in_DataObject() {
        shouldThrow<ValidationUmbrellaException> {
            TestMarykObject.validate(testObject.copy(int = 9))
        }
    }

    @Test
    fun fail_validation_with_incorrect_values_in_map() {
        val e = shouldThrow<ValidationUmbrellaException> {
            TestMarykObject.validate(
                TestMarykObject.map {
                    mapOf(
                        string with "wrong",
                        int with 999
                    )
                }
            )
        }

        e.exceptions.size shouldBe 2

        (e.exceptions[0] is InvalidValueException) shouldBe true
        (e.exceptions[1] is OutOfRangeException) shouldBe true
    }

    @Test
    fun get_property_definition_by_name() {
        TestMarykObject.properties.getDefinition("string") shouldBe TestMarykObject.Properties.string
        TestMarykObject.properties.getDefinition("int") shouldBe TestMarykObject.Properties.int
        TestMarykObject.properties.getDefinition("dateTime") shouldBe TestMarykObject.Properties.dateTime
        TestMarykObject.properties.getDefinition("bool") shouldBe TestMarykObject.Properties.bool
    }

    @Test
    fun get_property_definition_by_index() {
        TestMarykObject.properties.getDefinition(0) shouldBe TestMarykObject.Properties.string
        TestMarykObject.properties.getDefinition(1) shouldBe TestMarykObject.Properties.int
        TestMarykObject.properties.getDefinition(2) shouldBe TestMarykObject.Properties.uint
        TestMarykObject.properties.getDefinition(3) shouldBe TestMarykObject.Properties.double
        TestMarykObject.properties.getDefinition(4) shouldBe TestMarykObject.Properties.dateTime
        TestMarykObject.properties.getDefinition(5) shouldBe TestMarykObject.Properties.bool
    }

    @Test
    fun get_properties_by_name() {
        TestMarykObject.properties.getPropertyGetter("string")!!.invoke(testExtendedObject) shouldBe "hay"
        TestMarykObject.properties.getPropertyGetter("int")!!.invoke(testExtendedObject) shouldBe 4
        TestMarykObject.properties.getPropertyGetter("dateTime")!!.invoke(testExtendedObject) shouldBe DateTime(year = 2017, month = 12, day = 4, hour = 12, minute = 13)
        TestMarykObject.properties.getPropertyGetter("bool")!!.invoke(testExtendedObject) shouldBe true
    }

    @Test
    fun get_properties_by_index() {
        TestMarykObject.properties.getPropertyGetter(0)!!.invoke(testExtendedObject) shouldBe "hay"
        TestMarykObject.properties.getPropertyGetter(1)!!.invoke(testExtendedObject) shouldBe 4
        TestMarykObject.properties.getPropertyGetter(2)!!.invoke(testExtendedObject) shouldBe 32.toUInt32()
        TestMarykObject.properties.getPropertyGetter(3)!!.invoke(testExtendedObject) shouldBe 3.555
        TestMarykObject.properties.getPropertyGetter(4)!!.invoke(testExtendedObject) shouldBe DateTime(year = 2017, month = 12, day = 4, hour = 12, minute = 13)
        TestMarykObject.properties.getPropertyGetter(5)!!.invoke(testExtendedObject) shouldBe true
    }

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
        	"enum": "V0",
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
        	"multi": ["V2", {
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
        enum: V0
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
        multi: !V2
          value: subInMulti!
        listOfString: [test1, another test, 🤗]

        """.trimIndent()
    }

    @Test
    fun write_map_to_ProtoBuf_bytes() {
        val bc = ByteCollector()
        val cache = WriteCache()

        val map = TestMarykObject.map {
            mapOf(
                string with "hay",
                int with 4,
                uint with 32.toUInt32(),
                double with 3.555,
                dateTime with DateTime(year = 2017, month = 12, day = 4, hour = 12, minute = 13),
                bool with true,
                enum with Option.V2,
                reference with TestMarykObject.key(byteArrayOf(1, 5, 1, 5, 1, 5, 1, 5, 1))
            )
        }

        bc.reserve(
            TestMarykObject.calculateProtoBufLength(map, cache)
        )

        TestMarykObject.writeProtoBuf(map, cache, bc::write)

        bc.bytes!!.toHex() shouldBe "02036861790808102019400c70a3d70a3d7220ccf794d105280130026a09010501050105010501"
    }

    @Test
    fun convert_ProtoBuf_bytes_to_map() {
        val bytes = initByteArrayByHex("02036861790808102019400c70a3d70a3d7220ccf794d105280130026a09010501050105010501")
        var index = 0

        val map = TestMarykObject.readProtoBuf(bytes.size, {
            bytes[index++]
        })

        map.size shouldBe 8
        map { string } shouldBe "hay"
        map { int } shouldBe 4
        map { uint } shouldBe 32.toUInt32()
        map { double } shouldBe 3.555
        map { dateTime } shouldBe DateTime(year = 2017, month = 12, day = 4, hour = 12, minute = 13)
        map { bool } shouldBe true
        map { enum } shouldBe Option.V2
        map { reference }!!.bytes.toHex() shouldBe "010501050105010501"
    }

    @Test
    fun convert_map_to_ProtoBuf_and_back() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            TestMarykObject.calculateProtoBufLength(testMap, cache)
        )

        TestMarykObject.writeProtoBuf(testMap, cache, bc::write)

        TestMarykObject.readProtoBuf(bc.size, bc::read) shouldBe testMap
    }

    @Test
    fun convert_DataObject_to_ProtoBuf_and_back() {
        val bc = ByteCollector()
        val cache = WriteCache()

        bc.reserve(
            TestMarykObject.calculateProtoBufLength(testExtendedObject, cache)
        )

        TestMarykObject.writeProtoBuf(testExtendedObject, cache, bc::write)

        TestMarykObject.readProtoBuf(bc.size, bc::read).toDataObject() shouldBe testExtendedObject
    }

    @Test
    fun skip_reading_unknown_fields() {
        val bytes = initByteArrayByHex("930408161205ffffffffff9404a20603686179a80608b00620b906400c70a3d70a3d72c80601d006028a07020105")
        var index = 0

        val map = TestMarykObject.readProtoBuf(bytes.size, {
            bytes[index++]
        })

        map.size shouldBe 0
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
            TestMarykObject.readJson(reader = jsonReader()).toDataObject() shouldBe testExtendedObject
        }
    }

    @Test
    fun convert_JSON_to_map() {
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
            TestMarykObject.readJson(reader = jsonReader()) shouldBe testMap
        }
    }

    @Test
    fun convert_map_to_JSON_and_back_to_map() {
        var output = ""
        val writer = { string: String -> output += string }

        listOf(
            JsonWriter(writer = writer),
            JsonWriter(pretty = true, writer = writer)
        ).forEach { generator ->
            TestMarykObject.writeJson(testMap, generator)

            var index = 0
            val reader = { JsonReader(reader = { output[index++] }) }
            TestMarykObject.readJson(reader = reader()) shouldBe testMap

            output = ""
        }
    }

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(EmbeddedMarykObject, ObjectDataModel.Model, { DataModelContext() }, ::compareDataModels)
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(EmbeddedMarykObject, ObjectDataModel.Model, { DataModelContext() }, ::compareDataModels)
    }

    @Test
    fun convert_definition_to_YAML_and_back() {
        checkYamlConversion(EmbeddedMarykObject, ObjectDataModel.Model, { DataModelContext() }, ::compareDataModels)
    }
}
