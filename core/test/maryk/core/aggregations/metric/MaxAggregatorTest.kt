package maryk.core.aggregations.metric

import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class MaxAggregatorTest {
    @Test
    fun aggregate() {
        val maxAggregator = MaxAggregator(
            Max(TestMarykModel.ref { int })
        )

        expect(
            MaxResponse(
                TestMarykModel.ref { int },
                null
            )
        ) {
            maxAggregator.toResponse()
        }

        maxAggregator.aggregate { 12936 }
        maxAggregator.aggregate { 452 }
        maxAggregator.aggregate { 789 }
        expect(
            MaxResponse(
                TestMarykModel.ref { int },
                12936
            )
        ) {
            maxAggregator.toResponse()
        }
    }
}
