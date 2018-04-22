package maryk.core.query.responses

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.types.numeric.toUInt64
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.changes.change
import maryk.core.query.changes.delete
import kotlin.test.Test

class ObjectChangesResponseTest {
    private val value = SimpleMarykObject(value = "haha1")

    private val key = SimpleMarykObject.key(this.value)

    private val objectChangesResponse = ObjectChangesResponse(
        SimpleMarykObject,
        listOf(
            key.change(
                SimpleMarykObject.ref { value }.change("hoho"),
                SimpleMarykObject.ref { value }.delete(),
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
