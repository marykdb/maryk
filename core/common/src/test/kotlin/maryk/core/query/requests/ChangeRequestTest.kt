package maryk.core.query.requests

import maryk.SimpleMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.changes.change
import maryk.test.shouldBe
import kotlin.test.Test

private val key1 = SimpleMarykModel.key("MYc6LBYcT38nWxoE1ahNxA")
private val key2 = SimpleMarykModel.key("lneV6ioyQL0vnbkLqwVw+A")

internal val changeRequest = SimpleMarykModel.change(
    key1.change(),
    key2.change()
)

class ChangeRequestTest {
    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykModel.name to { SimpleMarykModel }
    ))

    @Test
    fun testChangeRequest(){
        changeRequest.objectChanges.size shouldBe 2
        changeRequest.dataModel shouldBe SimpleMarykModel
    }

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(changeRequest, ChangeRequest, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(changeRequest, ChangeRequest, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(changeRequest, ChangeRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        objectChanges:
        - key: MYc6LBYcT38nWxoE1ahNxA
          changes:
        - key: lneV6ioyQL0vnbkLqwVw+A
          changes:

        """.trimIndent()
    }
}
