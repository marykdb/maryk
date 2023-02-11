package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.changeRequest
import kotlin.test.Test
import kotlin.test.expect

class ChangeRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.Model.name toUnitLambda { SimpleMarykModel.Model }
    ))

    @Test
    fun testChangeRequest() {
        expect(2) { changeRequest.objects.size }
        expect(SimpleMarykModel.Model) { changeRequest.dataModel }
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(changeRequest, ChangeRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(changeRequest, ChangeRequest, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            to: SimpleMarykModel
            objects:
            - key: MYc6LBYcT38nWxoE1ahNxA
              changes: []
            - key: lneV6ioyQL0vnbkLqwVw+A
              changes: []

            """.trimIndent()
        ) {
            checkYamlConversion(changeRequest, ChangeRequest, { this.context })
        }
    }
}
