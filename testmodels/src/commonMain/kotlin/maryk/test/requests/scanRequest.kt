package maryk.test.requests

import maryk.core.query.filters.Exists
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.requests.scan
import maryk.test.models.SimpleMarykModel

private val key1 = SimpleMarykModel.key("Zk6m4QpZQegUg5s13JVYlQ")

val scanRequest = SimpleMarykModel.run {
    scan()
}

val scanMaxRequest = SimpleMarykModel.run {
    scan(
        startKey = key1,
        filter = Exists(ref { value }),
        order = ref { value }.ascending(),
        limit = 200u,
        filterSoftDeleted = true,
        toVersion = 2345uL,
        select = graph { listOf(value) }
    )
}

val scanOrdersRequest = SimpleMarykModel.run {
    scan(
        startKey = key1,
        order = Orders(
            ref { value }.ascending(),
            ref { value }.descending()
        ),
        select = graph { listOf(value) }
    )
}
