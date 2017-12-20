package maryk.core.query.responses

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.changes.DataObjectChange
import maryk.core.query.changes.PropertyChange
import maryk.core.query.changes.PropertyDelete
import kotlin.test.Test

class ObjectChangesResponseTest {
    private val value = SimpleMarykObject(value = "haha1")

    private val key = SimpleMarykObject.key.getKey(this.value)

    private val objectChangesResponse = ObjectChangesResponse(
            SimpleMarykObject,
            listOf(
                    DataObjectChange(
                            key,
                            PropertyChange(SimpleMarykObject.ref { value }, "hoho"),
                            PropertyDelete(SimpleMarykObject.ref { value }),
                            lastVersion = 14141L.toUInt64()
                    )
            )
    )

    private val context = DataModelPropertyContext(mapOf(
            SimpleMarykObject.name to SimpleMarykObject
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