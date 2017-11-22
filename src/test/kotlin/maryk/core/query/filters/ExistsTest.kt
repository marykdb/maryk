package maryk.core.query.filters

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.query.DataModelPropertyContext
import org.junit.Test

class ExistsTest {
    private val exists = Exists(
            reference = SubMarykObject.Properties.value.getRef()
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
        checkProtoBufConversion(this.exists, Exists, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.exists, Exists, this.context)
    }
}