@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.query.requests

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.test.models.SimpleMarykModel
import maryk.test.requests.scanVersionedChangesMaxRequest
import maryk.test.requests.scanVersionedChangesRequest
import maryk.test.shouldBe
import kotlin.test.Test

class ScanVersionedChangesRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(scanVersionedChangesRequest, ScanVersionedChangesRequest, { this.context })
        checkProtoBufConversion(scanVersionedChangesMaxRequest, ScanVersionedChangesRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(scanVersionedChangesRequest, ScanVersionedChangesRequest, { this.context })
        checkJsonConversion(scanVersionedChangesMaxRequest, ScanVersionedChangesRequest, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(scanVersionedChangesRequest, ScanVersionedChangesRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        filterSoftDeleted: true
        limit: 100
        fromVersion: 1234
        maxVersions: 1000

        """.trimIndent()

        checkYamlConversion(scanVersionedChangesMaxRequest, ScanVersionedChangesRequest, { this.context }) shouldBe """
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
