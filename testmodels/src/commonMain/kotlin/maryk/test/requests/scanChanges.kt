package maryk.test.requests

import maryk.core.models.graph
import maryk.core.models.key
import maryk.core.query.filters.Exists
import maryk.core.query.orders.descending
import maryk.core.query.requests.scanChanges
import maryk.test.models.SimpleMarykModel
import maryk.test.models.TestMarykModel

private val testKey1 = TestMarykModel.key("AAACKwEAAg")

val scanChangesRequest = SimpleMarykModel.scanChanges()

val scanChangesMaxRequest = TestMarykModel.run {
    scanChanges(
        startKey = testKey1,
        where = Exists(invoke { uint::ref }),
        order = this { uint::ref }.descending(),
        limit = 300u,
        includeStart = false,
        toVersion = 2345uL,
        fromVersion = 1234uL,
        maxVersions = 10u,
        select = graph { listOf(uint) }
    )
}
