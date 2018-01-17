package maryk.core.query.filters

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class ValueInTest {
    private val valueIn = ValueIn(
            reference = SimpleMarykObject.ref { value },
            values = setOf("t1", "t2", "t3")
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
        checkProtoBufConversion(this.valueIn, ValueIn, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.valueIn, ValueIn, this.context)
    }
}