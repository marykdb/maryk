package maryk.core.query.filters

import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.with
import maryk.test.shouldBe
import kotlin.test.Test

class EqualsTest {
    private val equals = Equals(
        TestMarykObject.ref { string } with "test",
        TestMarykObject.ref { int } with 5
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykObject.name to TestMarykObject
        ),
        dataModel = TestMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.equals, Equals, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.equals, Equals, { this.context }) shouldBe """
        {
        	"string": "test",
        	"int": 5
        }
        """.trimIndent()
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.equals, Equals, { this.context }) shouldBe """
        string: test
        int: 5

        """.trimIndent()
    }
}
