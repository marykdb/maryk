package maryk.core.query.requests

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.changes.change
import maryk.test.shouldBe
import kotlin.test.Test

class ChangeRequestTest {
    private val key1 = SimpleMarykObject.key(SimpleMarykObject("test1"))
    private val key2 = SimpleMarykObject.key(SimpleMarykObject("test2"))

    private val changeRequest = SimpleMarykObject.change(
        key1.change(),
        key2.change()
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
