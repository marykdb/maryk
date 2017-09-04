package maryk.core.objects

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.TestMarykObject
import maryk.core.properties.exceptions.PropertyValidationUmbrellaException
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.numeric.toUInt32
import org.junit.Test

val testObject = TestMarykObject(
        string = "haas",
        int = 4,
        uint = 53.toUInt32(),
        double = 3.5555,
        bool = true,
        dateTime = DateTime(year = 2017, month = 12, day = 5, hour = 12, minute = 40)
)

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
}
