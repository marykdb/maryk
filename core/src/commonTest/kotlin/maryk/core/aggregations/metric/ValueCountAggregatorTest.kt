package maryk.core.aggregations.metric

import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class ValueCountAggregatorTest {
    @Test
    fun aggregate() {
        val valueCountAggregator = ValueCountAggregator(
            ValueCount(TestMarykModel { int::ref })
        )

        expect(
            ValueCountResponse(
                TestMarykModel { int::ref },
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
                TestMarykModel { int::ref },
                3uL
            )
        ) {
            valueCountAggregator.toResponse()
        }
    }
}
