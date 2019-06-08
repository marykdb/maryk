package maryk.core.aggregations.metric

import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class SumAggregatorTest {
    @Test
    fun aggregate() {
        val sumAggregator = SumAggregator(
            Sum(TestMarykModel { int::ref })
        )

        expect(
            SumResponse(
                TestMarykModel { int::ref },
                null
            )
        ) {
            sumAggregator.toResponse()
        }

        sumAggregator.aggregate(12936)
        sumAggregator.aggregate(452)
        sumAggregator.aggregate(789)
        expect(
            SumResponse(
                TestMarykModel { int::ref },
                14177
            )
        ) {
            sumAggregator.toResponse()
        }
    }
}
