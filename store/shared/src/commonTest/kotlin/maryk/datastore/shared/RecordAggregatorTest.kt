package maryk.datastore.shared

import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.Aggregator
import maryk.core.aggregations.metric.Sum
import maryk.core.aggregations.metric.ValueCount
import maryk.core.models.RootDataModel
import maryk.core.models.graph
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.numeric.SInt32
import kotlin.test.Test
import kotlin.test.assertEquals

private object MetricRecord : RootDataModel<MetricRecord>() {
    val title by string(1u)
    val amount by number(2u, type = SInt32, required = false)
    val other by number(3u, type = SInt32, required = false)
}

class RecordAggregatorTest {
    @Test
    fun repeatedMissingAndPresentValuesAreFetchedOncePerRecordAndStillContributeEveryTime() {
        val requests = Aggregations("sum" to Sum(MetricRecord.amount.ref()), "count" to ValueCount(MetricRecord.amount.ref()))
        val cached = MetricRecord.createRecordAggregator(requests, MetricRecord.graph { listOf(title) })
        val ordinary = Aggregator(requests)
        var calls = 0
        for (value in listOf(null, 3, 3, 7)) {
            cached.aggregate { calls++; value }
            ordinary.aggregate { value }
        }
        assertEquals(4, calls)
        assertEquals(ordinary.toResponse(), cached.toResponse())
    }

    @Test
    fun fullSelectedAndDistinctMetricReadsKeepTheirOrdinaryFetcher() {
        val repeated = Aggregations("sum" to Sum(MetricRecord.amount.ref()), "count" to ValueCount(MetricRecord.amount.ref()))
        for (select in listOf(null, MetricRecord.graph { listOf(amount) })) {
            var calls = 0
            MetricRecord.createRecordAggregator(repeated, select).aggregate { calls++; 3 }
            assertEquals(2, calls)
        }
        var calls = 0
        val distinct = Aggregations("sum" to Sum(MetricRecord.amount.ref()), "other" to Sum(MetricRecord.other.ref()))
        MetricRecord.createRecordAggregator(distinct, MetricRecord.graph { listOf(title) }).aggregate { calls++; 3 }
        assertEquals(2, calls)
    }
}
