package maryk.core.query.changes

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class ChangeTest {
    private val valueChange = Change(
        SimpleMarykObject.ref { value }, "test"
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            SimpleMarykObject.name to SimpleMarykObject
        ),
        dataModel = SimpleMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
    )

    @Test
    fun testValueChange() {
        valueChange.reference shouldBe SimpleMarykObject.ref { value }
        valueChange.value shouldBe "test"
    }

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.valueChange, Change, this.context)
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.valueChange, Change, this.context)
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.valueChange, Change, this.context) shouldBe """
        reference: value
        value: test

        """.trimIndent()
    }
}
