package maryk.core.aggregations.metric

import maryk.test.models.TestMarykModel
import maryk.core.properties.types.Decimal
import kotlin.test.Test
import kotlin.test.expect

class AverageAggregatorTest {
    @Test
    fun aggregate() {
        val averageAggregator = AverageAggregator(
            Average(TestMarykModel.ref { int })
        )

        expect(
            AverageResponse(
                TestMarykModel.ref { int },
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
                TestMarykModel.ref { int },
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
            Average(DecimalAggregationModel.ref { amount })
        )

        averageAggregator.aggregate { Decimal.parse("1.02") }
        averageAggregator.aggregate { Decimal.parse("2.01") }

        expect(Decimal.parse("1.52")) {
            averageAggregator.toResponse().value
        }
    }
}
