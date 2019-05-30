package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.deleteRequest
import kotlin.test.Test
import kotlin.test.expect

class DeleteRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(deleteRequest, DeleteRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(deleteRequest, DeleteRequest, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            from: SimpleMarykModel
            keys: [B4CeT0fDRxYnEmSTQuLA2A, oDHjQh7GSDwyPX2kTUAniQ]
            hardDelete: true

            """.trimIndent()
        ) {
            checkYamlConversion(deleteRequest, DeleteRequest, { this.context })
        }
    }
}
