package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.addRequest
import maryk.test.shouldBe
import kotlin.test.Test

class AddRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(addRequest, AddRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(addRequest, AddRequest, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(addRequest, AddRequest, { this.context }) shouldBe """
        to: SimpleMarykModel
        objects:
        - value: haha1
        - value: haha2

        """.trimIndent()
    }
}
