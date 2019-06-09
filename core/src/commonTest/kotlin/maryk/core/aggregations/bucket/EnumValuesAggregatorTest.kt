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
                TestMarykModel.values {
                    mapNonNulls(
                        enum with V1
                    )
                }
            )
        )
        enumValuesAggregator.aggregate(
            createAggregator(
                TestMarykModel.values {
                    mapNonNulls(
                        enum with V1
                    )
                }
            )
        )

        enumValuesAggregator.aggregate(
            createAggregator(
                TestMarykModel.values {
                    mapNonNulls(
                        enum with V3
                    )
                }
            )
        )
        enumValuesAggregator.aggregate(
            createAggregator(
                TestMarykModel.values {
                    mapNonNulls(
                        enum with V3,
                        int with 37637
                    )
                }
            )
        )
        enumValuesAggregator.aggregate(
            createAggregator(
                TestMarykModel.values {
                    mapNonNulls(
                        enum with V3,
                        int with 1569
                    )
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
                            mapOf(
                                "totalInt" to SumResponse(
                                    TestMarykModel { int::ref },
                                    null
                                )
                            )
                        ),
                        2uL
                    ),
                    Bucket(
                        V3,
                        AggregationsResponse(
                            mapOf(
                                "totalInt" to SumResponse(
                                    TestMarykModel { int::ref },
                                    39206
                                )
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
