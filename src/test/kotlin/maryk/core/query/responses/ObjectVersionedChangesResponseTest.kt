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
import maryk.core.query.changes.ListPropertyChange
import maryk.core.query.changes.MapPropertyChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.PropertyChange
import maryk.core.query.changes.PropertyCheck
import maryk.core.query.changes.PropertyDelete
import maryk.core.query.changes.SetPropertyChange
import maryk.core.query.changes.VersionedChanges
import kotlin.test.Test

class ObjectVersionedChangesResponseTest {
    private val key = TestMarykObject.key.get(
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
                            ListPropertyChange(TestMarykObject.ref { list }),
                            SetPropertyChange(TestMarykObject.ref { set }),
                            MapPropertyChange(TestMarykObject.ref { map })
                        )
                    ),
                    VersionedChanges(
                        319674127L.toUInt64(),
                        listOf(
                            PropertyChange(SubMarykObject.ref(subModel) { value }, "new"),
                            PropertyDelete(SubMarykObject.ref(subModel) { value }),
                            PropertyCheck(SubMarykObject.ref(subModel) { value })
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