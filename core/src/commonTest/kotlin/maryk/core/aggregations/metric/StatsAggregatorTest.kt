package maryk.core.aggregations.metric

import maryk.test.models.TestMarykModel
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

        statsAggregator.aggregate(12936)
        statsAggregator.aggregate(452)
        statsAggregator.aggregate(789)
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
}
