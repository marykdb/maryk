package maryk.core.aggregations.metric

import maryk.test.models.TestMarykModel
import maryk.core.properties.types.Decimal
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

class SumAggregatorTest {
    @Test
    fun aggregate() {
        val sumAggregator = SumAggregator(
            Sum(TestMarykModel.ref { int })
        )

        expect(
            SumResponse(
                TestMarykModel.ref { int },
                null
            )
        ) {
            sumAggregator.toResponse()
        }

        sumAggregator.aggregate { 12936 }
        sumAggregator.aggregate { 452 }
        sumAggregator.aggregate { 789 }
        expect(
            SumResponse(
                TestMarykModel.ref { int },
                14177
            )
        ) {
            sumAggregator.toResponse()
        }
    }

    @Test
    fun aggregateDecimal() {
        val sumAggregator = SumAggregator(
            Sum(DecimalAggregationModel.ref { amount })
        )

        sumAggregator.aggregate { Decimal.parse("1.20") }
        sumAggregator.aggregate { Decimal.parse("2.10") }

        expect(Decimal.parse("3.30")) {
            sumAggregator.toResponse().value
        }
    }

    @Test
    fun rejectsNonArithmeticDefinition() {
        assertFailsWith<IllegalArgumentException> {
            Sum(NonArithmeticAggregationModel.ref { bytes }).createAggregator()
        }
    }
}
