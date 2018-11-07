package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.deleteRequest
import maryk.test.shouldBe
import kotlin.test.Test

class DeleteRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
    ))

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(deleteRequest, DeleteRequest, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(deleteRequest, DeleteRequest, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(deleteRequest, DeleteRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        objectsToDelete: [B4CeT0fDRxYnEmSTQuLA2A, oDHjQh7GSDwyPX2kTUAniQ]
        hardDelete: true

        """.trimIndent()
    }
}
