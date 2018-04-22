package maryk.core.query.filters

import maryk.SimpleMarykObject
import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class RangeTest {
    private val range = TestMarykObject.ref { int } inRange 2..6

    private val range2 = SimpleMarykObject.ref { value }.inRange(
        from = "test",
        to = "test999",
        inclusiveFrom = true,
        inclusiveTo = false
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykObject.name to TestMarykObject
        ),
        dataModel = TestMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
    )

    @Suppress("UNCHECKED_CAST")
    private val context2 = DataModelPropertyContext(
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

    @Test
    fun testProtoBufConversion2() {
        checkProtoBufConversion(this.range2, Range, this.context2)
    }

    @Test
    fun testJsonConversion2() {
        checkJsonConversion(this.range2, Range, this.context2)
    }
}
