package maryk.core.query.filters

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class ExistsTest {
    private val exists = SimpleMarykObject.ref { value }.exists()

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            SimpleMarykObject.name to SimpleMarykObject
        ),
        dataModel = SimpleMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
    )

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.exists, Exists, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.exists, Exists, this.context)
    }
}
