package maryk.core.query.filters

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class ValueInTest {
    private val valueIn = ValueIn(
            reference = SubMarykObject.Properties.value.getRef(),
            values = setOf("t1", "t2", "t3")
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
            mapOf(
                    SubMarykObject.name to SubMarykObject
            ),
            dataModel = SubMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
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