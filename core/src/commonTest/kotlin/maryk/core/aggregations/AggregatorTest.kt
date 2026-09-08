package maryk.core.aggregations

import maryk.core.aggregations.metric.Sum
import maryk.core.aggregations.metric.SumResponse
import maryk.core.aggregations.metric.ValueCount
import maryk.core.aggregations.metric.ValueCountResponse
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class AggregatorTest {
    @Test
    fun aggregate() {
        val aggregator = Aggregator(
            Aggregations(
                "sum" to Sum(TestMarykModel.ref { int }),
                "count" to ValueCount(TestMarykModel.ref { int })
            )
        )

        expect(
            AggregationsResponse(
                emptyMap()
            )
        ) {
            aggregator.toResponse()
        }

        aggregator.aggregate { 12936 }
        aggregator.aggregate { 452 }
        aggregator.aggregate { 789 }
        expect(
            AggregationsResponse(
                "sum" to SumResponse(
                    TestMarykModel.ref { int },
                    14177
                ),
                "count" to ValueCountResponse(
                    TestMarykModel.ref { int },
                    3uL
                )
            )
        ) {
            aggregator.toResponse()
        }
    }
}
