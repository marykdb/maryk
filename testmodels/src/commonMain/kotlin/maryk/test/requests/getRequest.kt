package maryk.test.requests

import maryk.core.query.descending
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
        filter = Exists(ref { value }),
        order = ref { value }.descending(),
        toVersion = 333uL,
        filterSoftDeleted = true,
        select = graph { listOf(value) }
    )
}
