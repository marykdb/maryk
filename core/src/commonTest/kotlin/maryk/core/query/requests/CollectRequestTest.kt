package maryk.core.query.requests

import maryk.SimpleMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.RequestContext
import maryk.test.shouldBe
import kotlin.test.Test

val collectRequest = CollectRequest(
    "testName",
    SimpleMarykModel.get(
        SimpleMarykModel.key("dR9gVdRcSPw2molM1AiOng"),
        SimpleMarykModel.key("Vc4WgX/mQHYCSEoLtfLSUQ")
    )
)

class CollectRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.name to { SimpleMarykModel }
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
