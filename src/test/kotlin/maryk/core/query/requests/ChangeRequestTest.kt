package maryk.core.query.requests

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.query.changes.DataObjectChange
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class ChangeRequestTest {
    private val key1 = SubMarykObject.key.getKey(SubMarykObject("test1"))
    private val key2 = SubMarykObject.key.getKey(SubMarykObject("test2"))

    private val changeRequest = ChangeRequest(
            SubMarykObject,
            DataObjectChange(key1),
            DataObjectChange(key2)
    )

    private val context = DataModelPropertyContext(mapOf(
            SubMarykObject.name to SubMarykObject
    ))

    @Test
    fun testChangeRequest(){
        changeRequest.objectChanges.size shouldBe 2
        changeRequest.dataModel shouldBe SubMarykObject
    }

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.changeRequest, ChangeRequest, this.context, ::compareRequest)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.changeRequest, ChangeRequest, this.context, ::compareRequest)
    }

    private fun compareRequest(converted: ChangeRequest<*, *>) {
        converted.dataModel shouldBe this.changeRequest.dataModel
    }
}