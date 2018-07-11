package maryk.core.query.changes

import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.pairs.with
import maryk.test.shouldBe
import kotlin.test.Test

class ChangeTest {
    private val valueChange = Change(
        TestMarykObject.ref { string } with "test",
        TestMarykObject.ref { int } with 5
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykObject.name to { TestMarykObject }
        ),
        dataModel = TestMarykObject as RootObjectDataModel<Any, ObjectPropertyDefinitions<Any>>
    )

    @Test
    fun testValueChange() {
        valueChange.referenceValuePairs[0].reference shouldBe TestMarykObject.ref { string }
        valueChange.referenceValuePairs[0].value shouldBe "test"
    }

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.valueChange, Change, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.valueChange, Change, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.valueChange, Change, { this.context }) shouldBe """
        string: test
        int: 5

        """.trimIndent()
    }
}
