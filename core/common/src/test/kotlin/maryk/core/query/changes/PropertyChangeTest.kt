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

class PropertyChangeTest {
    private val valueChange = SimpleMarykObject.ref { value }.change(
        newValue = "test",
        valueToCompare = "old"
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
        valueChange.newValue shouldBe "test"
    }

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.valueChange, PropertyChange, this.context)
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.valueChange, PropertyChange, this.context)
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.valueChange, PropertyChange, this.context) shouldBe """
        |reference: value
        |valueToCompare: old
        |newValue: test
        |""".trimMargin()
    }
}
