package maryk.core.query.requests

import maryk.SimpleMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.DataModelPropertyContext
import maryk.test.shouldBe
import kotlin.test.Test

class DeleteRequestTest {
    private val key1 = SimpleMarykObject.key("B4CeT0fDRxYnEmSTQuLA2A")
    private val key2 = SimpleMarykObject.key("oDHjQh7GSDwyPX2kTUAniQ")

    private val deleteRequest = SimpleMarykObject.delete(
        key1,
        key2,
        hardDelete = true
    )

    private val context = DataModelPropertyContext(mapOf(
        SimpleMarykObject.name to SimpleMarykObject
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.deleteRequest, DeleteRequest, this.context)
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.deleteRequest, DeleteRequest, this.context)
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.deleteRequest, DeleteRequest, this.context) shouldBe """
        dataModel: SimpleMarykObject
        objectsToDelete: [B4CeT0fDRxYnEmSTQuLA2A, oDHjQh7GSDwyPX2kTUAniQ]
        hardDelete: true

        """.trimIndent()
    }
}
