package maryk.core.query.filters

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class RegExTest {
    private val regEx = RegEx(
            reference = SubMarykObject.Properties.value.getRef(),
            regEx = ".*"
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
        checkProtoBufConversion(this.regEx, RegEx, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.regEx, RegEx, this.context)
    }
}