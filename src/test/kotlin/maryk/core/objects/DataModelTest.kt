package maryk.core.objects

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.SubMarykObject
import maryk.TestMarykObject
import maryk.TestValueObject
import maryk.core.json.JsonGenerator
import maryk.core.properties.exceptions.PropertyValidationUmbrellaException
import maryk.core.properties.types.Date
import maryk.core.properties.types.DateTime
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

private val textExtendedObject = TestMarykObject(
        string = "hey",
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
        valueObject = TestValueObject(43, DateTime(2017, 4, 1, 12, 55), true),
        subModel = SubMarykObject("test"),
        multi = TypedValue(2, SubMarykObject("subInMulti!"))
)

private const val json = "{\"string\":\"hey\",\"int\":4,\"uint\":32,\"double\":\"3.555\",\"bool\":true,\"dateTime\":\"2017-12-04T12:13\",\"enum\":\"V0\",\"list\":[34,2352,3423,766],\"set\":[\"2017-12-05\",\"2016-03-02\",\"1981-12-05\"],\"map\":{\"12:55\":\"yes\",\"10:03\":\"ahum\"},\"valueObject\":{\"int\":43,\"dateTime\":\"2017-04-01T12:55\",\"bool\":true},\"subModel\":{\"value\":\"test\"},\"multi\":[2,{\"value\":\"subInMulti!\"}]}"

private const val optimizedJson = "{\"string\":\"hey\",\"int\":4,\"uint\":32,\"double\":\"3.555\",\"bool\":true,\"dateTime\":\"1512389580,0\",\"enum\":\"-32768\",\"list\":[34,2352,3423,766],\"set\":[\"17505\",\"16862\",\"4356\"],\"map\":{\"46500000\":\"yes\",\"36180000\":\"ahum\"},\"valueObject\":\"gAAAKwGAAABY36MkAQE\",\"subModel\":{\"value\":\"test\"},\"multi\":[2,{\"value\":\"subInMulti!\"}]}"

private const val prettyJson = """{
	"string": "hey",
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
		"int": 43,
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
        TestMarykObject(mapOf(
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
    fun testValidation() {
        TestMarykObject.validate(testObject)
    }

    @Test
    fun testValidationFail() {
        shouldThrow<PropertyValidationUmbrellaException> {
            TestMarykObject.validate(testObject.copy(int = 9))
        }
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
    fun testJsonConversion() {
        var json = ""
        val generator = JsonGenerator {
            json += it
        }
        TestMarykObject.toJson(generator, textExtendedObject)

        json shouldBe json
    }

    @Test
    fun testOptimizedJsonConversion() {
        var json = ""
        val generator = JsonGenerator(optimized = true) {
            json += it
        }
        TestMarykObject.toJson(generator, textExtendedObject)

        json shouldBe optimizedJson
    }

    @Test
    fun testPrettyJsonConversion() {
        var json = ""
        val generator = JsonGenerator(pretty = true) {
            json += it
        }
        TestMarykObject.toJson(generator, textExtendedObject)

        json shouldBe prettyJson
    }
}
