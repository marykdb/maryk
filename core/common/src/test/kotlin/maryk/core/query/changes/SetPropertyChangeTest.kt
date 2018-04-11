package maryk.core.query.changes

import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.lib.time.Date
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class SetPropertyChangeTest {
    private val setPropertyChange = SetPropertyChange(
        reference = TestMarykObject.ref { set },
        addValues = setOf(
            Date(2014, 4, 14),
            Date(2013, 3, 13)
        ),
        deleteValues = setOf(Date(2018, 7, 17)),
        valueToCompare = setOf(
            Date(2016, 5, 15),
            Date(2017, 6, 16),
            Date(2018, 7, 17)
        )
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykObject.name to TestMarykObject
        ),
        dataModel = TestMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
    )

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.setPropertyChange, SetPropertyChange, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.setPropertyChange, SetPropertyChange, this.context)
    }
}
