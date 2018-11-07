@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")
package maryk.test.requests

import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.query.ascending
import maryk.core.query.filters.Exists
import maryk.core.query.requests.getChanges
import maryk.test.models.SimpleMarykModel

private val key1 = SimpleMarykModel.key("uBu6L+ARRCgpUuyks8f73g")
private val key2 = SimpleMarykModel.key("CXTD69pnTdsytwq0yxPryA")

val getChangesRequest = SimpleMarykModel.getChanges(
    key1,
    key2,
    fromVersion = 1234uL,
    toVersion = 3456uL
)

val getChangesMaxRequest = SimpleMarykModel.run {
    getChanges(
        key1,
        key2,
        filter = Exists(ref { value }),
        order = ref { value }.ascending(),
        fromVersion = 1234uL,
        toVersion = 3456uL,
        filterSoftDeleted = true,
        select = SimpleMarykModel.props {
            RootPropRefGraph<SimpleMarykModel>(
                value
            )
        }
    )
}

