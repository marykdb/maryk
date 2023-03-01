package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.scanMaxRequest
import maryk.test.requests.scanOrdersRequest
import maryk.test.requests.scanRequest
import kotlin.test.Test
import kotlin.test.expect

class ScanSelectRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.Model.name toUnitLambda { SimpleMarykModel.Model }
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(scanRequest, ScanRequest.Model, { this.context })
        checkProtoBufConversion(scanMaxRequest, ScanRequest.Model, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(scanRequest, ScanRequest.Model, { this.context })
        checkJsonConversion(scanMaxRequest, ScanRequest.Model, { this.context })
        checkJsonConversion(scanOrdersRequest, ScanRequest.Model, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            from: SimpleMarykModel
            filterSoftDeleted: true
            limit: 100
            includeStart: true

            """.trimIndent()
        ) {
            checkYamlConversion(scanRequest, ScanRequest.Model, { this.context })
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
            aggregations:
              totalValues: !ValueCount
                of: value
            order: value
            limit: 200
            includeStart: false

            """.trimIndent()
        ) {
            checkYamlConversion(scanMaxRequest, ScanRequest.Model, { this.context })
        }

        expect(
            """
            from: SimpleMarykModel
            startKey: Zk6m4QpZQegUg5s13JVYlQ
            select:
            - value
            filterSoftDeleted: true
            order:
            - value
            - !Desc value
            limit: 100
            includeStart: true

            """.trimIndent()
        ) {
            checkYamlConversion(scanOrdersRequest, ScanRequest.Model, { this.context })
        }
    }
}
