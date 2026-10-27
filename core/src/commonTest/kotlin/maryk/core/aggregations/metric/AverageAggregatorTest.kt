package maryk.core.aggregations.metric

import maryk.test.models.TestMarykModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.SInt8
import maryk.core.properties.types.Decimal
import kotlin.test.Test
import kotlin.test.expect

class AverageAggregatorTest {
    @Test
    fun narrowIntegerAverageAndStatsRetainTheFullCount() {
        val reference = NarrowAverageModel.ref { value }
        val average = AverageAggregator(Average(reference))
        val stats = StatsAggregator(Stats(reference))
        repeat(256) {
            val value = if (it == 0) 127.toByte() else 0.toByte()
            average.aggregate { value }
            stats.aggregate { value }
        }
        expect(0.toByte()) { average.toResponse().value }
        expect(256uL) { average.toResponse().valueCount }
        expect(0.toByte()) { stats.toResponse().average }
        expect(256uL) { stats.toResponse().valueCount }
    }

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

private object NarrowAverageModel : RootDataModel<NarrowAverageModel>() {
    val value by number(index = 1u, type = SInt8)
}
