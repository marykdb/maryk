package maryk.core.query.responses

import maryk.SubMarykObject
import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.changes.check
import maryk.core.query.changes.delete
import kotlin.test.Test

class ObjectVersionedChangesResponseTest {
    private val key = TestMarykObject.key(
        byteArrayOf(0, 0, 2, 43, 1, 1, 1, 0, 2)
    )

    private val subModel = TestMarykObject.ref { subModel }

    private val objectVersionedChangesResponse = ObjectVersionedChangesResponse(
        TestMarykObject,
        listOf(
            DataObjectVersionedChange(
                key = key,
                changes = listOf(
                    VersionedChanges(
                        219674127L.toUInt64(),
                        listOf(
                            ObjectSoftDeleteChange(true),
                            TestMarykObject.ref { list }.change(),
                            TestMarykObject.ref { set }.change(),
                            TestMarykObject.ref { map }.change()
                        )
                    ),
                    VersionedChanges(
                        319674127L.toUInt64(),
                        listOf(
                            SubMarykObject.ref(subModel) { value }.change("new"),
                            SubMarykObject.ref(subModel) { value }.delete(),
                            SubMarykObject.ref(subModel) { value }.check("current")
                        )
                    )
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
        checkProtoBufConversion(this.objectVersionedChangesResponse, ObjectVersionedChangesResponse, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.objectVersionedChangesResponse, ObjectVersionedChangesResponse, this.context)
    }
}
