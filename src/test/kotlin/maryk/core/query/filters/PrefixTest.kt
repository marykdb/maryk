package maryk.core.query.filters

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class PrefixTest {
    private val prefix = Prefix(
            reference = SubMarykObject.ref { value },
            prefix = "te"
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
        checkProtoBufConversion(this.prefix, Prefix, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.prefix, Prefix, this.context)
    }
}