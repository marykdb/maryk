package maryk.core.query.filters

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class PrefixTest {
    private val prefix = Prefix(
            reference = SubMarykObject.Properties.value.getRef(),
            prefix = "te"
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
        checkProtoBufConversion(this.prefix, Prefix, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.prefix, Prefix, this.context)
    }
}