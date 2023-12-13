package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.scanUpdatesMaxRequest
import maryk.test.requests.scanUpdatesRequest
import kotlin.test.Test
import kotlin.test.expect

class ScanUpdatesRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.Meta.name toUnitLambda { SimpleMarykModel }
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(scanUpdatesRequest, ScanUpdatesRequest, { this.context })
        checkProtoBufConversion(scanUpdatesMaxRequest, ScanUpdatesRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(scanUpdatesRequest, ScanUpdatesRequest, { this.context })
        checkJsonConversion(scanUpdatesMaxRequest, ScanUpdatesRequest, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            from: SimpleMarykModel
            filterSoftDeleted: true
            limit: 100
            includeStart: true
            fromVersion: 0
            maxVersions: 1

            """.trimIndent()
        ) {
            checkYamlConversion(scanUpdatesRequest, ScanUpdatesRequest, { this.context })
        }

        expect(
            """
            from: SimpleMarykModel
            startKey: Zk6m4QpZQegUg5s13JVYlQ
            select:
            - value
            where: !Exists value
            toVersion: 2345
            filterSoftDeleted: true
            order: !Desc value
            limit: 300
            includeStart: false
            fromVersion: 1234
            maxVersions: 10
            orderedKeys: [dR9gVdRcSPw2molM1AiOng, Vc4WgX_mQHYCSEoLtfLSUQ]

            """.trimIndent()
        ) {
            checkYamlConversion(scanUpdatesMaxRequest, ScanUpdatesRequest, { this.context })
        }
    }
}
