package io.maryk.app.data

import maryk.core.properties.definitions.DecimalDefinition
import maryk.core.properties.definitions.FixedBytesDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.types.numeric.SInt32
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AggregationSupportTest {
    @Test
    fun arithmeticMetricsOnlySupportArithmeticDefinitions() {
        assertTrue(AggregationMetric.SUM.supports(NumberDefinition(type = SInt32)))
        assertTrue(AggregationMetric.AVERAGE.supports(DecimalDefinition(scale = 2u)))
        assertTrue(AggregationMetric.STATS.supports(DecimalDefinition(scale = 2u)))
        assertFalse(AggregationMetric.SUM.supports(FixedBytesDefinition(byteSize = 4)))
    }

    @Test
    fun comparableMetricsStillSupportFixedBytes() {
        assertTrue(AggregationMetric.MIN.supports(FixedBytesDefinition(byteSize = 4)))
        assertTrue(AggregationMetric.MAX.supports(FixedBytesDefinition(byteSize = 4)))
    }
}
