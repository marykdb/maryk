package maryk.core.query.responses

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.changes.DataObjectChange
import maryk.core.query.changes.PropertyChange
import maryk.core.query.changes.PropertyDelete
import kotlin.test.Test

class ObjectChangesResponseTest {
    private val value = SubMarykObject(value = "haha1")

    private val key = SubMarykObject.key.getKey(this.value)

    private val objectChangesResponse = ObjectChangesResponse(
            SubMarykObject,
            listOf(
                    DataObjectChange(
                            key,
                            PropertyChange(SubMarykObject.Properties.value.getRef(), "hoho"),
                            PropertyDelete(SubMarykObject.Properties.value.getRef()),
                            lastVersion = 14141L.toUInt64()
                    )
            )
    )

    private val context = DataModelPropertyContext(mapOf(
            SubMarykObject.name to SubMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.objectChangesResponse, ObjectChangesResponse, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.objectChangesResponse, ObjectChangesResponse, this.context)
    }
}