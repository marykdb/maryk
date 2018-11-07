package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.scanMaxRequest
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
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(scanRequest, ScanRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        filterSoftDeleted: true
        limit: 100

        """.trimIndent()

        checkYamlConversion(scanMaxRequest, ScanRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        select:
        - value
        filter: !Exists value
        order: value
        toVersion: 2345
        filterSoftDeleted: true
        limit: 200

        """.trimIndent()
    }
}
