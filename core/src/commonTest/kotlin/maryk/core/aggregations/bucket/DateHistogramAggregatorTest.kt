package maryk.core.aggregations.bucket

import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.bucket.DateUnit.Hours
import maryk.lib.time.DateTime
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class DateHistogramAggregatorTest {
    @Test
    fun aggregate() {
        val dateHistogramAggregator = DateHistogramAggregator(
            DateHistogram(
                TestMarykModel { dateTime::ref },
                dateUnit = Hours
            )
        )

        expect(
            DateHistogramResponse(
                TestMarykModel { dateTime::ref }
            )
        ) {
            dateHistogramAggregator.toResponse()
        }

        dateHistogramAggregator.aggregate(DateTime(2019, 12, 11, 10, 12,8))
        dateHistogramAggregator.aggregate(DateTime(2019, 12, 11, 10, 12,9))
        dateHistogramAggregator.aggregate(DateTime(2019, 12, 11, 11, 32,19))
        dateHistogramAggregator.aggregate(DateTime(2019, 12, 11, 12, 55,56))

        expect(
            DateHistogramResponse(
                TestMarykModel { dateTime::ref },
                listOf(
                    Bucket(
                        DateTime(2019, 12, 11, 10),
                        AggregationsResponse(
                            mapOf()
                        ),
                        2uL
                    ),
                    Bucket(
                        DateTime(2019, 12, 11, 11),
                        AggregationsResponse(
                            mapOf()
                        ),
                        1uL
                    ),
                    Bucket(
                        DateTime(2019, 12, 11, 12),
                        AggregationsResponse(
                            mapOf()
                        ),
                        1uL
                    )
                )
            )
        ) {
            dateHistogramAggregator.toResponse()
        }
    }
}
