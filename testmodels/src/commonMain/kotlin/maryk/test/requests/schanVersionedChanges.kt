@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.test.requests

import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.query.descending
import maryk.core.query.filters.Exists
import maryk.core.query.requests.scanVersionedChanges
import maryk.test.models.SimpleMarykModel

private val key1 = SimpleMarykModel.key("Zk6m4QpZQegUg5s13JVYlQ")

val scanVersionedChangesRequest = SimpleMarykModel.scanVersionedChanges(
    startKey = key1,
    fromVersion = 1234uL
)

val scanVersionedChangesMaxRequest = SimpleMarykModel.run {
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
