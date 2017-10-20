package maryk.core.objects

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.Option
import maryk.SubMarykObject
import maryk.TestMarykObject
import maryk.TestValueObject
import maryk.core.extensions.initByteArrayByHex
import maryk.core.extensions.toHex
import maryk.core.json.JsonGenerator
import maryk.core.json.JsonParser
import maryk.core.properties.GrowableByteCollector
import maryk.core.properties.exceptions.PropertyInvalidValueException
import maryk.core.properties.exceptions.PropertyOutOfRangeException
import maryk.core.properties.exceptions.PropertyValidationUmbrellaException
import maryk.core.properties.types.Date
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.Key
import maryk.core.properties.types.Time
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.toUInt32
import org.junit.Test

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
        subModel = SubMarykObject("test"),
        multi = TypedValue(2, SubMarykObject("subInMulti!"))
)
private val testMap = sortedMapOf(
        0 to "hay",
        1 to 4,
        2 to 32.toUInt32(),
        3 to 3.555,
        4 to DateTime(year = 2017, month = 12, day = 4, hour = 12, minute = 13),
        5 to true,
        6 to Option.V0,
        7 to listOf(34, 2352, 3423, 766),
        8 to setOf(
                Date(2017, 12, 5),
                Date(2016, 3, 2),
                Date(1981, 12, 5)
        ),
        9 to mapOf(
                Time(12,55) to "yes",
                Time(10, 3) to "ahum"
        ),
        10 to TestValueObject(6, DateTime(2017, 4, 1, 12, 55), true),
        11 to SubMarykObject("test"),
        12 to TypedValue(2, SubMarykObject("subInMulti!"))
)

private const val json = "{\"string\":\"hay\",\"int\":4,\"uint\":32,\"double\":\"3.555\",\"bool\":true,\"dateTime\":\"2017-12-04T12:13\",\"enum\":\"V0\",\"list\":[34,2352,3423,766],\"set\":[\"2017-12-05\",\"2016-03-02\",\"1981-12-05\"],\"map\":{\"12:55\":\"yes\",\"10:03\":\"ahum\"},\"valueObject\":{\"int\":6,\"dateTime\":\"2017-04-01T12:55\",\"bool\":true},\"subModel\":{\"value\":\"test\"},\"multi\":[2,{\"value\":\"subInMulti!\"}]}"

private const val prettyJson = """{
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
	"valueObject": {
		"int": 6,
		"dateTime": "2017-04-01T12:55",
		"bool": true
	},
	"subModel": {
		"value": "test"
	},
	"multi": [2, {
		"value": "subInMulti!"
	}]
}"""

// Test if unknown values will be skipped
private const val prettyJsonWithSkip = """{
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
	"subModel": {
		"value": "test"
	},
	"multi": [2, {
		"value": "subInMulti!"
	}]
}"""

internal class DataModelTest {
    @Test
    fun testIndexConstruction() {
        TestMarykObject.construct(mapOf(
                0 to testObject.string,
                1 to testObject.int,
                2 to testObject.uint,
                3 to testObject.double,
                4 to testObject.dateTime,
                5 to testObject.bool,
                6 to testObject.enum
        )) shouldBe testObject
    }

    @Test
    fun testValidationWithDataObject() {
        TestMarykObject.validate(testObject)
    }

    @Test
    fun testValidationWithMap() {
        TestMarykObject.validate(testMap)
    }

    @Test
    fun testValidationFail() {
        shouldThrow<PropertyValidationUmbrellaException> {
            TestMarykObject.validate(testObject.copy(int = 9))
        }
    }

    @Test
    fun testValidationWithMapFail() {
        val e = shouldThrow<PropertyValidationUmbrellaException> {
            TestMarykObject.validate(mapOf(
                0 to "wrong",
                1 to 999
            ))
        }

        e.exceptions.size shouldBe 2

        (e.exceptions[0] is PropertyInvalidValueException) shouldBe true
        (e.exceptions[1] is PropertyOutOfRangeException) shouldBe true
    }

    @Test
    fun testDefinitionByName() {
        TestMarykObject.getDefinition("string") shouldBe TestMarykObject.Properties.string
        TestMarykObject.getDefinition("int") shouldBe TestMarykObject.Properties.int
        TestMarykObject.getDefinition("dateTime") shouldBe TestMarykObject.Properties.dateTime
        TestMarykObject.getDefinition("bool") shouldBe TestMarykObject.Properties.bool
    }

    @Test
    fun testDefinitionByIndex() {
        TestMarykObject.getDefinition(0) shouldBe TestMarykObject.Properties.string
        TestMarykObject.getDefinition(1) shouldBe TestMarykObject.Properties.int
        TestMarykObject.getDefinition(2) shouldBe TestMarykObject.Properties.uint
        TestMarykObject.getDefinition(3) shouldBe TestMarykObject.Properties.double
        TestMarykObject.getDefinition(4) shouldBe TestMarykObject.Properties.dateTime
        TestMarykObject.getDefinition(5) shouldBe TestMarykObject.Properties.bool
    }

