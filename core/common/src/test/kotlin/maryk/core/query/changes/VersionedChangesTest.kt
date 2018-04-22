package maryk.core.query.changes

import maryk.SubMarykObject
import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import kotlin.test.Test

class VersionedChangesTest {
    private val subModelValue = TestMarykObject { subModel ref { value } }

    private val versionedChanges = VersionedChanges(
        219674127L.toUInt64(),
        listOf(
            subModelValue.change("new"),
            subModelValue.delete(),
            subModelValue.check(),
            ObjectSoftDeleteChange(true),
            TestMarykObject.ref { list }.change(),
            TestMarykObject.ref { set }.change(),
            TestMarykObject.ref { map }.change()
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
        checkProtoBufConversion(this.versionedChanges, VersionedChanges, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.versionedChanges, VersionedChanges, this.context)
    }
}
