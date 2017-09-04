package maryk.core.properties.references

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import maryk.TestMarykObject
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.SubModelDefinition
import org.junit.Test

internal class PropertyReferenceTest {
    val modelDefinition = SubModelDefinition(
            name = "subModel",
            dataModel = TestMarykObject
    )

    val definition = StringDefinition(
            name = "test"
    )

    @Test
    fun getCompleteName() {
        definition.getRef().completeName shouldBe "test"

        definition.getRef({ modelDefinition.getRef() }).completeName shouldBe "subModel.test"
    }

    @Test
    fun testHashCode() {
        definition.getRef().hashCode() shouldBe 3556498
    }

    @Test
    fun testCompareTo() {
        definition.getRef() shouldBe definition.getRef()
        definition.getRef() shouldNotBe modelDefinition.getRef()
    }
}