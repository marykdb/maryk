package maryk.core.query.changes

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class PropertyCheckTest {
    private val valueCheck = PropertyCheck(
            reference = SubMarykObject.Properties.value.getRef(),
            valueToCompare = "test"
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
            mapOf(
                    SubMarykObject.name to SubMarykObject
            ),
            dataModel = SubMarykObject as RootDataModel<Any>
    )

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.valueCheck, PropertyCheck, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.valueCheck, PropertyCheck, this.context)
    }
}