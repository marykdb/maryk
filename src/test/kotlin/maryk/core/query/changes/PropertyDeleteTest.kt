package maryk.core.query.changes

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class PropertyDeleteTest {
    private val propertyDelete = PropertyDelete(
            reference = SubMarykObject.ref { value },
            valueToCompare = "test"
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
        checkProtoBufConversion(this.propertyDelete, PropertyDelete, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.propertyDelete, PropertyDelete, this.context)
    }
}