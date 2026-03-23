package maryk.test.requests

import maryk.core.models.graph
import maryk.core.query.filters.Exists
import maryk.core.query.requests.scanUpdateHistory
import maryk.test.models.SimpleMarykModel

val scanUpdateHistoryRequest = SimpleMarykModel.scanUpdateHistory()

val scanUpdateHistoryMaxRequest = SimpleMarykModel.run {
    scanUpdateHistory(
        where = Exists(invoke { value::ref }),
        limit = 300u,
        toVersion = 2345uL,
        fromVersion = 1234uL,
        select = graph { listOf(value) }
    )
}
