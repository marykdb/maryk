package maryk.core.query.properties.changes

import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.query.properties.DataModelPropertyContext
import kotlin.test.Test

class ListPropertyChangeTest {
    private val listPropertyChange = ListPropertyChange(
            reference = TestMarykObject.Properties.listOfString.getRef(),
            addValuesAtIndex = mapOf(2 to "a", 3 to "abc"),
            addValuesToEnd = listOf("four", "five"),
            deleteAtIndex = listOf(0, 1),
            deleteValues = listOf("three"),
            valueToCompare = listOf("one", "two", "three")
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
            mapOf(
                    TestMarykObject.name to TestMarykObject
            ),
            dataModel = TestMarykObject as RootDataModel<Any>
    )

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.listPropertyChange, ListPropertyChange, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.listPropertyChange, ListPropertyChange, this.context)
    }
}