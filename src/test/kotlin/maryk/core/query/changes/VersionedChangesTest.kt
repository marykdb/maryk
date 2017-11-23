package maryk.core.query.changes

import maryk.SubMarykObject
import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.types.toUInt64
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class VersionedChangesTest {
    private val subModel = { TestMarykObject.Properties.subModel.getRef() }

    private val versionedChanges = VersionedChanges(
            219674127L.toUInt64(),
            listOf(
                    PropertyChange(SubMarykObject.Properties.value.getRef(subModel), "new"),
                    PropertyDelete(SubMarykObject.Properties.value.getRef(subModel)),
                    PropertyCheck(SubMarykObject.Properties.value.getRef(subModel)),
                    ObjectSoftDeleteChange(true),
                    ListPropertyChange(TestMarykObject.Properties.list.getRef()),
                    SetPropertyChange(TestMarykObject.Properties.set.getRef()),
                    MapPropertyChange(TestMarykObject.Properties.map.getRef())
            )
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
            dataModels = mapOf(
                SubMarykObject.name to SubMarykObject,
                TestMarykObject.name to TestMarykObject
            ),
            dataModel = TestMarykObject as RootDataModel<Any>
    )

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.versionedChanges, VersionedChanges, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.versionedChanges, VersionedChanges, this.context)
    }
}