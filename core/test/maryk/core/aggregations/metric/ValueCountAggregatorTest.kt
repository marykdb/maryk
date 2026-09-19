package maryk.core.aggregations.metric

import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class ValueCountAggregatorTest {
    @Test
    fun aggregate() {
        val valueCountAggregator = ValueCountAggregator(
            ValueCount(TestMarykModel.ref { int })
        )

        expect(
            ValueCountResponse(
                TestMarykModel.ref { int },
                0uL
            )
        ) {
            valueCountAggregator.toResponse()
        }

        valueCountAggregator.aggregate { 12936 }
        valueCountAggregator.aggregate { 452 }
        valueCountAggregator.aggregate { 789 }
        expect(
            ValueCountResponse(
                TestMarykModel.ref { int },
                3uL
            )
        ) {
            valueCountAggregator.toResponse()
        }
    }
}
