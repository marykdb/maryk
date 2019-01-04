@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.scanChangesMaxRequest
import maryk.test.requests.scanChangesRequest
import maryk.test.shouldBe
import kotlin.test.Test

class ScanChangesRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(scanChangesRequest, ScanChangesRequest, { this.context })
        checkProtoBufConversion(scanChangesMaxRequest, ScanChangesRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(scanChangesRequest, ScanChangesRequest, { this.context })
        checkJsonConversion(scanChangesMaxRequest, ScanChangesRequest, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(scanChangesRequest, ScanChangesRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        filterSoftDeleted: true
        limit: 100
        fromVersion: 0
        maxVersions: 1

        """.trimIndent()

        checkYamlConversion(scanChangesMaxRequest, ScanChangesRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        select:
        - value
        filter: !Exists value
        order: !Desc value
        toVersion: 2345
        filterSoftDeleted: true
        limit: 300
        fromVersion: 1234
        maxVersions: 10

        """.trimIndent()
    }
}
