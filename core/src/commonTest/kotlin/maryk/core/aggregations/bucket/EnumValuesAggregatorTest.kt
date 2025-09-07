package maryk.core.aggregations.bucket

import maryk.core.aggregations.Aggregations
import maryk.core.aggregations.AggregationsResponse
import maryk.core.aggregations.metric.Sum
import maryk.core.aggregations.metric.SumResponse
import maryk.test.models.Option.V1
import maryk.test.models.Option.V3
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class EnumValuesAggregatorTest {
    @Test
    fun aggregate() {
        val enumValuesAggregator = EnumValues(
            TestMarykModel { enum::ref },
            aggregations = Aggregations(
                "totalInt" to Sum(TestMarykModel { int::ref })
            )
        ).createAggregator()

        expect(
            EnumValuesResponse(
                TestMarykModel { enum::ref }
            )
        ) {
            enumValuesAggregator.toResponse()
        }

        enumValuesAggregator.aggregate(
            createAggregator(
                TestMarykModel.create { enum += V1 }
            )
        )
        enumValuesAggregator.aggregate(
            createAggregator(
                TestMarykModel.create { enum += V1 }
            )
        )

        enumValuesAggregator.aggregate(
            createAggregator(
                TestMarykModel.create { enum += V3 }
            )
        )
        enumValuesAggregator.aggregate(
            createAggregator(
                TestMarykModel.create {
                    enum += V3
                    int += 37637
                }
            )
        )
        enumValuesAggregator.aggregate(
            createAggregator(
                TestMarykModel.create {
                    enum += V3
                    int += 1569
                }
            )
        )

        expect(
            EnumValuesResponse(
                TestMarykModel { enum::ref },
                listOf(
                    Bucket(
                        V1,
                        AggregationsResponse(
                            "totalInt" to SumResponse(
                                TestMarykModel { int::ref },
                                null
                            )
                        ),
                        2uL
                    ),
                    Bucket(
                        V3,
                        AggregationsResponse(
                            "totalInt" to SumResponse(
                                TestMarykModel { int::ref },
                                39206
                            )
                        ),
                        3uL
                    )
                )
            )
        ) {
            enumValuesAggregator.toResponse()
        }
    }
}
