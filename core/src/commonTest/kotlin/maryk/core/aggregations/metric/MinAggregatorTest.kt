package maryk.core.aggregations.metric

import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class MinAggregatorTest {
    @Test
    fun aggregate() {
        val minAggregator = MinAggregator(
            Min(TestMarykModel { int::ref })
        )

        expect(
            MinResponse(
                TestMarykModel { int::ref },
                null
            )
        ) {
            minAggregator.toResponse()
        }

        minAggregator.aggregate { 12936 }
        minAggregator.aggregate { 452 }
        minAggregator.aggregate { 789 }

        expect(
            MinResponse(
                TestMarykModel { int::ref },
                452
            )
        ) {
            minAggregator.toResponse()
        }
    }
}
