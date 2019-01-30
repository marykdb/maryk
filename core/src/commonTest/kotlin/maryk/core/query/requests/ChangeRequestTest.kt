package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.changeRequest
import maryk.test.shouldBe
import kotlin.test.Test

class ChangeRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
    ))

    @Test
    fun testChangeRequest(){
        changeRequest.objects.size shouldBe 2
        changeRequest.dataModel shouldBe SimpleMarykModel
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
        checkYamlConversion(changeRequest, ChangeRequest, { this.context }) shouldBe """
        to: SimpleMarykModel
        objects:
        - key: MYc6LBYcT38nWxoE1ahNxA
          changes:
        - key: lneV6ioyQL0vnbkLqwVw+A
          changes:

        """.trimIndent()
    }
}
