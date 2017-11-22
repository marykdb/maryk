package maryk.core.query.changes

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class PropertyValueChangeTest {
    private val valueChange = PropertyValueChange(
            reference = SubMarykObject.Properties.value.getRef(),
            newValue = "test",
            valueToCompare = "old"
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
            mapOf(
                    SubMarykObject.name to SubMarykObject
            ),
            dataModel = SubMarykObject as RootDataModel<Any>
    )

    @Test
    fun testValueChange() {
        valueChange.reference shouldBe SubMarykObject.Properties.value.getRef()
        valueChange.newValue shouldBe "test"
    }

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.valueChange, PropertyValueChange, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.valueChange, PropertyValueChange, this.context)
    }
}