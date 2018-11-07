package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.collectRequest
import maryk.test.shouldBe
import kotlin.test.Test

class CollectRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(collectRequest, CollectRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(collectRequest, CollectRequest, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(collectRequest, CollectRequest, { this.context }) shouldBe """
        testName: !Get
          dataModel: SimpleMarykModel
          keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
          filterSoftDeleted: true

        """.trimIndent()
    }
}
