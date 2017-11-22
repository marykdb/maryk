package maryk.core.query.requests

import maryk.SubMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.query.properties.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class AddRequestTest {
    private val addRequest = AddRequest(
            SubMarykObject,
            SubMarykObject(value = "haha1"),
            SubMarykObject(value = "haha2")
    )

    @Test
    fun testAddObject() {
        this.addRequest.dataModel shouldBe SubMarykObject
        this.addRequest.objectsToAdd.size shouldBe 2
    }

    private val context = DataModelPropertyContext(mapOf(
            SubMarykObject.name to SubMarykObject
    ))

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.addRequest, AddRequest, this.context) {
            it.objectsToAdd.contentDeepEquals(this.addRequest.objectsToAdd) shouldBe true
            it.dataModel shouldBe this.addRequest.dataModel
        }
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.addRequest, AddRequest, this.context) {
            it.objectsToAdd.contentDeepEquals(this.addRequest.objectsToAdd) shouldBe true
            it.dataModel shouldBe this.addRequest.dataModel
        }
    }
}