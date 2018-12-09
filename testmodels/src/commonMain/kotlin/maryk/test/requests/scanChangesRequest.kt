@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.test.requests

import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.query.ascending
import maryk.core.query.filters.Exists
import maryk.core.query.requests.scanChanges
import maryk.test.models.SimpleMarykModel

private val key1 = SimpleMarykModel.key("Zk6m4QpZQegUg5s13JVYlQ")

val scanChangesRequest = SimpleMarykModel.scanChanges(
    fromVersion = 1234uL
)

val scanChangeMaxRequest = SimpleMarykModel.scanChanges(
    startKey = key1,
    filter = Exists(SimpleMarykModel.ref { value }),
    order = SimpleMarykModel.ref { value }.ascending(),
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
