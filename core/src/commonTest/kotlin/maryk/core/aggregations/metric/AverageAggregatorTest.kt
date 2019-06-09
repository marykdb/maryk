package maryk.core.aggregations.metric

import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class AverageAggregatorTest {
    @Test
    fun aggregate() {
        val averageAggregator = AverageAggregator(
            Average(TestMarykModel { int::ref })
        )

        expect(
            AverageResponse(
                TestMarykModel { int::ref },
                null,
                0uL
            )
        ) {
            averageAggregator.toResponse()
        }

        averageAggregator.aggregate { 125 }
        averageAggregator.aggregate { 452 }
        averageAggregator.aggregate { 789 }

        expect(
            AverageResponse(
                TestMarykModel { int::ref },
                455,
                3uL
            )
        ) {
            averageAggregator.toResponse()
        }
    }
}
