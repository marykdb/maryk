package maryk.core.query.requests

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.changes.DataObjectChange
import maryk.test.shouldBe
import kotlin.test.Test

class ChangeRequestTest {
    private val key1 = SimpleMarykObject.key.getKey(SimpleMarykObject("test1"))
    private val key2 = SimpleMarykObject.key.getKey(SimpleMarykObject("test2"))

    private val changeRequest = ChangeRequest(
        SimpleMarykObject,
        DataObjectChange(key1),
        DataObjectChange(key2)
    )

    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to SimpleMarykObject
    ))

    @Test
    fun testChangeRequest(){
        changeRequest.objectChanges.size shouldBe 2
        changeRequest.dataModel shouldBe SimpleMarykObject
    }

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.changeRequest, ChangeRequest, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.changeRequest, ChangeRequest, this.context)
    }
}
