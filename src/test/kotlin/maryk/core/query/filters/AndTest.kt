package maryk.core.query.filters

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class AndTest {
    private val and = And(
            Exists(SubMarykObject.Properties.value.getRef()),
            Equals(SubMarykObject.Properties.value.getRef(), "hoi")
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
        checkProtoBufConversion(this.and, And, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.and, And, this.context)
    }
}