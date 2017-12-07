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

class DataObjectVersionedChangeTest {
    private val key1 = TestMarykObject.key.get(
            byteArrayOf(0, 0, 2, 43, 1, 1, 1, 0, 2)
    )

    private val subModel = TestMarykObject.Properties.subModel.getRef()

    private val dataObjectVersionedChanges = DataObjectVersionedChange(
            key = key1,
            changes = listOf(
                    VersionedChanges(
                            219674127L.toUInt64(),
                            listOf(
                                    ObjectSoftDeleteChange(true),
                                    ListPropertyChange(TestMarykObject.Properties.list.getRef()),
                                    SetPropertyChange(TestMarykObject.Properties.set.getRef()),
                                    MapPropertyChange(TestMarykObject.Properties.map.getRef())
                            )
                    ),
                    VersionedChanges(
                            319674127L.toUInt64(),
                            listOf(
                                    PropertyChange(SubMarykObject.Properties.value.getRef(subModel), "new"),
                                    PropertyDelete(SubMarykObject.Properties.value.getRef(subModel)),
                                    PropertyCheck(SubMarykObject.Properties.value.getRef(subModel))
                            )
                    )
            )
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
            dataModels = mapOf(
                    SubMarykObject.name to SubMarykObject,
                    TestMarykObject.name to TestMarykObject
            ),
            dataModel = TestMarykObject as RootDataModel<Any, PropertyDefinitions<Any>>
    )

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.dataObjectVersionedChanges, DataObjectVersionedChange, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.dataObjectVersionedChanges, DataObjectVersionedChange, this.context)
    }
}