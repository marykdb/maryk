package maryk.core.properties.definitions

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import maryk.core.objects.DataModel
import maryk.core.objects.Def
import maryk.core.properties.exceptions.PropertyValidationUmbrellaException
import org.junit.Test

internal class SubModelDefinitionTest {
    private data class MarykObject(
            val string: String = "jur"
    ){
        object Properties {
            val string = StringDefinition(
                    name = "string",
                    index = 0,
                    regEx = "jur"
            )
        }
        companion object: DataModel<MarykObject>(definitions = listOf(
                Def(Properties.string, MarykObject::string)
        ))
    }

    private val def = SubModelDefinition(
            name = "test",
            dataModel = MarykObject
    )

    @Test
    fun hasValues() {
        def.dataModel shouldBe MarykObject
    }

    @Test
    fun testReference() {
        def.getRef().dataModel shouldBe MarykObject
    }

    @Test
    fun validate() {
        def.validate(newValue = MarykObject())
        shouldThrow<PropertyValidationUmbrellaException> {
            def.validate(newValue = MarykObject("wrong"))
        }
    }
}