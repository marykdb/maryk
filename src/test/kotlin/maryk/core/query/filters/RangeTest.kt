package maryk.core.query.filters

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class RangeTest {
    private val range = Range(
            reference = SubMarykObject.Properties.value.getRef(),
            from = "test",
            to = "test999",
            inclusiveFrom = true,
            inclusiveTo = false
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
        checkProtoBufConversion(this.range, Range, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.range, Range, this.context)
    }
}