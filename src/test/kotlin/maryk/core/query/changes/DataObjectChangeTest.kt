package maryk.core.query.changes

import maryk.SubMarykObject
import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.toUInt64
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class DataObjectChangeTest {
    private val key1 = TestMarykObject.key.get(
            byteArrayOf(0, 0, 2, 43, 1, 1, 1, 0, 2)
    )

    private val subModel = { TestMarykObject.Properties.subModel.getRef() }

    private val dataObjectChange = DataObjectChange(
            key1,
            PropertyChange(SubMarykObject.Properties.value.getRef(subModel), "new"),
            PropertyDelete(SubMarykObject.Properties.value.getRef(subModel)),
            PropertyCheck(SubMarykObject.Properties.value.getRef(subModel)),
            ObjectSoftDeleteChange(true),
            ListPropertyChange(TestMarykObject.Properties.list.getRef()),
            SetPropertyChange(TestMarykObject.Properties.set.getRef()),
            MapPropertyChange(TestMarykObject.Properties.map.getRef()),
            lastVersion = 12345L.toUInt64()
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
        checkProtoBufConversion(this.dataObjectChange, DataObjectChange, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.dataObjectChange, DataObjectChange, this.context)
    }
}