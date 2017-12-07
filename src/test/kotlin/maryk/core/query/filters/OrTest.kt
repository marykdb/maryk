package maryk.core.query.filters

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class OrTest {
    private val or = Or(
            Exists(SubMarykObject.ref { value }),
            Equals(SubMarykObject.ref { value }, "hoi")
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
        checkProtoBufConversion(this.or, Or, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.or, Or, this.context)
    }
}