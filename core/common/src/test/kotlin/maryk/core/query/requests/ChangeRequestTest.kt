package maryk.core.query.requests

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.changes.change
import maryk.test.shouldBe
import kotlin.test.Test

class ChangeRequestTest {
    private val key1 = SimpleMarykObject.key("MYc6LBYcT38nWxoE1ahNxA")
    private val key2 = SimpleMarykObject.key("lneV6ioyQL0vnbkLqwVw+A")

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
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.changeRequest, ChangeRequest, this.context)
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.changeRequest, ChangeRequest, this.context)
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.changeRequest, ChangeRequest, this.context) shouldBe """
        dataModel: SimpleMarykObject
        objectChanges:
        - key: MYc6LBYcT38nWxoE1ahNxA
          changes:
        - key: lneV6ioyQL0vnbkLqwVw+A
          changes:

        """.trimIndent()
    }
}
