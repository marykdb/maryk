@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.query.requests

import maryk.SimpleMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.query.RequestContext
import maryk.core.query.descending
import maryk.core.query.filters.Exists
import maryk.test.shouldBe
import kotlin.test.Test

private val key1 = SimpleMarykModel.key("Zk6m4QpZQegUg5s13JVYlQ")

internal val scanVersionedChangesRequest = SimpleMarykModel.scanVersionedChanges(
    startKey = key1,
    fromVersion = 1234uL
)

internal val scanVersionedChangesMaxRequest = SimpleMarykModel.run {
    scanVersionedChanges(
        startKey = key1,
        filter = Exists(ref { value }),
        order = ref { value }.descending(),
        limit = 300u,
        toVersion = 2345uL,
        fromVersion = 1234uL,
        maxVersions = 10u,
        select = SimpleMarykModel.props {
            RootPropRefGraph<SimpleMarykModel>(
                value
            )
        }
    )
}

class ScanVersionedChangesRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.name to { SimpleMarykModel }
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
