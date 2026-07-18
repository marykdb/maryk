package maryk.core.aggregations.metric

import maryk.test.models.TestMarykModel
import maryk.core.properties.types.Decimal
import kotlin.test.Test
import kotlin.test.expect

class StatsAggregatorTest {
    @Test
    fun aggregate() {
        val statsAggregator = StatsAggregator(
            Stats(TestMarykModel { int::ref })
        )

        expect(
            StatsResponse(
                TestMarykModel { int::ref },
                min = null,
                max = null,
                sum = null,
                average = null,
                valueCount = 0uL
            )
        ) {
            statsAggregator.toResponse()
        }

        statsAggregator.aggregate { 12936 }
        statsAggregator.aggregate { 452 }
        statsAggregator.aggregate { 789 }
        expect(
            StatsResponse(
                TestMarykModel { int::ref },
                min = 452,
                max = 12936,
                sum = 14177,
                average = 4725,
                valueCount = 3uL
            )
        ) {
            statsAggregator.toResponse()
        }
    }

    @Test
    fun aggregateDecimal() {
        val statsAggregator = StatsAggregator(
            Stats(DecimalAggregationModel { amount::ref })
        )

        statsAggregator.aggregate { Decimal.parse("1.02") }
        statsAggregator.aggregate { Decimal.parse("2.01") }

        expect(
            StatsResponse(
                DecimalAggregationModel { amount::ref },
                min = Decimal.parse("1.02"),
                max = Decimal.parse("2.01"),
                sum = Decimal.parse("3.03"),
                average = Decimal.parse("1.52"),
                valueCount = 2uL,
            )
        ) {
            statsAggregator.toResponse()
        }
    }
}
