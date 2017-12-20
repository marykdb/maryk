package maryk.core.query.filters

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class RangeTest {
    private val range = Range(
            reference = SimpleMarykObject.ref { value },
            from = "test",
            to = "test999",
            inclusiveFrom = true,
            inclusiveTo = false
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
            mapOf(
                    SimpleMarykObject.name to SimpleMarykObject
            ),
            dataModel = SimpleMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
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