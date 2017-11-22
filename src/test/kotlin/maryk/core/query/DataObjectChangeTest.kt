package maryk.core.query

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.types.toUInt64
import maryk.core.query.changes.DataObjectChange
import maryk.core.query.changes.PropertyChange
import maryk.core.query.changes.PropertyDelete
import maryk.test.shouldBe
import kotlin.test.Test

class DataObjectChangeTest {
    private val key1 = SubMarykObject.key.getKey(SubMarykObject("test1"))

    private val dataObjectChange = DataObjectChange(
            key1,
            PropertyChange(SubMarykObject.Properties.value.getRef(), "new"),
            PropertyDelete(SubMarykObject.Properties.value.getRef()),
            lastVersion = 12345L.toUInt64()
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
        checkProtoBufConversion(this.dataObjectChange, DataObjectChange, this.context, ::compareRequest)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.dataObjectChange, DataObjectChange, this.context, ::compareRequest)
    }

    private fun compareRequest(converted: DataObjectChange<*>) {
        converted.key shouldBe this.dataObjectChange.key
        converted.changes.contentDeepEquals(this.dataObjectChange.changes) shouldBe true
        converted.lastVersion shouldBe this.dataObjectChange.lastVersion
    }
}