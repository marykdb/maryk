package maryk.core.aggregations.bucket

import kotlinx.datetime.LocalDateTime
import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.metric.Sum
import maryk.core.aggregations.metric.SumResponse
import maryk.core.properties.types.DateUnit.Hours
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class DateHistogramAggregatorTest {
    @Test
    fun aggregate() {
        val dateHistogramAggregator = DateHistogram(
            TestMarykModel { dateTime::ref },
            dateUnit = Hours,
            aggregations = Aggregations(
                "totalInt" to Sum(TestMarykModel { int::ref })
            )
        ).createAggregator()

        expect(
            DateHistogramResponse(
                TestMarykModel { dateTime::ref }
            )
        ) {
            dateHistogramAggregator.toResponse()
        }

        dateHistogramAggregator.aggregate(
            createAggregator(
                TestMarykModel.run {
                    create(
                        dateTime with LocalDateTime(2019, 12, 11, 10, 12, 8),
                        int with 345
                    )
                }
            )
        )
        dateHistogramAggregator.aggregate(
            createAggregator(
                TestMarykModel.run {
                    create(
                        dateTime with LocalDateTime(2019, 12, 11, 10, 12, 9),
                        int with 2537
                    )
                }
            )
        )
        dateHistogramAggregator.aggregate(
            createAggregator(
                TestMarykModel.run {
                    create(
                        dateTime with LocalDateTime(2019, 12, 11, 11, 32, 19),
                        int with 1
                    )
                }
            )
        )
        dateHistogramAggregator.aggregate(
            createAggregator(
                TestMarykModel.run {
                    create(
                        dateTime with LocalDateTime(2019, 12, 11, 12, 55, 56)
                    )
                }
            )
        )

        expect(
            DateHistogramResponse(
                TestMarykModel { dateTime::ref },
                listOf(
                    Bucket(
                        LocalDateTime(2019, 12, 11, 10, 0),
                        AggregationsResponse(
                            "totalInt" to SumResponse(
                                TestMarykModel { int::ref },
                                2882
                            )
                        ),
                        2uL
                    ),
                    Bucket(
                        LocalDateTime(2019, 12, 11, 11, 0),
                        AggregationsResponse(
                            "totalInt" to SumResponse(
                                TestMarykModel { int::ref },
                                1
                            )
                        ),
                        1uL
                    ),
                    Bucket(
                        LocalDateTime(2019, 12, 11, 12, 0),
                        AggregationsResponse(
                            "totalInt" to SumResponse(
                                TestMarykModel { int::ref },
                                null
                            )
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
