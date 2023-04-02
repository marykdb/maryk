package maryk.test.requests

import maryk.core.models.graph
import maryk.core.models.key
import maryk.core.query.filters.Exists
import maryk.core.query.requests.getChanges
import maryk.test.models.SimpleMarykModel

private val key1 = SimpleMarykModel.key("WWurg6ysTsozoMei/SurOw")
private val key2 = SimpleMarykModel.key("awfbjYrVQ+cdXblfQKV10A")

val getUpdatesRequest = SimpleMarykModel.getChanges(
    key1,
    key2
)

val getUpdatesMaxRequest = SimpleMarykModel.run {
    getChanges(
        key1,
        key2,
        where = Exists(invoke { value::ref }),
        fromVersion = 1234uL,
        toVersion = 12345uL,
        maxVersions = 5u,
        filterSoftDeleted = true,
        select = graph { listOf(value) }
    )
}
