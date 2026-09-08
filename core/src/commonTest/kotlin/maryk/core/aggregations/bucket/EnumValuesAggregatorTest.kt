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
            TestMarykModel.ref { enum },
            aggregations = Aggregations(
                "totalInt" to Sum(TestMarykModel.ref { int })
            )
        ).createAggregator()

        expect(
            EnumValuesResponse(
                TestMarykModel.ref { enum }
            )
        ) {
            enumValuesAggregator.toResponse()
        }

        enumValuesAggregator.aggregate(
            createAggregator(
                TestMarykModel.create { enum with V1 }
            )
        )
        enumValuesAggregator.aggregate(
            createAggregator(
                TestMarykModel.create { enum with V1 }
            )
        )

        enumValuesAggregator.aggregate(
            createAggregator(
                TestMarykModel.create { enum with V3 }
            )
        )
        enumValuesAggregator.aggregate(
            createAggregator(
                TestMarykModel.create {
                    enum with V3
                    int with 37637
                }
            )
        )
        enumValuesAggregator.aggregate(
            createAggregator(
                TestMarykModel.create {
                    enum with V3
                    int with 1569
                }
            )
        )

        expect(
            EnumValuesResponse(
                TestMarykModel.ref { enum },
                listOf(
                    Bucket(
                        V1,
                        AggregationsResponse(
                            "totalInt" to SumResponse(
                                TestMarykModel.ref { int },
                                null
                            )
                        ),
                        2uL
                    ),
                    Bucket(
                        V3,
                        AggregationsResponse(
                            "totalInt" to SumResponse(
                                TestMarykModel.ref { int },
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
