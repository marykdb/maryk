package maryk.core.query.changes

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class PropertyDeleteTest {
    private val propertyDelete = SimpleMarykObject.ref { value }.delete(valueToCompare = "test")

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            SimpleMarykObject.name to SimpleMarykObject
        ),
        dataModel = SimpleMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
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
