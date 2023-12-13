package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.collectRequest
import kotlin.test.Test
import kotlin.test.expect

class CollectRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.Meta.name toUnitLambda { SimpleMarykModel }
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
        expect(
            """
            testName: !Get
              from: SimpleMarykModel
              keys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX_mQHYCSEoLtfLSUQ]
              filterSoftDeleted: true

            """.trimIndent()
        ) {
            checkYamlConversion(collectRequest, CollectRequest, { this.context })
        }
    }
}
