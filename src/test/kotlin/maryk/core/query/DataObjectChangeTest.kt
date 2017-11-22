package maryk.core.query

import maryk.SubMarykObject
import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.types.toUInt64
import maryk.core.query.changes.DataObjectChange
import maryk.core.query.changes.ListPropertyChange
import maryk.core.query.changes.MapPropertyChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.PropertyChange
import maryk.core.query.changes.PropertyCheck
import maryk.core.query.changes.PropertyDelete
import maryk.core.query.changes.SetPropertyChange
import maryk.test.shouldBe
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
            dataModel = TestMarykObject as RootDataModel<Any>
    )

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.dataObjectChange, DataObjectChange, this.context, ::compareRequest)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.dataObjectChange, DataObjectChange, this.context, ::compareRequest)
    }

    private fun compareRequest(converted: DataObjectChange<*>, original: DataObjectChange<*>) {
        converted.key shouldBe original.key
        converted.changes.contentDeepEquals(original.changes) shouldBe true
        converted.lastVersion shouldBe original.lastVersion
    }
}