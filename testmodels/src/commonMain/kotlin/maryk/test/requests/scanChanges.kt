package maryk.test.requests

import maryk.core.query.descending
import maryk.core.query.filters.Exists
import maryk.core.query.requests.scanChanges
import maryk.test.models.SimpleMarykModel

private val key1 = SimpleMarykModel.key("Zk6m4QpZQegUg5s13JVYlQ")

val scanChangesRequest = SimpleMarykModel.scanChanges()

val scanChangesMaxRequest = SimpleMarykModel.run {
    scanChanges(
        startKey = key1,
        filter = Exists(ref { value }),
        order = ref { value }.descending(),
        limit = 300u,
        toVersion = 2345uL,
        fromVersion = 1234uL,
        maxVersions = 10u,
        select = graph { listOf(value) }
    )
}
