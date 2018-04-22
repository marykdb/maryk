package maryk.core.query.filters

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class AndTest {
    private val and = And(
        SimpleMarykObject.ref{ value }.exists(),
        SimpleMarykObject.ref{ value } equals "hoi"
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
        checkProtoBufConversion(this.and, And, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.and, And, this.context)
    }
}
