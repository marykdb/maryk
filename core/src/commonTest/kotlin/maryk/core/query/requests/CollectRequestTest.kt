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
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(collectRequest, CollectRequest, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(collectRequest, CollectRequest, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(collectRequest, CollectRequest, { this.context }) shouldBe """
        testName: !Get
          dataModel: SimpleMarykModel
          keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX/mQHYCSEoLtfLSUQ]
          filterSoftDeleted: true

        """.trimIndent()
    }
}