    @Test
    fun testPropertyGetterByName() {
        TestMarykObject.getPropertyGetter("string") shouldBe TestMarykObject::string
        TestMarykObject.getPropertyGetter("int") shouldBe TestMarykObject::int
        TestMarykObject.getPropertyGetter("dateTime") shouldBe TestMarykObject::dateTime
        TestMarykObject.getPropertyGetter("bool") shouldBe TestMarykObject::bool
    }

    @Test
    fun testPropertyGetterByIndex() {
        TestMarykObject.getPropertyGetter(0) shouldBe TestMarykObject::string
        TestMarykObject.getPropertyGetter(1) shouldBe TestMarykObject::int
        TestMarykObject.getPropertyGetter(2) shouldBe TestMarykObject::uint
        TestMarykObject.getPropertyGetter(3) shouldBe TestMarykObject::double
        TestMarykObject.getPropertyGetter(4) shouldBe TestMarykObject::dateTime
        TestMarykObject.getPropertyGetter(5) shouldBe TestMarykObject::bool
    }

    @Test
    fun testToJsonConversion() {
        var output = ""
        val writer = { string: String -> output += string }

        mapOf(
                json to JsonGenerator(writer = writer),
                prettyJson to JsonGenerator(pretty = true, writer = writer)
        ).forEach { result, generator ->
            TestMarykObject.toJson(generator, testExtendedObject)

            output shouldBe result
            output = ""
        }
    }

    @Test
    fun testToProtoBufConversionWithMap() {
        val byteCollector = GrowableByteCollector()

        TestMarykObject.toProtoBuf(mapOf(
                0 to "hay",
                1 to 4,
                2 to 32.toUInt32(),
                3 to 3.555,
                5 to true,
                6 to Option.V2,
                13 to SubMarykObject.key.get(byteArrayOf(1, 5))
        ), byteCollector::reserve, byteCollector::write)

        byteCollector.bytes.toHex() shouldBe "02036861790808102019400c70a3d70a3d72280130026a020105"
    }

    @Test
    fun testFromProtoBufConversionWithMap() {
        val bytes = initByteArrayByHex("02036861790808102019400c70a3d70a3d72280130026a1001050105010501050105010501050105")
        var index = 0

        val map = TestMarykObject.fromProtoBuf(bytes.size) {
            bytes[index++]
        }

        map.size shouldBe 7
        map[0] shouldBe "hay"
        map[1] shouldBe 4
        map[2] shouldBe 32.toUInt32()
        map[3] shouldBe 3.555
        map[5] shouldBe true
        map[6] shouldBe Option.V2
        (map[13] as Key<*>).bytes.toHex() shouldBe "01050105010501050105010501050105"
    }

    @Test
    fun testProtoBufConversion() {
        val byteCollector = GrowableByteCollector()

        TestMarykObject.toProtoBuf(testExtendedObject, byteCollector::reserve, byteCollector::write)

        TestMarykObject.fromProtoBufToObject(byteCollector.size, byteCollector::read) shouldBe testExtendedObject
    }

    @Test
    fun testProtoBufWithNoLengthConversion() {
        val byteCollector = GrowableByteCollector()

        TestMarykObject.toProtoBuf(testExtendedObject, byteCollector::reserve, byteCollector::write)

        TestMarykObject.fromProtoBufToObject(byteCollector.size, byteCollector::read) shouldBe testExtendedObject
    }

    @Test
    fun testSkipProtoBufConversion() {
        val bytes = initByteArrayByHex("930408161205ffffffffff9404a20603686179a80608b00620b906400c70a3d70a3d72c80601d006028a07020105")
        var index = 0

        val map = TestMarykObject.fromProtoBuf(bytes.size) {
            bytes[index++]
        }

        map.size shouldBe 0
    }

    @Test
    fun testFromJsonToObjectConversion() {
        var input = ""
        var index = 0
        val reader = { input[index++] }
        val parser = { JsonParser(reader = reader) }

        listOf(
                json,
                prettyJsonWithSkip
        ).forEach { jsonInput ->
            input = jsonInput
            index = 0
            TestMarykObject.fromJsonToObject(parser()) shouldBe testExtendedObject
        }
    }

    @Test
    fun testFromJsonToMapConversion() {
        var input = ""
        var index = 0
        val reader = { input[index++] }
        val parser = { JsonParser(reader = reader) }

        listOf(
                json,
                prettyJsonWithSkip
        ).forEach { jsonInput ->
            input = jsonInput
            index = 0
            TestMarykObject.fromJson(parser()) shouldBe testMap
        }
    }

    @Test
    fun testMapToJsonAndBackConversion() {
        var output = ""
        val writer = { string: String -> output += string }

        listOf(
                JsonGenerator(writer = writer),
                JsonGenerator(pretty = true, writer = writer)
        ).forEach { generator ->
            TestMarykObject.toJson(generator, testMap)

            var index = 0
            val parser = { JsonParser(reader = { output[index++] }) }
            TestMarykObject.fromJson(parser()) shouldBe testMap

            output = ""
        }
    }
}

