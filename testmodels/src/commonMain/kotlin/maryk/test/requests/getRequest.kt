package maryk.test.requests

import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.metric.ValueCount
import maryk.core.properties.graph
import maryk.core.properties.key
import maryk.core.query.filters.Exists
import maryk.core.query.requests.get
import maryk.test.models.SimpleMarykModel

private val key1 = SimpleMarykModel.key("dR9gVdRcSPw2molM1AiOng")
private val key2 = SimpleMarykModel.key("Vc4WgX/mQHYCSEoLtfLSUQ")

val getRequest = SimpleMarykModel.get(
    key1,
    key2
)

val getMaxRequest = SimpleMarykModel.run {
    get(
        key1,
        key2,
        where = Exists(invoke { value::ref }),
        toVersion = 333uL,
        filterSoftDeleted = true,
        select = graph { listOf(value) },
        aggregations = Aggregations(
            "totalValues" to ValueCount(
                SimpleMarykModel { value::ref }
            )
        )
    )
}
