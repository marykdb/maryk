@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.query.requests

import maryk.SimpleMarykModel
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.query.Order
import maryk.core.query.RequestContext
import maryk.core.query.filters.Exists
import maryk.test.shouldBe
import kotlin.test.Test

private val key1 = SimpleMarykModel.key("Zk6m4QpZQegUg5s13JVYlQ")

internal val scanChangesRequest = SimpleMarykModel.scanChanges(
    startKey = key1,
    fromVersion = 1234uL
)

@Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")
internal val scanChangeMaxRequest = SimpleMarykModel.scanChanges(
    startKey = key1,
    filter = Exists(SimpleMarykModel.ref { value }),
    order = Order(SimpleMarykModel.ref { value }),
    limit = 100u,
    filterSoftDeleted = true,
    toVersion = 2345uL,
    fromVersion = 1234uL,
    select = SimpleMarykModel.props {
        RootPropRefGraph<SimpleMarykModel>(
            value
        )
    }
)

class ScanChangesRequestTest {
    private val context = RequestContext(mapOf(
        SimpleMarykModel.name to { SimpleMarykModel }
    ))

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(scanChangesRequest, ScanChangesRequest, { this.context })
        checkProtoBufConversion(scanChangeMaxRequest, ScanChangesRequest, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(scanChangesRequest, ScanChangesRequest, { this.context })
        checkJsonConversion(scanChangeMaxRequest, ScanChangesRequest, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        checkYamlConversion(scanChangesRequest, ScanChangesRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        filterSoftDeleted: true
        limit: 100
        fromVersion: 1234

        """.trimIndent()

        checkYamlConversion(scanChangeMaxRequest, ScanChangesRequest, { this.context }) shouldBe """
        dataModel: SimpleMarykModel
        startKey: Zk6m4QpZQegUg5s13JVYlQ
        select:
        - value
        filter: !Exists value
        order: value
        toVersion: 2345
        filterSoftDeleted: true
        limit: 100
        fromVersion: 1234

        """.trimIndent()
    }
}
