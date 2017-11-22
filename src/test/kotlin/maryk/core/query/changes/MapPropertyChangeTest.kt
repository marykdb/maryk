package maryk.core.query.changes

import maryk.SubMarykObject
import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.types.Time
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class MapPropertyChangeTest {
    private val mapPropertyChange = MapPropertyChange(
            reference = TestMarykObject.Properties.map.getRef(),
            keysToDelete = setOf(
                    Time(12, 33, 12)
            ),
            valuesToAdd = mapOf(
                    Time(23, 0, 0) to "Test4",
                    Time(5, 51, 53) to "Test5",
                    Time(11, 10, 9) to "Test6"
            ),
            valueToCompare = mapOf(
                    Time(22, 22, 22) to "Test1",
                    Time(1, 1, 5) to "Test2",
                    Time(12, 33, 12) to "Test3"
            )
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
            mapOf(
                    TestMarykObject.name to SubMarykObject
            ),
            dataModel = TestMarykObject as RootDataModel<Any>
    )

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.mapPropertyChange, MapPropertyChange, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.mapPropertyChange, MapPropertyChange, this.context)
    }
}