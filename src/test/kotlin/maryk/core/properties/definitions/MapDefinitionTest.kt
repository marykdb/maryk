package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.TestMarykObject
import maryk.core.properties.exceptions.*
import maryk.core.properties.types.numeric.SInt32
import org.junit.Test
import kotlin.test.assertTrue

internal class MapDefinitionTest {
    val intDef = NumberDefinition(
            type = SInt32,
            required = true,
            maxValue = 1000
    )

    val stringDef = StringDefinition(
            required = true,
            regEx = "#.*"
    )

    val subModelDef = SubModelDefinition(
            name = "marykRef",
            dataModel = TestMarykObject,
            required = true
    )

    val def = MapDefinition(
            name = "intStringMap",
            minSize = 2,
            maxSize = 4,
            keyDefinition = intDef,
            valueDefinition = stringDef
    )

    val defSubModel = MapDefinition(
            name = "intSubmodelMap",
            keyDefinition = intDef,
            valueDefinition = subModelDef
    )

    @Test
    fun testReference() {
        def.getRef().dataModel shouldBe null
        defSubModel.getRef().dataModel shouldBe TestMarykObject
    }

    @Test
    fun testValidateSize() {
        def.validate(newValue = mapOf(
                12 to "#twelve",
                30 to "#thirty"
        ))
        def.validate(newValue = mapOf(
                12 to "#twelve",
                30 to "#thirty",
                100 to "#hundred",
                1000 to "#thousand"
        ))

        shouldThrow<PropertyTooLittleItemsException> {
            def.validate(newValue = mapOf(
                    1 to "#one"
            ))
        }

        shouldThrow<PropertyTooMuchItemsException> {
            def.validate(newValue = mapOf(
                    12 to "#twelve",
                    30 to "#thirty",
                    100 to "#hundred",
                    1000 to "#thousand",
                    0 to "#zero"
            ))
        }
    }

    @Test
    fun testValidateContent() {
        val e = shouldThrow<PropertyValidationUmbrellaException> {
            def.validate(newValue = mapOf(
                    12 to "#twelve",
                    30 to "WRONG",
                    1001 to "#thousandone",
                    3000 to "#threethousand"
            ))
        }
        e.exceptions.size shouldBe 3

        with(e.exceptions[0]) {
            assertTrue(this is PropertyInvalidValueException)
            this.reference!!.completeName shouldBe "intStringMap[30]"
        }

        with(e.exceptions[1]) {
            assertTrue(this is PropertyOutOfRangeException)
            this.reference!!.completeName shouldBe "intStringMap<1001>"
        }

        with(e.exceptions[2]) {
            assertTrue(this is PropertyOutOfRangeException)
            this.reference!!.completeName shouldBe "intStringMap<3000>"
        }
    }
}