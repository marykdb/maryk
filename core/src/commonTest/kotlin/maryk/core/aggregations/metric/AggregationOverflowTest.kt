package maryk.core.aggregations.metric

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.decimal
import maryk.core.properties.definitions.number
import maryk.core.properties.types.Decimal
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.properties.types.numeric.SInt8
import maryk.core.properties.types.numeric.SInt64
import maryk.core.properties.types.numeric.UInt64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private object OverflowModel : RootDataModel<OverflowModel>() {
    val byte by number(1u, type = SInt8)
    val long by number(2u, type = SInt64)
    val ulong by number(3u, type = UInt64)
    val decimal by decimal(4u, scale = 2u, byteSize = 1)
    val float by number(5u, type = Float32)
    val double by number(6u, type = Float64)
}

class AggregationOverflowTest {
    @Test
    fun floatAveragesKeepFractionsWhenTheSumIsWhole() {
        val average = AverageAggregator(Average(OverflowModel.ref { float }))
        val stats = StatsAggregator(Stats(OverflowModel.ref { float }))
        listOf(1f, 2f).forEach { value -> average.aggregate { value }; stats.aggregate { value } }
        assertEquals(1.5f, average.toResponse().value)
        assertEquals(1.5f, stats.toResponse().average)
    }

    @Test
    fun doubleAveragesKeepFractionsWhenTheSumIsWhole() {
        val average = AverageAggregator(Average(OverflowModel.ref { double }))
        val stats = StatsAggregator(Stats(OverflowModel.ref { double }))
        listOf(1.0, 2.0).forEach { value -> average.aggregate { value }; stats.aggregate { value } }
        assertEquals(1.5, average.toResponse().value)
        assertEquals(1.5, stats.toResponse().average)
    }

    @Test
    fun averagesUseExactSumsForNarrowAndFullWidthIntegers() {
        val bytes = AverageAggregator(Average(OverflowModel.ref { byte }))
        repeat(128) { bytes.aggregate { 1.toByte() } }
        assertEquals(1.toByte(), bytes.toResponse().value)
        val longs = AverageAggregator(Average(OverflowModel.ref { long }))
        repeat(2) { longs.aggregate { Long.MAX_VALUE } }
        assertEquals(Long.MAX_VALUE, longs.toResponse().value)
        val unsigned = AverageAggregator(Average(OverflowModel.ref { ulong }))
        repeat(2) { unsigned.aggregate { ULong.MAX_VALUE } }
        assertEquals(ULong.MAX_VALUE, unsigned.toResponse().value)
    }

    @Test
    fun typedSumAndStatsRejectFinalOverflow() {
        val sum = SumAggregator(Sum(OverflowModel.ref { byte }))
        val stats = StatsAggregator(Stats(OverflowModel.ref { long }))
        repeat(2) { sum.aggregate { 127.toByte() }; stats.aggregate { Long.MAX_VALUE } }
        assertFailsWith<ArithmeticException> { sum.toResponse() }
        assertFailsWith<ArithmeticException> { stats.toResponse() }
    }

    @Test
    fun intermediateOverflowAndNegativeDivisionDoNotLoseInformation() {
        val sum = SumAggregator(Sum(OverflowModel.ref { long }))
        listOf(Long.MAX_VALUE, Long.MAX_VALUE, -Long.MAX_VALUE).forEach { value -> sum.aggregate { value } }
        assertEquals(Long.MAX_VALUE, sum.toResponse().value)
        val average = AverageAggregator(Average(OverflowModel.ref { long }))
        listOf(-1L, 0L).forEach { value -> average.aggregate { value } }
        assertEquals(0L, average.toResponse().value)
    }

    @Test
    fun decimalAverageCanFitEvenWhenItsSumDoesNot() {
        val average = AverageAggregator(Average(OverflowModel.ref { decimal }))
        val sum = SumAggregator(Sum(OverflowModel.ref { decimal }))
        repeat(2) { average.aggregate { Decimal.parse("1.00") }; sum.aggregate { Decimal.parse("1.00") } }
        assertEquals(Decimal.parse("1.00"), average.toResponse().value)
        assertFailsWith<ArithmeticException> { sum.toResponse() }
    }
}
