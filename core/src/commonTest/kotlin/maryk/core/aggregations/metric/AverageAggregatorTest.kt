package maryk.core.aggregations.metric

import maryk.test.models.TestMarykModel
import maryk.core.properties.types.Decimal
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

    @Test
    fun aggregateDecimalRoundsHalfEvenAtPropertyScale() {
        val averageAggregator = AverageAggregator(
            Average(DecimalAggregationModel { amount::ref })
        )

        averageAggregator.aggregate { Decimal.parse("1.02") }
        averageAggregator.aggregate { Decimal.parse("2.01") }

        expect(Decimal.parse("1.52")) {
            averageAggregator.toResponse().value
        }
    }
}
