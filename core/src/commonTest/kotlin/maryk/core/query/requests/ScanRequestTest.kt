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
import maryk.test.shouldBe
import kotlin.test.Test

class ScanSelectRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(scanRequest, ScanRequest, { this.context })
        checkProtoBufConversion(scanMaxRequest, ScanRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(scanRequest, ScanRequest, { this.context })
        checkJsonConversion(scanMaxRequest, ScanRequest, { this.context })
        checkJsonConversion(scanOrdersRequest, ScanRequest, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(scanRequest, ScanRequest, { this.context }) shouldBe """
        from: SimpleMarykModel
        filterSoftDeleted: true
        limit: 100

        """.trimIndent()

        checkYamlConversion(scanMaxRequest, ScanRequest, { this.context }) shouldBe """
        from: SimpleMarykModel
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        select:
        - value
        where: !Exists value
        toVersion: 2345
        filterSoftDeleted: true
        order: value
        limit: 200

        """.trimIndent()

        checkYamlConversion(scanOrdersRequest, ScanRequest, { this.context }) shouldBe """
        from: SimpleMarykModel
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        select:
        - value
        filterSoftDeleted: true
        order:
        - value
        - !Desc value
        limit: 100

        """.trimIndent()
    }
}
